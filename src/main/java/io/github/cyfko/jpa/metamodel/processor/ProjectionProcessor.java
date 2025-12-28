package io.github.cyfko.jpa.metamodel.processor;

import io.github.cyfko.jpa.metamodel.Projection;
import io.github.cyfko.jpa.metamodel.model.CollectionKind;
import io.github.cyfko.jpa.metamodel.model.CollectionType;
import io.github.cyfko.jpa.metamodel.model.projection.ComputedField;
import io.github.cyfko.jpa.metamodel.model.projection.DirectMapping;

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
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.github.cyfko.jpa.metamodel.processor.ProcessorUtils.BASIC_JPA_TYPES;

/**
 * Processor for @Projection annotated DTOs that generates projection metadata.
 * NOT a standalone annotation processor - used by MetamodelProcessor.
 */
public class ProjectionProcessor {
    private final ProcessingEnvironment processingEnv;
    private final EntityProcessor entityProcessor;
    private final Map<String, SimpleProjectionMetadata> projectionRegistry = new LinkedHashMap<>();

    public ProjectionProcessor(ProcessingEnvironment processingEnv, EntityProcessor entityProcessor) {
        this.processingEnv = processingEnv;
        this.entityProcessor = entityProcessor;
    }

    /**
     * Processes all {@link Projection}
     * annotated classes discovered in the current round. 
     * <p>
     * For each DTO:
     * </p>
     * <ul>
     *   <li>Resolves the target entity type from the {@code @Projection} annotation.</li>
     *   <li>Validates that the entity has been registered by {@link EntityProcessor}.</li>
     *   <li>Collects direct field mappings annotated with {@code @Projected}.</li>
     *   <li>Collects computed fields annotated with {@code @Computed} and validates their computation providers.</li>
     *   <li>Stores the resulting metadata in an internal registry keyed by DTO type.</li>
     * </ul>
     *
     * @param roundEnv the current round environment exposing elements annotated with {@code @Projection}
     */
    public void processProjections(RoundEnvironment roundEnv) {
        Messager messager = processingEnv.getMessager();
        messager.printMessage(Diagnostic.Kind.NOTE, "🎯 Phase 2: Processing @Projection annotations...");

        for (Element element : roundEnv.getElementsAnnotatedWith(Projection.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@Projection can only be applied to classes", element);
                continue;
            }

            TypeElement dtoClass = (TypeElement) element;
            processProjection(dtoClass, messager);
        }

        messager.printMessage(Diagnostic.Kind.NOTE,
                "✅ Processed " + projectionRegistry.size() + " projections");
    }

    /**
     * Returns an unmodifiable view of the collected projection metadata registry. 
     *
     * @return an unmodifiable map where keys are fully qualified DTO class names
     *         and values are {@link SimpleProjectionMetadata} instances
     */
    public Map<String, SimpleProjectionMetadata> getRegistry() {
        return Collections.unmodifiableMap(projectionRegistry);
    }

    /**
     * Generates the {@code ProjectionMetadataRegistryProviderImpl} class implementing
     * {@code ProjectionMetadataRegistryProvider} and exposing all collected projection metadata. 
     * <p>
     * The generated class is written via the {@link javax.annotation.processing.Filer} and contains
     * a static unmodifiable registry initialized at class-load time. It is safe to invoke this
     * method only after all relevant {@code @Projection} DTOs have been processed. 
     * </p>
     */
    public void generateProviderImpl() {
        Messager messager = processingEnv.getMessager();
        messager.printMessage(Diagnostic.Kind.NOTE,
                "🛠️ Generating ProjectionMetadataRegistryProvider implementation...");

        try {
            JavaFileObject file = processingEnv.getFiler()
                    .createSourceFile("io.github.cyfko.jpa.metamodel.providers.ProjectionMetadataRegistryProviderImpl");

            try (Writer writer = file.openWriter()) {
                writeProjectionRegistry(writer);
            }

            messager.printMessage(Diagnostic.Kind.NOTE,
                    "✅ ProjectionMetadataRegistryProviderImpl generated with " + projectionRegistry.size() + " projections");

        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate ProjectionMetadataRegistryProviderImpl: " + e.getMessage());
        }
    }

    /**
     * Processes a single {@code @Projection}-annotated DTO class and records its metadata. 
     * <p>
     * This includes:
     * </p>
     * <ul>
     *   <li>Resolving the targeted JPA entity.</li>
     *   <li>Validating that each {@code @Projected} path exists in the entity/embeddable graph.</li>
     *   <li>Analyzing collection fields to determine collection kind and type.</li>
     *   <li>Resolving {@code @Computed} fields and ensuring corresponding computation provider
     *       methods exist with compatible signatures.</li>
     * </ul>
     *
     * @param dtoClass the DTO type element annotated with {@code @Projection}
     * @param messager the messager used to report notes and errors
     */
    private void processProjection(TypeElement dtoClass, Messager messager) {
        // Get entity class from annotation
        TypeMirror entityTypeMirror = getEntityClass(dtoClass);
        if (entityTypeMirror == null) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Cannot determine entity class from @Projection", dtoClass);
            return;
        }

        TypeElement entityClass = (TypeElement) ((DeclaredType) entityTypeMirror).asElement();
        String entityClassName = entityClass.getQualifiedName().toString();

        messager.printMessage(Diagnostic.Kind.NOTE,
                "🔍 Processing projection: " + dtoClass.getSimpleName() + " → " + entityClass.getSimpleName());

        // Validate entity has metadata (using EntityRegistryProcessor)
        if (!entityProcessor.hasEntityMetadata(entityClassName)) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    String.format(
                            "Entity %s has no metadata. Ensure it is annotated with @Entity or @Embeddable and is public.",
                            entityClassName
                    ),
                    dtoClass);
            return;
        }

        List<SimpleDirectMapping> directMappings = new ArrayList<>();
        List<ComputedField> computedFields = new ArrayList<>();
        List<SimpleComputationProvider> computers = new ArrayList<>();

        // Process computation providers
        ProcessorUtils.processExplicitFields(dtoClass,
                "io.github.cyfko.jpa.metamodel.Projection",
                params -> {
                    @SuppressWarnings("unchecked") List<Map<String,Object>> computersList = (List<Map<String,Object>>) params.get("providers");
                    if (computersList == null) return;

                    computersList.forEach(com -> computers.add(
                                    new SimpleComputationProvider((String) com.get("value"), (String) com.get("bean"))
                            )
                    );
                },
                null
        );

        // Process fields
        for (Element enclosedElement : dtoClass.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.FIELD) continue;

            // Process direct mappings
            ProcessorUtils.processExplicitFields(enclosedElement,
                    "io.github.cyfko.jpa.metamodel.Projected",
                    params -> {

                        String dtoField = enclosedElement.getSimpleName().toString();
                        String entityField;
                        if (params.containsKey("from")) {
                            entityField = params.get("from").toString();
                        } else {
                            entityField = dtoField;
                        }

                        // validate entity field path
                        ValidationResult validation = validateEntityFieldPath(entityClassName, entityField, null);
                        if (!validation.isValid()) {
                            messager.printMessage(Diagnostic.Kind.ERROR,
                                    validation.errorMessage(), enclosedElement);
                            return;
                        }

                        VariableElement field = (VariableElement) enclosedElement;
                        boolean isCollection = isCollection(field.asType());
                        String itemType = resolveRelatedType(field, isCollection, messager);

                        if (isCollection) {
                            DirectMapping.CollectionMetadata collectionMetadata = analyzeCollection(field, itemType);
                            directMappings.add(new SimpleDirectMapping(dtoField, entityField, itemType, Optional.of(collectionMetadata)));
                        } else {
                            directMappings.add(new SimpleDirectMapping(dtoField, entityField, field.asType().toString(), Optional.empty()));
                        }

                        messager.printMessage(Diagnostic.Kind.NOTE,
                                "  ✅ " + dtoField + " → " + entityField);
                    },
                    null
            );

            // Process computed fields
            ProcessorUtils.processExplicitFields(enclosedElement,
                    "io.github.cyfko.jpa.metamodel.Computed",
                    params -> {
                        String dtoField = enclosedElement.getSimpleName().toString();
                        @SuppressWarnings("unchecked") List<String> dependencies = (List<String>) params.get("dependsOn");

                        // Validate DTO field exists
                        if (dependencies.isEmpty()) {
                            messager.printMessage(Diagnostic.Kind.ERROR,
                                    "Computed field '" + dtoField + "' does not declare any dependency in " + dtoClass.getSimpleName(),
                                    dtoClass);
                            return;
                        }

                        // Validate all dependencies exist in entity
                        final Map<String, String> depsToFqcnMapping = new HashMap<>();
                        for (String dependency : dependencies) {
                            ValidationResult validation = validateEntityFieldPath(entityClassName, dependency,fqcn -> depsToFqcnMapping.put(dependency, fqcn));
                            if (!validation.isValid()) {
                                messager.printMessage(Diagnostic.Kind.ERROR, "Computed field '" + dtoField + "': " + validation.errorMessage(), dtoClass);
                                return;
                            }
                        }

                        // Validate that compute method exist in any of provided computation providers
                        ComputedField field = new ComputedField(dtoField, dependencies.toArray(new String[0]));
                        String errMessage = validateComputeMethod(field, enclosedElement.asType(), computers, depsToFqcnMapping);
                        if (errMessage != null){
                            printComputationMethodError(dtoClass, enclosedElement, field, errMessage, computers, depsToFqcnMapping);
                            return;
                        }

                        // Everything OK ! then record this compute field!
                        computedFields.add(field);
                        messager.printMessage(Diagnostic.Kind.NOTE, "  🧮 " + dtoField + " ← [" + String.join(", ", dependencies) + "]");
                    },
                    null
            );
        }

        // Store metadata
        SimpleProjectionMetadata metadata = new SimpleProjectionMetadata(
                entityClassName,
                directMappings,
                computedFields,
                computers.toArray(SimpleComputationProvider[]::new)
        );

        projectionRegistry.put(dtoClass.getQualifiedName().toString(), metadata);
    }

    /**
     * Prints a detailed error message when a computation method for a computed field
     * cannot be resolved or does not match the expected signature. 
     * <p>
     * The message contains:
     * </p>
     * <ul>
     *   <li>The high-level error description.</li>
     *   <li>The DTO source type.</li>
     *   <li>The expected method signature, including parameter names and types.</li>
     *   <li>The list of available computation provider classes.</li>
     * </ul>
     *
     * @param dtoClass        the DTO type declaring the computed field
     * @param enclosedElement the field element representing the computed property
     * @param field           the computed field metadata
     * @param errMessage      the base error message describing the mismatch
     * @param computers       the list of configured computation providers
     * @param depsToFqcnMapping a mapping of dependency paths to their fully qualified types
     */
    private void printComputationMethodError(TypeElement dtoClass,
                                             Element enclosedElement,
                                             ComputedField field,
                                             String errMessage,
                                             List<SimpleComputationProvider> computers,
                                             Map<String, String> depsToFqcnMapping) {

        String expectedMethodSignature = String.format("public %s get%s(%s);",
                enclosedElement.asType().toString(),
                capitalize(field.dtoField()),
                String.join(", ", Arrays.stream(field.dependencies())
                        .map(d -> depsToFqcnMapping.get(d) + " " + getLastSegment(d,"\\."))
                        .toList())
        );

        String computationProviders = computers.stream().map(SimpleComputationProvider::className).collect(Collectors.joining(", "));
        String msg = String.format("%s \n- Source: %s \n- Providers: %s \n- Expected computer's method: %s ",
                errMessage,
                dtoClass.getQualifiedName(),
                computers.isEmpty() ? "<error: undefined provider>" : computationProviders,
                expectedMethodSignature
        );

        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, dtoClass);
    }

    /**
     * Returns the last segment of a dotted or delimited string. 
     *
     * @param content the full content string
     * @param delim   the regular expression delimiter used to split the content
     * @return the last segment after splitting, or the original content if no delimiter is found
     */
    private static String getLastSegment(String content, String delim){
        String[] segments = content.split(delim);
        return segments[segments.length - 1];
    }

    /**
     * Validates that a compute method exists for the given computed field in one of the
     * configured computation provider classes. 
     * <p>
     * Validation covers:
     * </p>
     * <ul>
     *   <li>Method name convention: {@code get} + capitalized DTO field name.</li>
     *   <li>Exact return type matching the computed DTO field type.</li>
     *   <li>Exact parameter count matching the number of declared dependencies.</li>
     *   <li>Exact parameter type matching the resolved dependency types in {@code depsToFqcnMapping}.</li>
     * </ul>
     *
     * @param field            the computed field descriptor
     * @param fieldType        the expected return type of the compute method
     * @param computers        the list of available computation providers
     * @param depsToFqcnMapping mapping from dependency paths to their fully qualified class names
     * @return {@code null} if a compatible compute method is found, otherwise a human-readable error message
     */
    private String validateComputeMethod(ComputedField field,
                                           TypeMirror fieldType,
                                           List<SimpleComputationProvider> computers,
                                           Map<String, String> depsToFqcnMapping) {
        Elements elements = processingEnv.getElementUtils();
        Types types = processingEnv.getTypeUtils();

        final String methodName = "get" + capitalize(field.dtoField());

        for (SimpleComputationProvider provider : computers) {
            TypeElement providerElement = elements.getTypeElement(provider.className());
            if (providerElement == null) continue; // classe introuvable

            for (Element enclosed : providerElement.getEnclosedElements()) {
                if (enclosed.getKind() != ElementKind.METHOD) continue;

                ExecutableElement method = (ExecutableElement) enclosed;
                if (!methodName.equals(method.getSimpleName().toString())) continue;

                // Vérifie le type de retour
                TypeMirror returnType = method.getReturnType();
                if( !types.isSameType(returnType, fieldType) ){
                    return String.format("Method %s.%s has incompatible return type Mismatch on return type. Required: %s, Found: %s.",
                            provider.className,
                            methodName,
                            fieldType.toString(),
                            returnType.toString()
                    );
                }

                // vérifie le nombre d'arguments
                List<? extends VariableElement> parameters = method.getParameters();
                if (parameters.size() != field.dependencies().length){
                    return String.format("Method %s.%s has incompatible parameters count. Required: %s, Found: %s.",
                            provider.className,
                            methodName,
                            field.dependencies().length,
                            parameters.size()
                    );
                }

                // Vérifie le type d'arguments
                for (int i = 0; i < parameters.size(); i++) {
                    String methodParamFqcn = parameters.get(i).asType().toString();
                    String dependencyFqcn = depsToFqcnMapping.get(field.dependencies()[i]);
                    if (!methodParamFqcn.equals(dependencyFqcn)) {
                        return String.format("Method %s.%s has incompatible type on parameter at position %d. Required: %s, Found: %s.",
                                provider.className,
                                methodName,
                                i,
                                dependencyFqcn,
                                methodParamFqcn
                        );
                    }
                }

                return null;
            }
        }

        return String.format("No matching provider method found for computed field '%s'.", field.dtoField());
    }

    /**
     * Determines whether the given type represents a {@link java.util.Collection}-like type
     * (including subtypes such as {@link java.util.List} or {@link java.util.Set}). 
     *
     * @param type the type to inspect
     * @return {@code true} if the type is assignable to {@code java.util.Collection}, {@code false} otherwise
     */
    private boolean isCollection(TypeMirror type) {
        Types types = processingEnv.getTypeUtils();
        TypeMirror collectionType = processingEnv.getElementUtils()
                .getTypeElement("java.util.Collection").asType();
        return types.isAssignable(types.erasure(type), types.erasure(collectionType));
    }

    /**
     * Resolves the element type for a collection-related field. 
     * <p>
     * If the field is a parameterized collection, the first generic argument type is returned.
     * If the field is treated as an element collection but has no type arguments, the raw
     * declared type is returned. Otherwise, {@code null} is returned. 
     * </p>
     *
     * @param field               the field element to analyze
     * @param isElementCollection whether the field is known to represent an element collection
     * @param messager            the messager to use for diagnostics if needed
     * @return the fully qualified name of the element type, or {@code null} if it cannot be determined
     */
    private String resolveRelatedType(VariableElement field, boolean isElementCollection, Messager messager) {
        TypeMirror type = field.asType();
        if (type instanceof DeclaredType dt) {
            Element target = dt.asElement();

            if (!dt.getTypeArguments().isEmpty()) {
                return dt.getTypeArguments().getFirst().toString();
            } else if (isElementCollection) {
                return target.toString();
            }
        }
        return null;
    }

    /**
     * Analyzes a collection-mapped DTO field and derives its {@link CollectionKind}
     * and {@link CollectionType}. 
     *
     * @param field       the DTO field representing a collection
     * @param elementType the fully qualified name of the element type
     * @return collection metadata describing the kind (scalar, entity, embeddable) and collection type
     */
    private DirectMapping.CollectionMetadata analyzeCollection(VariableElement field, String elementType) {
        CollectionKind kind = ProcessorUtils.determineCollectionKind(elementType, processingEnv);
        CollectionType collectionType = ProcessorUtils.determineCollectionType(field.asType());

        return new DirectMapping.CollectionMetadata(kind, collectionType);
    }

    /**
     * Extracts the entity class type mirror from the {@link Projection} annotation
     * present on the given DTO type. 
     *
     * @param dtoClass the DTO type annotated with {@code @Projection}
     * @return the type mirror of the targeted entity, or {@code null} if not specified or not resolvable
     */
    private TypeMirror getEntityClass(TypeElement dtoClass) {
        for (AnnotationMirror mirror : dtoClass.getAnnotationMirrors()) {
            if (!mirror.getAnnotationType().toString()
                    .equals("io.github.cyfko.jpa.metamodel.Projection")) {
                continue;
            }

            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                    mirror.getElementValues().entrySet()) {
                if (entry.getKey().getSimpleName().toString().equals("entity")) {
                    return (TypeMirror) entry.getValue().getValue();
                }
            }
        }
        return null;
    }

    /**
     * Validates that a dotted entity field path (e.g. {@code "address.city"}) exists
     * and can be navigated using metadata provided by {@link EntityProcessor}.
     * <p>
     * The method walks through entity and embeddable metadata, segment by segment, ensuring
     * that each intermediate segment is non-scalar and that the final segment is present.
     * When {@code withFqcn} is provided and the path is valid, the fully qualified type of
     * the last segment is passed to the consumer. 
     * </p>
     *
     * @param entityClassName the fully qualified name of the root entity
     * @param fieldPath       the dotted path to validate
     * @param withFqcn        optional consumer invoked with the fully qualified type of the last segment
     * @return a {@link ValidationResult} describing whether the path is valid and, if not, the error message
     */
    private ValidationResult validateEntityFieldPath(String entityClassName, String fieldPath, Consumer<String> withFqcn) {
        // Get entity metadata from EntityRegistryProcessor
        Map<String, Map<String, EntityProcessor.SimplePersistenceMetadata>> entityRegistry = entityProcessor.getRegistry();
        Map<String, Map<String, EntityProcessor.SimplePersistenceMetadata>> embeddableRegistry = entityProcessor.getEmbeddableRegistry();
        Map<String, EntityProcessor.SimplePersistenceMetadata> entityMetadata = entityRegistry.get(entityClassName);

        if (entityMetadata == null) {
            return ValidationResult.error("Entity " + entityClassName + " not found in registry");
        }

        // Handle nested paths (e.g., "address.city")
        String[] segments = fieldPath.split("\\.");
        String currentClassName = entityClassName;

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];

            // Get metadata for current class
            Map<String, EntityProcessor.SimplePersistenceMetadata> currentMetadata = entityRegistry.get(currentClassName);
            if (currentMetadata == null) {
                currentMetadata = embeddableRegistry.get(currentClassName);
            }

            if (currentMetadata == null) {
                return ValidationResult.error(
                        String.format("Field '%s' not found in %s (path: %s)",
                                segment, currentClassName, fieldPath)
                );
            }

            // Check if field exists in metadata
            EntityProcessor.SimplePersistenceMetadata fieldMetadata = currentMetadata.get(segment);
            if (fieldMetadata == null) {
                return ValidationResult.error(
                        String.format("Field '%s' not found in entity %s (path: %s)",
                                segment, getSimpleName(currentClassName), fieldPath)
                );
            }

            // If not the last segment, navigate to related type
            if (i < segments.length - 1) {
                if (BASIC_JPA_TYPES.contains(fieldMetadata.relatedType())) {
                    return ValidationResult.error(
                            String.format("Cannot navigate through scalar field '%s' in %s",
                                    segment, getSimpleName(currentClassName))
                    );
                }

                currentClassName = fieldMetadata.relatedType();
            } else if (withFqcn != null) {
                withFqcn.accept(fieldMetadata.relatedType());
            }
        }

        return ValidationResult.valid();
    }

    /**
     * Retrieves a {@link java.lang.reflect.Field} from the given class, searching in its
     * superclass hierarchy if necessary. 
     *
     * @param clazz     the starting class to inspect
     * @param fieldName the name of the field to locate
     * @return the reflective {@link java.lang.reflect.Field} instance
     * @throws NoSuchFieldException if the field cannot be found in the class or any of its superclasses
     */
    private java.lang.reflect.Field getFieldFromClass(Class<?> clazz, String fieldName)
            throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(
                String.format("Field '%s' not found in %s or its superclasses",
                        fieldName, clazz.getSimpleName())
        );
    }

    /**
     * Returns the simple class name extracted from a fully qualified class name. 
     *
     * @param fqcn the fully qualified class name
     * @return the simple name (segment after the last dot) or the original string if no dot is present
     */
    private String getSimpleName(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        return lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn;
    }

    /**
     * Writes the body of the generated {@code ProjectionMetadataRegistryProviderImpl} class
     * to the given writer. 
     * <p>
     * The generated code initializes a static registry of {@code ProjectionMetadata} entries,
     * one per processed DTO, and implements the provider interface by returning an unmodifiable
     * view of that registry. 
     * </p>
     *
     * @param writer the writer used to output Java source code
     * @throws IOException if an error occurs while writing to the underlying stream
     */
    private void writeProjectionRegistry(Writer writer) throws IOException {
        writer.write("package io.github.cyfko.jpa.metamodel.providers;\n\n");
        writer.write("import io.github.cyfko.jpa.metamodel.model.projection.*;\n");
        writer.write("import io.github.cyfko.jpa.metamodel.model.CollectionKind;\n");
        writer.write("import io.github.cyfko.jpa.metamodel.model.CollectionType;\n");
        writer.write("import java.util.Map;\n");
        writer.write("import java.util.HashMap;\n");
        writer.write("import java.util.List;\n");
        writer.write("import java.util.Collections;\n");
        writer.write("import java.util.Optional;\n\n");

        writer.write("/**\n");
        writer.write(" * Generated projection metadata provider implementation.\n");
        writer.write(" * DO NOT EDIT - This file is automatically generated.\n");
        writer.write(" */\n");
        writer.write("public class ProjectionMetadataRegistryProviderImpl implements ProjectionMetadataRegistryProvider {\n\n");

        writer.write("    private static final Map<Class<?>, ProjectionMetadata> REGISTRY;\n\n");

        writer.write("    static {\n");
        writer.write("        Map<Class<?>, ProjectionMetadata> registry = new HashMap<>();\n\n");

        for (var entry : projectionRegistry.entrySet()) {
            writeProjectionEntry(writer, entry);
        }

        writer.write("        REGISTRY = Collections.unmodifiableMap(registry);\n");
        writer.write("    }\n\n");

        writer.write("    @Override\n");
        writer.write("    public Map<Class<?>, ProjectionMetadata> getProjectionMetadataRegistry() {\n");
        writer.write("        return REGISTRY;\n");
        writer.write("    }\n");
        writer.write("}\n");
    }

    /**
     * Writes a single projection entry into the generated registry initialization block. 
     *
     * @param writer the writer used to output Java source code
     * @param entry  the metadata entry keyed by the DTO fully qualified name
     * @throws IOException if an error occurs while writing to the underlying stream
     */
    private void writeProjectionEntry(Writer writer, Map.Entry<String, SimpleProjectionMetadata> entry) throws IOException {
        String dtoType = entry.getKey();
        SimpleProjectionMetadata metadata = entry.getValue();

        StringBuilder sb = new StringBuilder();
        sb.append("        // ").append(dtoType).append(" → ").append(metadata.entityClass()).append("\n");
        sb.append("        registry.put(\n");
        sb.append("            ").append(dtoType).append(".class,\n");
        sb.append("            new ProjectionMetadata(\n");
        sb.append("                ").append(metadata.entityClass()).append(".class,\n");

        // Direct mappings
        sb.append("                new DirectMapping[]{\n");
        for (int i = 0; i < metadata.directMappings().size(); i++) {
            sb.append(formatDirectMapping(metadata.directMappings().get(i)));
            sb.append(i < metadata.directMappings().size() - 1 ? ",\n" : "\n");
        }
        sb.append("                },\n");

        // Computed fields
        sb.append("                new ComputedField[]{\n");
        for (int i = 0; i < metadata.computedFields().size(); i++) {
            sb.append(formatComputedField(metadata.computedFields().get(i)));
            sb.append(i < metadata.computedFields().size() - 1 ? ",\n" : "\n");
        }
        sb.append("                },\n");

        // Computers providers
        sb.append("                new ComputationProvider[]{\n");
        for (int i = 0; i < metadata.computers().length; i++) {
            sb.append(formatComputerProvider(metadata.computers()[i]));
            sb.append(i < metadata.computers().length - 1 ? ",\n" : "\n");
        }
        sb.append("                }\n");

        sb.append("            )\n");
        sb.append("        );\n");

        writer.write(sb.toString());
    }

    /**
     * Formats a {@link SimpleDirectMapping} instance as a Java code snippet that constructs
     * a corresponding {@link DirectMapping} in the generated registry. 
     *
     * @param m the simple direct mapping metadata
     * @return a Java expression string constructing a {@code DirectMapping}
     */
    private String formatDirectMapping(SimpleDirectMapping m) {
        String collection = m.collection()
                .map(c -> "Optional.of(" + c.asInstance() + ")")
                .orElse("Optional.empty()");
        return String.format(
                "                    new DirectMapping(\"%s\", \"%s\", %s.class, %s)",
                m.dtoField(), m.entityField(), m.dtoFieldType(), collection
        );
    }

    /**
     * Formats a {@link ComputedField} instance as a Java expression suitable for inclusion
     * in the generated registry source code. 
     *
     * @param f the computed field descriptor
     * @return a Java expression string constructing a {@code ComputedField}
     */
    private String formatComputedField(ComputedField f) {
        String deps = Arrays.stream(f.dependencies())
                .map(d -> "\"" + d + "\"")
                .collect(Collectors.joining(", "));
        return String.format(
                "                    new ComputedField(\"%s\", new String[]{%s})",
                f.dtoField(), deps
        );
    }

    /**
     * Formats a {@link SimpleComputationProvider} instance as a Java expression constructing
     * a {@code ComputationProvider} in the generated registry. 
     *
     * @param c the computation provider metadata
     * @return a Java expression string constructing a {@code ComputationProvider}
     */
    private String formatComputerProvider(SimpleComputationProvider c) {
        return String.format(
                "                    new ComputationProvider(%s.class, \"%s\")",
                c.className(), c.bean()
        );
    }

    /**
     * Capitalizes the first character of the given string, leaving the remainder unchanged.
     * <p>
     * This method is null-safe and returns the input as-is when the string is {@code null}
     * or empty. It is typically used to build JavaBean-style accessor names from field
     * identifiers, for example when resolving {@code getXxx} methods via reflection.
     * </p>
     *
     * @param str the input string, possibly {@code null} or empty
     * @return the capitalized string, or the original value if {@code null} or empty
     */
    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Lightweight value object describing a direct mapping between a DTO field and
     * an entity field, including optional collection metadata. 
     *
     * @param dtoField       the DTO field name
     * @param entityField    the entity field path
     * @param dtoFieldType   the DTO field type as a fully qualified name
     * @param collection     optional collection metadata if the field represents a collection
     */
    private record SimpleDirectMapping(String dtoField,
                                       String entityField,
                                       String dtoFieldType,
                                       Optional<DirectMapping.CollectionMetadata> collection){}

    /**
     * Aggregated projection metadata used internally by the processor before being written
     * to generated source code. 
     *
     * @param entityClass    the fully qualified name of the projected JPA entity
     * @param directMappings the list of direct property mappings
     * @param computedFields the list of computed fields with their dependencies
     * @param computers      the computation provider descriptors used for evaluating computed fields
     */
    public record SimpleProjectionMetadata(String entityClass,
                                            List<SimpleDirectMapping> directMappings,
                                            List<ComputedField> computedFields,
                                            SimpleComputationProvider[] computers){}

    /**
     * Describes a computation provider class and the bean name used to obtain its instance. 
     *
     * @param className the fully qualified provider class name
     * @param bean      the bean identifier used to resolve the provider in the runtime container
     */
    public record SimpleComputationProvider(String className, String bean) { }

    /**
     * Simple validation result record used to report success or failure of structural checks. 
     *
     * @param isValid      {@code true} if validation succeeded, {@code false} otherwise
     * @param errorMessage the error message when invalid, or {@code null} when valid
     */
    record ValidationResult(boolean isValid, String errorMessage) {
        static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }
    }
}