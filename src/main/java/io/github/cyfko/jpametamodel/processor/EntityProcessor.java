package io.github.cyfko.jpametamodel.processor;

import io.github.cyfko.jpametamodel.api.CollectionKind;
import io.github.cyfko.jpametamodel.api.CollectionMetadata;
import io.github.cyfko.jpametamodel.api.CollectionType;
import io.github.cyfko.jpametamodel.api.PersistenceMetadata;
import io.github.cyfko.jpametamodel.providers.PersistenceRegistryProvider;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

import static io.github.cyfko.jpametamodel.processor.AnnotationProcessorUtils.BASIC_JPA_TYPES;

/**
 * Processor responsible for scanning JPA entities and embeddables during
 * annotation processing
 * and extracting structural metadata about their persistent fields.
 * <p>
 * This processor is not a standalone {@code AbstractProcessor}; it is intended
 * to be orchestrated
 * by a higher-level {@code MetamodelProcessor}. It inspects {@code @Entity}
 * classes, walks their
 * inheritance hierarchy, discovers referenced {@code @Embeddable} types, and
 * builds an in-memory
 * registry suitable for code generation or runtime lookup.
 * </p>
 *
 * @author Frank KOSSI
 * @since 1.0.0
 */
public class EntityProcessor {

    private final ProcessingEnvironment processingEnv;
    private final Types typeUtils;
    private final Elements elementUtils;
    private final Messager messager;

    private final Map<String, Map<String, SimplePersistenceMetadata>> collectedRegistry = new LinkedHashMap<>();
    private final Map<String, Map<String, SimplePersistenceMetadata>> collectedEmbeddable = new LinkedHashMap<>();

    // Only process entities referenced by @Projection
    private final List<String> referencedEntities = new ArrayList<>();

    /**
     * Constructs a new EntityProcessor.
     *
     * @param processingEnv the processing environment
     */
    public EntityProcessor(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.typeUtils = processingEnv.getTypeUtils();
        this.elementUtils = processingEnv.getElementUtils();
        this.messager = processingEnv.getMessager();
    }

    /**
     * Sets the set of entity class names (FQCN) that should be processed.
     * If null, all entities are processed (legacy behavior).
     */
    public void setReferencedEntities(Set<String> referencedEntities) {
        this.referencedEntities.addAll(referencedEntities);
    }

    /**
     * Returns an unmodifiable view of the collected entity metadata registry.
     * <p>
     * The returned map is keyed by the fully qualified entity class name and
     * contains, for each
     * entity, a map of field names to {@link SimplePersistenceMetadata}.
     * </p>
     *
     * @return an unmodifiable entity metadata registry
     */
    public Map<String, Map<String, SimplePersistenceMetadata>> getRegistry() {
        return Collections.unmodifiableMap(collectedRegistry);
    }

    /**
     * Returns an unmodifiable view of the collected embeddable metadata registry.
     * <p>
     * The returned map is keyed by the fully qualified embeddable class name and
     * contains, for each
     * embeddable, a map of field names to {@link SimplePersistenceMetadata}.
     * </p>
     *
     * @return an unmodifiable embeddable metadata registry
     */
    public Map<String, Map<String, SimplePersistenceMetadata>> getEmbeddableRegistry() {
        return Collections.unmodifiableMap(collectedEmbeddable);
    }

    /**
     * Indicates whether metadata has been collected for the given entity class
     * name.
     *
     * @param entityClassName the fully qualified entity class name
     * @return {@code true} if metadata is available, {@code false} otherwise
     */
    public boolean hasEntityMetadata(String entityClassName) {
        return collectedRegistry.containsKey(entityClassName);
    }

    /**
     * Indicates whether metadata has been collected for the given embeddable class
     * name.
     *
     * @param embeddableClassName the fully qualified embeddable class name
     * @return {@code true} if metadata is available, {@code false} otherwise
     */
    public boolean hasEmbeddableMetadata(String embeddableClassName) {
        return collectedEmbeddable.containsKey(embeddableClassName);
    }

    /**
     * Processes all {@code @Entity}-annotated classes discovered in the current
     * round and
     * registers their persistent fields in the internal registry.
     * <p>
     * For each entity, this method:
     * </p>
     * <ul>
     * <li>Skips non-public classes and those declared inside a non-public enclosing
     * class.</li>
     * <li>Traverses the class hierarchy to collect persistent fields, excluding
     * {@code @Transient} ones.</li>
     * <li>Analyzes each field to determine its persistence kind (scalar, id,
     * embedded, collection).</li>
     * <li>Discovers and processes referenced embeddables recursively.</li>
     * </ul>
     *
     */
    public void processEntities() {

        // Process @Entity classes
        for (var i = 0; i < referencedEntities.size(); i++) {
            String fqcnEntity = referencedEntities.get(i);
            TypeElement entityType = processingEnv.getElementUtils().getTypeElement(fqcnEntity);

            if (entityType == null)
                continue;
            if (collectedRegistry.containsKey(fqcnEntity))
                continue;
            if (shouldSkipEntity(entityType))
                continue;

            messager.printMessage(Diagnostic.Kind.NOTE, "🔍 Analysing entity: " + entityType.getQualifiedName());

            Map<String, SimplePersistenceMetadata> fields = extractFields(entityType, entityType);
            collectedRegistry.put(fqcnEntity, fields);

            messager.printMessage(Diagnostic.Kind.NOTE,
                    "✅ Extracted " + fields.size() + " fields from " + entityType.getSimpleName());

            // Process referenced embeddables
            processReferencedEmbeddables(fields);
        }
    }

    /**
     * Extracts persistent fields for the given type and its superclasses, creating
     * {@link SimplePersistenceMetadata} entries for each field.
     * <p>
     * This method:
     * </p>
     * <ul>
     * <li>Skips {@code @Transient} fields.</li>
     * <li>Handles {@code @Id}, {@code @EmbeddedId}, {@code @Embedded}, collections
     * and scalar types.</li>
     * <li>Resolves related types for associations and element collections.</li>
     * <li>Checks for missing or inconsistent identifier declarations on entity
     * roots.</li>
     * </ul>
     *
     * @param type       the type whose fields should be inspected (including
     *                   hierarchy)
     * @param rootEntity the root entity type used for {@code @Id} consistency
     *                   checks
     * @return an ordered mapping from field name to
     *         {@link SimplePersistenceMetadata}
     */
    private Map<String, SimplePersistenceMetadata> extractFields(TypeElement type, TypeElement rootEntity) {
        Map<String, SimplePersistenceMetadata> result = new LinkedHashMap<>();
        Types types = processingEnv.getTypeUtils();

        while (type != null && !type.getQualifiedName().toString().equals("java.lang.Object")) {
            for (Element enclosed : type.getEnclosedElements()) {
                if (enclosed.getKind() != ElementKind.FIELD)
                    continue;
                if (enclosed.getModifiers().contains(Modifier.STATIC))
                    continue;

                VariableElement field = (VariableElement) enclosed;
                String name = field.getSimpleName().toString();

                if (result.containsKey(name))
                    continue;
                if (AnnotationProcessorUtils.hasAnnotation(field, "jakarta.persistence.Transient"))
                    continue;

                SimplePersistenceMetadata metadata = analyzeField(field);
                result.put(name, metadata);

                if (metadata.relatedType != null && AnnotationProcessorUtils.hasAnnotation(metadata.relatedType, "jakarta.persistence.Entity")) {
                    referencedEntities.add(metadata.relatedType.toString());
                }
            }

            TypeMirror superType = type.getSuperclass();
            if (superType.getKind() == TypeKind.NONE)
                break;
            type = (TypeElement) types.asElement(superType);
        }

        // Only warn about missing @Id for actual entities, not embeddables
        if (AnnotationProcessorUtils.hasAnnotation(rootEntity, "jakarta.persistence.Entity")) {
            long idCount = result.values().stream().filter(SimplePersistenceMetadata::isId).count();
            if (idCount == 0) {
                messager.printMessage(Diagnostic.Kind.WARNING,
                        "⚠️ No @Id field found in " + rootEntity.getQualifiedName(), rootEntity);
            } else if (idCount > 1
                    && !AnnotationProcessorUtils.hasAnnotation(rootEntity, "jakarta.persistence.IdClass")) {
                messager.printMessage(Diagnostic.Kind.WARNING,
                        "⚠️ " + rootEntity.getQualifiedName()
                                + " is not annotated with @jakarta.persistence.IdClass but multiple @Id fields detected",
                        rootEntity);
            }
        }

        return result;
    }

    /**
     * Processes embeddable types referenced by the given set of entity fields.
     * <p>
     * This method is recursive and handles nested embeddables by:
     * </p>
     * <ul>
     * <li>Resolving non-basic {@code relatedType}s.</li>
     * <li>Detecting {@code @Embeddable} classes.</li>
     * <li>Extracting their fields and registering them into the embeddable
     * registry.</li>
     * <li>Following further embeddable references transitively.</li>
     * </ul>
     *
     * @param fields   the already extracted fields for an entity or embeddable
     */
    private void processReferencedEmbeddables(Map<String, SimplePersistenceMetadata> fields) {
        for (Map.Entry<String, SimplePersistenceMetadata> entry : fields.entrySet()) {
            SimplePersistenceMetadata metadata = entry.getValue();

            // Check if field has a related type that might be an embeddable
            if (!BASIC_JPA_TYPES.contains(metadata.relatedTypeFqcn)) {
                Element relatedType = metadata.relatedType();

                // Skip if already processed
                if (collectedEmbeddable.containsKey(metadata.relatedTypeFqcn)) {
                    continue;
                }

                // Try to load the type and check if it's an embeddable
                if (AnnotationProcessorUtils.hasAnnotation(relatedType, "jakarta.persistence.Embeddable")) {
                    messager.printNote("📎 Analysing referenced embeddable: " + relatedType);

                    // Extract fields from embeddable
                    Map<String, SimplePersistenceMetadata> embeddableFields = extractFields((TypeElement) relatedType, (TypeElement) relatedType);
                    collectedEmbeddable.put(metadata.relatedTypeFqcn, embeddableFields);

                    messager.printNote("✅ Extracted " + embeddableFields.size() + " fields from embeddable " + relatedType.getSimpleName());

                    // Recursive: process embeddables referenced by this embeddable
                    processReferencedEmbeddables(embeddableFields);
                }
            }
        }
    }

    /**
     * Generates the {@code PersistenceRegistryProviderImpl} implementation
     * class.
     * <p>
     * The generated class initializes two static registries:
     * </p>
     * <ul>
     * <li>One for entities and their persistent fields.</li>
     * <li>One for embeddables and their fields.</li>
     * </ul>
     * <p>
     * Both registries are exposed through the
     * {@code PersistenceRegistryProvider} interface
     * and wrapped in unmodifiable maps to prevent runtime mutation.
     * </p>
     */
    public void generateProviderImpl() {
        // Count entities vs embeddables for better logging
        long entityCount = collectedRegistry.keySet().stream()
                .filter(fqcn -> {
                    TypeElement te = processingEnv.getElementUtils().getTypeElement(fqcn);
                    return te != null && AnnotationProcessorUtils.hasAnnotation(te, "jakarta.persistence.Entity");
                })
                .count();
        long embeddableCount = collectedRegistry.size() - entityCount;

        messager.printMessage(Diagnostic.Kind.NOTE, "🛠️ Generating PersistenceRegistryProvider implementation...");
        try {
            JavaFileObject file = processingEnv.getFiler()
                    .createSourceFile(
                            "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl");

            try (Writer writer = file.openWriter()) {
                writeEntityRegistry(writer);
            }

            messager.printMessage(Diagnostic.Kind.NOTE,
                    String.format(
                            "✅ PersistenceRegistryProviderImpl generated successfully with %d entities and %d embeddables",
                            entityCount, embeddableCount));

        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate PersistenceRegistryProviderImpl: " + e.getMessage());
        }

        Helper.generateServiceProviderInfo(
                processingEnv,
                PersistenceRegistryProvider.class,
                "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl");
    }

    /**
     * Writes the body of the generated
     * {@code PersistenceRegistryProviderImpl} class
     * into the provided writer.
     * <p>
     * This method emits Java source code that:
     * </p>
     * <ul>
     * <li>Declares static maps for entity and embeddable metadata.</li>
     * <li>Populates them with {@code PersistenceMetadata} instances based on the
     * collected registry.</li>
     * <li>Exposes them through the {@code getEntityMetadataRegistry} and
     * {@code getEmbeddableMetadataRegistry} methods.</li>
     * </ul>
     *
     * @param writer the writer targeting the generated Java source file
     * @throws IOException if an I/O error occurs while writing
     */
    private void writeEntityRegistry(Writer writer) throws IOException {
        writer.write("package io.github.cyfko.jpametamodel.providers.impl;\n\n");
        writer.write("import io.github.cyfko.jpametamodel.providers.PersistenceRegistryProvider;\n");
        writer.write("import io.github.cyfko.jpametamodel.api.PersistenceMetadata;\n");
        writer.write("import io.github.cyfko.jpametamodel.api.CollectionKind;\n");
        writer.write("import io.github.cyfko.jpametamodel.api.CollectionType;\n");
        writer.write("import io.github.cyfko.jpametamodel.api.CollectionMetadata;\n");
        writer.write("import java.util.Collections;\n");
        writer.write("import java.util.Map;\n");
        writer.write("import java.util.HashMap;\n");
        writer.write("import java.util.Optional;\n\n");

        writer.write("/**\n");
        writer.write(" * Generated entity metadata provider implementation.\n");
        writer.write(" * DO NOT EDIT - This file is automatically generated.\n");
        writer.write(" */\n");
        writer.write(
                "public class PersistenceRegistryProviderImpl implements PersistenceRegistryProvider {\n");
        writer.write(
                "  public static final Map<Class<?>, Map<String, PersistenceMetadata>> ENTITY_METADATA_REGISTRY;\n");
        writer.write(
                "  public static final Map<Class<?>, Map<String, PersistenceMetadata>> EMBEDDABLE_METADATA_REGISTRY;\n\n");

        // FOR ENTITIES
        writer.write("    static {\n");
        writer.write("       Map<Class<?>, Map<String, PersistenceMetadata>> registry = new HashMap<>();\n");
        writer.write(
                "       Map<Class<?>, Map<String, PersistenceMetadata>> embeddableRegistry = new HashMap<>();\n\n");

        writer.write("       // Fill entity metadata registry\n");
        writer.write("       {\n");
        for (Map.Entry<String, Map<String, SimplePersistenceMetadata>> entry : collectedRegistry.entrySet()) {
            String fqcn = entry.getKey();
            writer.write("          // " + fqcn + "\n");
            writer.write("          {\n");
            writer.write("              Map<String, PersistenceMetadata> fields = new HashMap<>();\n");

            for (Map.Entry<String, SimplePersistenceMetadata> fieldEntry : entry.getValue().entrySet()) {
                String fieldName = fieldEntry.getKey();
                SimplePersistenceMetadata meta = fieldEntry.getValue();

                writer.write("              fields.put(\"" + fieldName + "\", ");
                writer.write(generateMetadataInstantiation(meta));
                writer.write(");\n");
            }

            writer.write("              registry.put(" + fqcn + ".class, fields);\n");
            writer.write("          }\n\n");
        }
        writer.write("       }\n\n");

        writer.write("       // Fill embeddables metadata registry\n");
        writer.write("       {\n");
        for (Map.Entry<String, Map<String, SimplePersistenceMetadata>> entry : collectedEmbeddable.entrySet()) {
            String fqcn = entry.getKey();
            writer.write("          // " + fqcn + "\n");
            writer.write("          {\n");
            writer.write("              Map<String, PersistenceMetadata> fields = new HashMap<>();\n");

            for (Map.Entry<String, SimplePersistenceMetadata> fieldEntry : entry.getValue().entrySet()) {
                String fieldName = fieldEntry.getKey();
                SimplePersistenceMetadata meta = fieldEntry.getValue();

                writer.write("              fields.put(\"" + fieldName + "\", ");
                writer.write(generateMetadataInstantiation(meta));
                writer.write(");\n");
            }

            writer.write("              embeddableRegistry.put(" + fqcn + ".class, fields);\n");
            writer.write("          }\n\n");
        }
        writer.write("       }\n\n");

        writer.write("      ENTITY_METADATA_REGISTRY = Collections.unmodifiableMap(registry);\n");
        writer.write("      EMBEDDABLE_METADATA_REGISTRY = Collections.unmodifiableMap(embeddableRegistry);\n");
        writer.write("    }\n\n");

        // FOR ENTITIES
        writer.write("    @Override\n");
        writer.write(
                "    public Map<Class<?>, Map<String, PersistenceMetadata>> getEntityMetadataRegistry() { return ENTITY_METADATA_REGISTRY; }\n\n");

        // FOR EMBEDDABLE
        writer.write("    @Override\n");
        writer.write(
                "    public Map<Class<?>, Map<String, PersistenceMetadata>> getEmbeddableMetadataRegistry() { return EMBEDDABLE_METADATA_REGISTRY; }\n\n");
        writer.write("}\n");
    }

    /**
     * Determines whether a given {@code @Entity} type should be skipped from
     * processing.
     * <p>
     * Entities are ignored when:
     * </p>
     * <ul>
     * <li>The class itself is not {@code public}.</li>
     * <li>It is declared inside a non-public enclosing class.</li>
     * </ul>
     *
     * @param entityType the entity type to inspect
     * @return {@code true} if the entity should be skipped, {@code false} otherwise
     */
    private boolean shouldSkipEntity(TypeElement entityType) {
        String className = entityType.getQualifiedName().toString();

        if (!entityType.getModifiers().contains(Modifier.PUBLIC)) {
            messager.printMessage(Diagnostic.Kind.NOTE, "⏭️ Skipping non-public class: " + className);
            return true;
        }

        Element enclosingElement = entityType.getEnclosingElement();
        if (enclosingElement != null && enclosingElement.getKind() == ElementKind.CLASS) {
            TypeElement enclosingClass = (TypeElement) enclosingElement;
            if (!enclosingClass.getModifiers().contains(Modifier.PUBLIC)) {
                messager.printMessage(Diagnostic.Kind.NOTE,
                        "⏭️ Skipping class with non-public enclosing class: " + className);
                return true;
            }
        }

        return false;
    }

    /**
     * Analyzes a single field and produces its {@link SimplePersistenceMetadata}
     * description.
     * <p>
     * The analysis detects:
     * </p>
     * <ul>
     * <li>Simple identifiers ({@code @Id}).</li>
     * <li>Embedded identifiers ({@code @EmbeddedId}).</li>
     * <li>Embedded value objects ({@code @Embedded}).</li>
     * <li>Collections with their {@link CollectionMetadata} (kind, type,
     * mapping).</li>
     * <li>Plain scalar attributes.</li>
     * </ul>
     * <p>
     * If an embedded or embedded-id type is not annotated as {@code @Embeddable},
     * an error is reported.
     * </p>
     *
     * @param field    the field to analyse
     * @return the corresponding {@link SimplePersistenceMetadata} instance
     */
    private SimplePersistenceMetadata analyzeField(VariableElement field) {
        boolean isId = AnnotationProcessorUtils.hasAnnotation(field, "jakarta.persistence.Id");
        boolean isEmbeddedId = AnnotationProcessorUtils.hasAnnotation(field, "jakarta.persistence.EmbeddedId");
        boolean isCollection = isCollection(field.asType());
        boolean isEmbedded = AnnotationProcessorUtils.hasAnnotation(field, "jakarta.persistence.Embedded");

        String mappedIdField = extractMappedId(field);

        SimplePersistenceMetadata metadata;
        String fieldTypeFqcn = AnnotationProcessorUtils.getTypeNameWithoutAnnotations(field.asType());
        TypeElement fieldType = elementUtils.getTypeElement(fieldTypeFqcn);


        if (isId) {
            metadata = SimplePersistenceMetadata.id(fieldType,fieldTypeFqcn);
        } else if (isEmbeddedId) {
            if (!isEmbeddableType(fieldType)) {
                messager.printError(fieldTypeFqcn + " is not embeddable. Missing @jakarta.persistence.Embeddable annotation on it.", field);
            }
            metadata = SimplePersistenceMetadata.id(fieldType,fieldTypeFqcn);
        } else if (isEmbedded) {
            if (!isEmbeddableType(fieldType)) {
                messager.printError(fieldTypeFqcn + " is not embeddable. Missing @jakarta.persistence.Embeddable annotation on it.", field);
            }
            metadata = SimplePersistenceMetadata.scalar(fieldType,fieldTypeFqcn);
        } else if (isCollection) {
            metadata = SimplePersistenceMetadata.collection(field, typeUtils);
        } else {
            metadata = SimplePersistenceMetadata.scalar(fieldType,fieldTypeFqcn);
        }

        if (mappedIdField != null) {
            metadata = metadata.withMappedId(mappedIdField);
        }

        return metadata;
    }

    /**
     * Indicates whether the given type refers to an
     * {@code @Embeddable} type.
     *
     * @param typeElement the qualified type name, may be {@code null}
     * @return {@code true} if the type is an embeddable, {@code false} otherwise
     */
    private boolean isEmbeddableType(TypeElement typeElement) {
        if (typeElement == null)
            return false;
        return AnnotationProcessorUtils.hasAnnotation(typeElement, "jakarta.persistence.Embeddable");
    }

    /**
     * Determines whether the given type mirror represents a
     * {@link Collection}-like type.
     *
     * @param type the type mirror to check
     * @return {@code true} if the type is assignable to
     *         {@code java.util.Collection}, {@code false} otherwise
     */
    private boolean isCollection(TypeMirror type) {
        TypeMirror collectionType = elementUtils.getTypeElement("java.util.Collection").asType();
        return typeUtils.isAssignable(typeUtils.erasure(type), typeUtils.erasure(collectionType));
    }

    /**
     * Resolves the related element type for a field, taking into account element
     * collections,
     * associations and embedded values.
     * <p>
     * The resolution strategy is:
     * </p>
     * <ul>
     * <li>If the type is parameterized, return the first type argument.</li>
     * <li>If annotated as {@code @ElementCollection} without type arguments, use
     * the raw target type.</li>
     * <li>If annotated with {@code @OneToX}/{@code @ManyToX}, validate the target
     * is an {@code @Entity}
     * and return its type.</li>
     * <li>If annotated as {@code @Embedded}, return the embedded type.</li>
     * </ul>
     *
     * @param field               the field to analyse
     * @param isElementCollection whether the field is an element collection
     * @param messager            the messager used for warnings
     * @return the fully qualified related type name, or {@code null} if it cannot
     *         be determined
     */
    private TypeElement resolveRelatedType(VariableElement field, boolean isElementCollection, Messager messager) {
        TypeMirror type = field.asType();

        String typeFqcn = null;
        if (type instanceof DeclaredType dt) {
            Element target = dt.asElement();

            if (!dt.getTypeArguments().isEmpty()) {
                typeFqcn = AnnotationProcessorUtils.getTypeNameWithoutAnnotations(dt.getTypeArguments().getFirst());
            } else if (isElementCollection) {
                typeFqcn = AnnotationProcessorUtils.getTypeNameWithoutAnnotations(dt);
            }

            for (AnnotationMirror ann : field.getAnnotationMirrors()) {
                String annType = ann.getAnnotationType().toString();
                if (annType.startsWith("jakarta.persistence.OneTo") ||
                        annType.startsWith("jakarta.persistence.ManyTo")) {
                    if (!AnnotationProcessorUtils.hasAnnotation(target, "jakarta.persistence.Entity")) {
                        messager.printMessage(Diagnostic.Kind.WARNING,
                                "⚠️ Relation to non-@Entity class: " + target, field);
                    }
                    typeFqcn = AnnotationProcessorUtils.getTypeNameWithoutAnnotations(dt);
                    break;
                }
            }

            if (AnnotationProcessorUtils.hasAnnotation(field, "jakarta.persistence.Embedded")) {
                typeFqcn = AnnotationProcessorUtils.getTypeNameWithoutAnnotations(dt);
            }
        }
        return typeFqcn != null ? elementUtils.getTypeElement(typeFqcn) : null;
    }

    /**
     * Extracts the {@code @MapsId} attribute from the given field if present.
     * <p>
     * This is used to associate a relationship field with a composite identifier
     * attribute.
     * </p>
     *
     * @param field the field annotated with {@code @MapsId}, if any
     * @return the mapped identifier field name, an empty string if no value is
     *         specified, or {@code null} if not annotated
     */
    private String extractMappedId(VariableElement field) {
        for (AnnotationMirror ann : field.getAnnotationMirrors()) {
            if (ann.getAnnotationType().toString().equals("jakarta.persistence.MapsId")) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : ann.getElementValues()
                        .entrySet()) {
                    return entry.getValue().getValue().toString();
                }
                return "";
            }
        }
        return null;
    }

    /**
     * Generates a Java expression that instantiates {@link PersistenceMetadata}
     * from the internal
     * {@link SimplePersistenceMetadata} representation.
     * <p>
     * The resulting string is directly embedded into the generated source code of
     * the registry
     * provider implementation.
     * </p>
     *
     * @param meta the simple metadata descriptor
     * @return a Java expression constructing a {@code PersistenceMetadata} instance
     */
    private String generateMetadataInstantiation(SimplePersistenceMetadata meta) {
        StringBuilder sb = new StringBuilder("new PersistenceMetadata(");

        sb.append(meta.isId).append(", ");
        sb.append(meta.relatedTypeFqcn).append(".class").append(", ");

        if (meta.mappedIdField.isPresent()) {
            sb.append("Optional.of(\"").append(meta.mappedIdField.get()).append("\"), ");
        } else {
            sb.append("Optional.empty(), ");
        }

        if (meta.collection.isPresent()) {
            CollectionMetadata cm = meta.collection.get();
            sb.append("Optional.of(new CollectionMetadata(");
            sb.append("CollectionKind.").append(cm.kind().name()).append(", ");
            sb.append("CollectionType.").append(cm.collectionType().name()).append(", ");

            if (cm.mappedBy().isPresent()) {
                sb.append("Optional.of(\"").append(cm.mappedBy().get()).append("\"), ");
            } else {
                sb.append("Optional.empty(), ");
            }

            if (cm.orderBy().isPresent()) {
                sb.append("Optional.of(\"").append(cm.orderBy().get()).append("\")");
            } else {
                sb.append("Optional.empty()");
            }

            sb.append("))");
        } else {
            sb.append("Optional.empty()");
        }

        sb.append(")");
        return sb.toString();
    }

    /**
     * Lightweight internal representation of persistence metadata for a single
     * field.
     * <p>
     * This record captures:
     * </p>
     * <ul>
     * <li>Whether the field is part of the identifier.</li>
     * <li>The related type name (scalar, entity, embeddable or element type).</li>
     * <li>An optional mapped identifier field name for {@code @MapsId}
     * associations.</li>
     * <li>Optional collection metadata if the field is collection-valued.</li>
     * </ul>
     * <p>
     * Factory methods are provided to create scalar, identifier and collection
     * instances,
     * as well as a {@link #withMappedId(String)} helper to attach identifier
     * mapping information.
     * </p>
     *
     * @param isId          whether the field is part of the entity identifier
     * @param relatedType   the related type for this attribute (maybe {@code null} for primitives)
     * @param relatedTypeFqcn   the related type for this attribute. Never null.
     * @param mappedIdField optional mapped identifier field name for
     *                      {@code @MapsId}
     * @param collection    optional collection metadata if the attribute is
     *                      collection-valued
     */
    public record SimplePersistenceMetadata(
            boolean isId,
            Element relatedType,
            String relatedTypeFqcn,
            Optional<String> mappedIdField,
            Optional<CollectionMetadata> collection) {

        /**
         * Canonical constructor enforcing non-null invariants on {@code relatedType},
         * {@code mappedIdField} and {@code collection}.
         *
         * @param isId          whether the field is part of the identifier
         * @param relatedType   the related type name
         * @param mappedIdField optional mapped identifier field
         * @param collection    optional collection metadata
         */
        public SimplePersistenceMetadata {
            Objects.requireNonNull(relatedTypeFqcn, "relatedTypeFqcn cannot be null");
            Objects.requireNonNull(mappedIdField, "mappedIdField cannot be null");
            Objects.requireNonNull(collection, "collection cannot be null");
        }

        // ==================== Factory Methods ====================

        /**
         * Creates a scalar metadata entry for a non-identifier, non-collection field.
         *
         * @param relatedType the type of the scalar attribute
         * @return a new {@code SimplePersistenceMetadata} instance
         */
        public static SimplePersistenceMetadata scalar(Element relatedType, String relatedTypeFqcn) {
            return new SimplePersistenceMetadata(false, relatedType, relatedTypeFqcn, Optional.empty(), Optional.empty());
        }

        /**
         * Creates an identifier metadata entry for a field annotated with {@code @Id}
         * or {@code @EmbeddedId}.
         *
         * @param relatedType the type of the identifier attribute or embeddable id
         * @return a new {@code SimplePersistenceMetadata} instance marked as identifier
         */
        public static SimplePersistenceMetadata id(Element relatedType, String relatedTypeFqcn) {
            return new SimplePersistenceMetadata(true, relatedType, relatedTypeFqcn,Optional.empty(), Optional.empty());
        }

        /**
         * Creates a collection metadata entry for a collection-valued attribute.
         *
         * @param col the collection
         * @return a new {@code SimplePersistenceMetadata} instance representing a
         *         collection attribute
         */
        public static SimplePersistenceMetadata collection(VariableElement col, Types typeUtils) {
            TypeMirror typeMirror = col.asType();

            if (typeMirror instanceof DeclaredType dt) {
                TypeElement item = (TypeElement) typeUtils.asElement( dt.getTypeArguments().getFirst() );
                return new SimplePersistenceMetadata(
                        false,
                        item,
                        item.toString(),
                        Optional.empty(),
                        Optional.of(analyzeCollection(col, item, typeUtils))
                );
            } else if (typeMirror instanceof ArrayType arrayType) {
                // Cas tableau : on récupère le type élémentaire
                TypeMirror componentType = arrayType.getComponentType();
                if (componentType instanceof DeclaredType compDeclared) {
                    TypeElement item = (TypeElement) compDeclared.asElement();
                    return new SimplePersistenceMetadata(
                            false,
                            item,
                            componentType.toString(),
                            Optional.empty(),
                            Optional.of(analyzeCollection(col, item, typeUtils))
                    );
                }
            }

            throw new IllegalArgumentException("Strange collection type");
        }

        /**
         * Builds {@link CollectionMetadata} for a collection-valued association or
         * element collection.
         * <p>
         * This includes the collection kind (scalar/entity/embeddable), collection type
         * (list, set, map, etc.),
         * {@code mappedBy} side when defined, and any ordering expression declared via
         * {@code @OrderBy}.
         * </p>
         *
         * @param collection the collection field
         * @return a {@link CollectionMetadata} describing the collection
         */
        private static CollectionMetadata analyzeCollection(VariableElement collection, TypeElement item, Types typeUtils) {
            TypeElement element = (TypeElement) typeUtils.asElement(collection.asType());

            CollectionKind kind = AnnotationProcessorUtils.determineCollectionKind(item);
            CollectionType collectionType = AnnotationProcessorUtils.determineCollectionType(collection.asType());
            Optional<String> mappedBy = extractMappedBy(collection);
            Optional<String> orderBy = extractOrderBy(collection);

            return new CollectionMetadata(kind, collectionType, mappedBy, orderBy);
        }


        /**
         * Extracts the {@code mappedBy} attribute from {@code @OneToMany} or
         * {@code @ManyToMany}
         * annotations declared on the given field, if present.
         *
         * @param field the association field to inspect
         * @return an {@link Optional} containing the {@code mappedBy} attribute, or
         *         empty if none is declared
         */
        private static Optional<String> extractMappedBy(VariableElement field) {
            for (AnnotationMirror ann : field.getAnnotationMirrors()) {
                String annType = ann.getAnnotationType().toString();
                if (annType.equals("jakarta.persistence.OneToMany") || annType.equals("jakarta.persistence.ManyToMany")) {
                    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : ann.getElementValues()
                            .entrySet()) {
                        if (entry.getKey().getSimpleName().toString().equals("mappedBy")) {
                            return Optional.of(entry.getValue().getValue().toString());
                        }
                    }
                }
            }
            return Optional.empty();
        }

        /**
         * Extracts the {@code value} attribute from {@code @OrderBy} declared on the
         * given field.
         * <p>
         * If {@code @OrderBy} is present without an explicit value, an empty string is
         * returned
         * to indicate the default ordering.
         * </p>
         *
         * @param field the collection field to inspect
         * @return an {@link Optional} containing the ordering clause or empty if no
         *         {@code @OrderBy} is present
         */
        private static Optional<String> extractOrderBy(VariableElement field) {
            for (AnnotationMirror ann : field.getAnnotationMirrors()) {
                if (ann.getAnnotationType().toString().equals("jakarta.persistence.OrderBy")) {
                    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : ann.getElementValues()
                            .entrySet()) {
                        if (entry.getKey().getSimpleName().toString().equals("value")) {
                            return Optional.of(entry.getValue().getValue().toString());
                        }
                    }
                    return Optional.of("");
                }
            }
            return Optional.empty();
        }

        /**
         * Returns a copy of this metadata with the {@code mappedIdField} set to the
         * given name.
         *
         * @param fieldName the identifier field name mapped by {@code @MapsId}
         * @return a new {@code SimplePersistenceMetadata} instance with the mapped id
         *         information
         */
        public SimplePersistenceMetadata withMappedId(String fieldName) {
            return new SimplePersistenceMetadata(isId, relatedType, relatedTypeFqcn, Optional.of(fieldName), collection);
        }

        /**
         * Indicates whether this metadata describes a collection-valued attribute.
         *
         * @return {@code true} if a collection metadata is present, {@code false}
         *         otherwise
         */
        public boolean isCollection() {
            return collection.isPresent();
        }
    }
}