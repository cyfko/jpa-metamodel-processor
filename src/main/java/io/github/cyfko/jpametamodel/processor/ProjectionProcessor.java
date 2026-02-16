package io.github.cyfko.jpametamodel.processor;

import io.github.cyfko.jpametamodel.providers.ProjectionRegistryProvider;
import io.github.cyfko.projection.Computed;
import io.github.cyfko.projection.Projected;
import io.github.cyfko.projection.Projection;
import io.github.cyfko.jpametamodel.api.CollectionKind;
import io.github.cyfko.jpametamodel.api.CollectionType;
import io.github.cyfko.jpametamodel.api.ComputedField;
import io.github.cyfko.jpametamodel.api.DirectMapping;

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

import static io.github.cyfko.jpametamodel.processor.AnnotationProcessorUtils.BASIC_JPA_TYPES;
import static io.github.cyfko.jpametamodel.processor.StringUtils.hasJavaNamingConvention;
import static io.github.cyfko.jpametamodel.processor.StringUtils.toJavaNamingAwareFieldName;

/**
 * Processor for @Projection annotated DTOs that generates projection metadata.
 * NOT a standalone annotation processor - used by MetamodelProcessor.
 */
public class ProjectionProcessor {
    private final ProcessingEnvironment processingEnv;
    private final EntityProcessor entityProcessor;
    private final Map<String, SimpleProjectionMetadata> projectionRegistry = new LinkedHashMap<>();
    private final List<TypeElement> referencedProjections = new ArrayList<>();

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
     * <li>Resolves the target entity type from the {@code @Projection}
     * annotation.</li>
     * <li>Validates that the entity has been registered by
     * {@link EntityProcessor}.</li>
     * <li>Collects direct field mappings annotated with {@code @Projected}.</li>
     * <li>Collects computed fields annotated with {@code @Computed} and validates
     * their computation providers.</li>
     * <li>Stores the resulting metadata in an internal registry keyed by DTO
     * type.</li>
     * </ul>
     *
     */
    public void processProjections() {
        Messager messager = processingEnv.getMessager();

        for (TypeElement dtoClass : referencedProjections) {
            try {
                processProjection(dtoClass, messager);
                messager.printMessage(Diagnostic.Kind.NOTE, "✅ Processed " + projectionRegistry.size() + " projections");
            } catch (Exception e) {
                messager.printError(e.getMessage(), dtoClass);
            }
        }
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
     * Generates the {@code ProjectionRegistryProviderImpl} class
     * implementing
     * {@code ProjectionRegistryProvider} and exposing all collected
     * projection metadata.
     * <p>
     * The generated class is written via the
     * {@link javax.annotation.processing.Filer} and contains
     * a static unmodifiable registry initialized at class-load time. It is safe to
     * invoke this
     * method only after all relevant {@code @Projection} DTOs have been processed.
     * </p>
     */
    public void generateProviderImpl() {
        Messager messager = processingEnv.getMessager();
        messager.printMessage(Diagnostic.Kind.NOTE,
                "🛠️ Generating ProjectionRegistryProvider implementation...");

        try {
            JavaFileObject file = processingEnv.getFiler()
                    .createSourceFile("io.github.cyfko.jpametamodel.providers.impl.ProjectionRegistryProviderImpl");

            try (Writer writer = file.openWriter()) {
                writeProjectionRegistry(writer);
            }

            messager.printMessage(Diagnostic.Kind.NOTE,
                    "✅ ProjectionRegistryProviderImpl generated with " + projectionRegistry.size() + " projections");

        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate ProjectionRegistryProviderImpl: " + e.getMessage());
        }

        Helper.generateServiceProviderInfo(
                processingEnv,
                ProjectionRegistryProvider.class,
                "io.github.cyfko.jpametamodel.providers.impl.ProjectionRegistryProviderImpl"
        );
    }

    /**
     * Processes a single {@code @Projection}-annotated DTO class and records its
     * metadata.
     * <p>
     * This includes:
     * </p>
     * <ul>
     * <li>Resolving the targeted JPA entity.</li>
     * <li>Validating that each {@code @Projected} path exists in the
     * entity/embeddable graph.</li>
     * <li>Analyzing collection fields to determine collection kind and type.</li>
     * <li>Resolving {@code @Computed} fields and ensuring corresponding computation
     * provider
     * methods exist with compatible signatures.</li>
     * </ul>
     *
     * @param dtoClass the DTO type element annotated with {@code @Projection}
     * @param messager the messager used to report notes and errors
     */
    private void processProjection(TypeElement dtoClass, Messager messager) {
        // Get entity class from annotation
        TypeMirror entityTypeMirror = getEntityClass(dtoClass);
        if (entityTypeMirror == null) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Cannot determine entity class from @Projection", dtoClass);
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
                            entityClassName),
                    dtoClass);
            return;
        }

        List<SimpleDirectMapping> directMappings = new ArrayList<>();
        List<SimpleComputedField> computedFields = new ArrayList<>();
        List<SimpleComputationProvider> computers = new ArrayList<>();

        // Process computation providers
        AnnotationProcessorUtils.processExplicitFields(dtoClass,
                Projection.class.getName(),
                params -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> computersList = (List<Map<String, Object>>) params.get("providers");
                    if (computersList == null)
                        return;

                    computersList.forEach(com -> computers.add(
                            new SimpleComputationProvider((String) com.get("value"), (String) com.get("bean"))));
                },
                null);

        // Process fields
        for (Element enclosedElement : dtoClass.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD)
                continue;
            Set<Modifier> modifiers = enclosedElement.getModifiers();
            if (modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.PRIVATE))
                continue; // Skip static and private element

            // Process direct mappings
            final ExecutableElement methodElement = (ExecutableElement) enclosedElement;
            AnnotationProcessorUtils.processExplicitFields(
                    methodElement,
                    Projected.class.getName(),
                    params -> {
                        // validate entity field path
                        String entityField = params.get("from").toString();
                        insertDirectMapping(methodElement, entityClassName, entityField, directMappings);
                    },
                    () -> {
                        // Automatically consider this method if it is not a @Computed field and start by getXxxx
                        // as Java Naming Convention.
                        String name = methodElement.getSimpleName().toString();
                        if (methodElement.getAnnotation(Computed.class) != null || !hasJavaNamingConvention(name)) {
                            return;
                        }
                        name = toJavaNamingAwareFieldName(methodElement);
                        insertDirectMapping(methodElement, entityClassName, name, directMappings);
                    });

            // Process computed fields
            AnnotationProcessorUtils.processExplicitFields(methodElement,
                    Computed.class.getName(),
                    params -> {
                        String dtoField = toJavaNamingAwareFieldName(methodElement);

                        @SuppressWarnings("unchecked")
                        List<String> dependencies = (List<String>) params.get("dependsOn");

                        // Validate DTO field exists
                        if (dependencies.isEmpty()) {
                            messager.printMessage(Diagnostic.Kind.ERROR, String.format(
                                    "@Computed method '%s' does not declare any dependency in %s",
                                    dtoClass.getSimpleName(), dtoClass)
                            );
                            return;
                        }

                        // Validate all dependencies exist in entity
                        final Map<String, String> depsToFqcnMapping = new HashMap<>();
                        for (String dependency : dependencies) {
                            String errorMessage = validateEntityFieldPath(entityClassName, dependency,
                                    fqcn -> depsToFqcnMapping.put(dependency, fqcn));
                            if (errorMessage != null) {
                                messager.printMessage(Diagnostic.Kind.ERROR,
                                        String.format("Computed field '%s': %s", dtoField,  errorMessage),
                                        dtoClass
                                );
                                return;
                            }
                        }

                        // Extraction de @Method (computedBy)
                        @SuppressWarnings("unchecked")
                        Map<String, Object> computedBy = (Map<String, Object>) params.get("computedBy");
                        String computedByClass = computedBy != null ? (String) computedBy.get("type") : null;
                        String computedByMethod = computedBy != null ? (String) computedBy.get("value") : null;

                        // Backward compatibility
                        if (computedByMethod == null && computedBy != null) {
                            computedByMethod = (String) computedBy.get("method");
                        }

                        // Extract reducers
                        @SuppressWarnings("unchecked")
                        var reducersList = (List<String>) params.get("reducers");
                        var reducerNames = reducersList != null ? reducersList.toArray(new String[0]) : new String[0];

                        // Validate reducers: each collection dependency MUST have a reducer
                        List<String> collectionDeps = findCollectionDependencies(entityClassName, dependencies);
                        if (!collectionDeps.isEmpty() && reducerNames.length != collectionDeps.size()) {
                            messager.printMessage(Diagnostic.Kind.ERROR,
                                    String.format("Computed field '%s': reducers count (%d) must match " +
                                            "collection dependency count (%d). Collection dependencies: %s",
                                            dtoField, reducerNames.length, collectionDeps.size(), collectionDeps),
                                    dtoClass);
                            return;
                        }

                        // Build reducer indices (index of each collection dependency in the
                        // dependencies array)
                        int[] reducerIndices = new int[collectionDeps.size()];
                        for (int i = 0; i < collectionDeps.size(); i++) {
                            reducerIndices[i] = dependencies.indexOf(collectionDeps.get(i));
                        }

                        // Validate that compute method exist in any of provided computation providers
                        SimpleComputedField field = new SimpleComputedField(
                                dtoField,
                                dependencies.toArray(new String[0]),
                                reducerIndices,
                                reducerNames,
                                computedByClass,
                                computedByMethod
                        );
                        String errMessage = validateComputeMethod(field, methodElement, computers, depsToFqcnMapping);
                        if (errMessage != null) {
                            printComputationMethodError(dtoClass, methodElement, field, errMessage, computers, depsToFqcnMapping);
                            return;
                        }

                        // Everything OK ! then record this compute field!
                        computedFields.add(field);
                        messager.printMessage(Diagnostic.Kind.NOTE,
                                "  🧮 " + dtoField + " ← [" + String.join(", ", dependencies) + "]" +
                                        (reducerNames.length > 0 ? " ⬇ [" + String.join(", ", reducerNames) + "]"
                                                : ""));
                    },
                    null);
        }

        // Store metadata
        SimpleProjectionMetadata metadata = new SimpleProjectionMetadata(
                entityClassName,
                directMappings,
                computedFields,
                computers.toArray(SimpleComputationProvider[]::new));

        projectionRegistry.put(dtoClass.getQualifiedName().toString(), metadata);
    }

    private void insertDirectMapping(ExecutableElement dtoMethod,
                                     String entityClassName,
                                     String entityField,
                                     List<SimpleDirectMapping> directMappings) {
        Messager messager = this.processingEnv.getMessager();
        if (!dtoMethod.getParameters().isEmpty()) {
            messager.printMessage(Diagnostic.Kind.ERROR, "@Projected methods can not have parameters.", dtoMethod);
            return;
        }

        String errorMessage = validateEntityFieldPath(entityClassName, entityField, null);
        if (errorMessage != null) {
            messager.printMessage(Diagnostic.Kind.ERROR, errorMessage, dtoMethod);
            return;
        }

        TypeMirror dtoType = dtoMethod.getReturnType();
        String dtoName = toJavaNamingAwareFieldName(dtoMethod);
        boolean isCollection = isCollection(dtoType);
        String itemType = resolveRelatedType(dtoType, isCollection);

        if (isCollection) {
            DirectMapping.CollectionMetadata collectionMetadata = analyzeCollection(dtoType, itemType);
            directMappings.add(new SimpleDirectMapping(
                    dtoName,
                    entityField,
                    itemType,
                    Optional.of(collectionMetadata))
            );
        } else {
            directMappings.add(new SimpleDirectMapping(
                    dtoName,
                    entityField,
                    AnnotationProcessorUtils.getTypeNameWithoutAnnotations(dtoType),
                    Optional.empty())
            );
        }

        messager.printMessage(Diagnostic.Kind.NOTE, "  ✅ " + dtoName + " → " + entityField);
    }

    /**
     * Prints a detailed error message when a computation method for a computed
     * field
     * cannot be resolved or does not match the expected signature.
     * <p>
     * The message contains:
     * </p>
     * <ul>
     * <li>The high-level error description.</li>
     * <li>The DTO source type.</li>
     * <li>The expected method signature, including parameter names and types.</li>
     * <li>The list of available computation provider classes.</li>
     * </ul>
     *
     * @param dtoClass          the DTO type declaring the computed field
     * @param methodElement     method element representing the computed property
     * @param field             the computed field metadata
     * @param errMessage        the base error message describing the mismatch
     * @param computers         the list of configured computation providers
     * @param depsToFqcnMapping a mapping of dependency paths to their fully
     *                          qualified types
     */
    private void printComputationMethodError(
            TypeElement dtoClass,
            ExecutableElement methodElement,
            SimpleComputedField field,
            String errMessage,
            List<SimpleComputationProvider> computers,
            Map<String, String> depsToFqcnMapping) {

        String expectedMethodSignature = String.format(
                "public %s %s(%s);",
                methodElement.getReturnType().toString(),
                field.methodName != null ? field.methodName : "to" + StringUtils.capitalize(field.dtoField()),
                String.join(", ", Arrays.stream(field.dependencies())
                        .map(d -> depsToFqcnMapping.get(d) + " " + getLastSegment(d, "\\."))
                        .toList())
        );

        if (field.methodClass != null) {
            computers = List.of(new SimpleComputationProvider(field.methodClass, null));
        }

        String computationProviders = computers.stream().map(SimpleComputationProvider::className)
                .collect(Collectors.joining(", "));
        String msg = String.format("%s \n- Source: %s \n- Providers: %s \n- Expected computing method: %s ",
                errMessage,
                dtoClass.getQualifiedName(),
                computers.isEmpty() ? "<error: undefined provider>" : computationProviders,
                expectedMethodSignature);

        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, dtoClass);
    }

    /**
     * Returns the last segment of a dotted or delimited string.
     *
     * @param content the full content string
     * @param delim   the regular expression delimiter used to split the content
     * @return the last segment after splitting, or the original content if no
     *         delimiter is found
     */
    private static String getLastSegment(String content, String delim) {
        String[] segments = content.split(delim);
        return segments[segments.length - 1];
    }

    /**
     * Validates that a compute method exists for the given computed field in one of
     * the
     * configured computation provider classes.
     * <p>
     * Validation covers:
     * </p>
     * <ul>
     * <li>Method name convention: {@code get} + capitalized DTO field name.</li>
     * <li>Exact return type matching the computed DTO field type.</li>
     * <li>Exact parameter count matching the number of declared dependencies.</li>
     * <li>Exact parameter type matching the resolved dependency types in
     * {@code depsToFqcnMapping}.</li>
     * </ul>
     *
     * @param field             the computed field descriptor
     * @param informativeMethod  the @Computed annotated method
     * @param computers         the list of available computation providers
     * @param depsToFqcnMapping mapping from dependency paths to their fully
     *                          qualified class names
     * @return {@code null} if a compatible compute method is found, otherwise a
     *         human-readable error message
     */
    private String validateComputeMethod(
            SimpleComputedField field,
            ExecutableElement informativeMethod,
            List<SimpleComputationProvider> computers,
            Map<String, String> depsToFqcnMapping) {

        TypeMirror fieldType = informativeMethod.getReturnType();
        Elements elements = processingEnv.getElementUtils();
        Types types = processingEnv.getTypeUtils();

        final String methodName = (field.methodName != null && !field.methodName().isBlank()) ?
                field.methodName() :
                "to" + StringUtils.capitalize(field.dtoField());

        if (field.methodClass != null) {
            computers = List.of(new SimpleComputationProvider(field.methodClass, null));
        }

        for (SimpleComputationProvider provider : computers) {
            TypeElement providerElement = elements.getTypeElement(provider.className());
            if (providerElement == null)
                continue; // classe introuvable

            for (Element enclosed : providerElement.getEnclosedElements()) {
                if (enclosed.getKind() != ElementKind.METHOD)
                    continue;

                ExecutableElement method = (ExecutableElement) enclosed;
                if (!methodName.equals(method.getSimpleName().toString()))
                    continue;

                // Vérifie le type de retour
                TypeMirror returnType = method.getReturnType();
                if (!types.isSameType(returnType, fieldType)) {
                    return String.format("Method %s.%s has incompatible return type. Required: %s, Found: %s.",
                            provider.className,
                            methodName,
                            fieldType.toString(),
                            returnType.toString());
                }

                // vérifie le nombre d'arguments
                List<? extends VariableElement> parameters = method.getParameters();
                if (parameters.size() != field.dependencies().length) {
                    return String.format("Method %s.%s has incompatible parameters count. Required: %s, Found: %s.",
                            provider.className,
                            methodName,
                            field.dependencies().length,
                            parameters.size());
                }

                // Vérifie le type d'arguments
                for (int i = 0; i < parameters.size(); i++) {
                    String methodParamFqcn = parameters.get(i).asType().toString();
                    String dependencyFqcn = depsToFqcnMapping.get(field.dependencies()[i]);
                    if (!methodParamFqcn.equals(dependencyFqcn)) {
                        return String.format(
                                "Method %s.%s has incompatible type on parameter[%d]. Required: %s, Found: %s.",
                                provider.className,
                                methodName,
                                i,
                                dependencyFqcn,
                                methodParamFqcn);
                    }
                }

                return null;
            }
        }

        return String.format("No matching provider found for @Computed method '%s'.", informativeMethod.getSimpleName());
    }

    /**
     * Determines whether the given type represents a
     * {@link java.util.Collection}-like type
     * (including subtypes such as {@link java.util.List} or {@link java.util.Set}).
     *
     * @param type the type to inspect
     * @return {@code true} if the type is assignable to
     *         {@code java.util.Collection}, {@code false} otherwise
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
     * If the field is a parameterized collection, the first generic argument type
     * is returned.
     * If the field is treated as an element collection but has no type arguments,
     * the raw
     * declared type is returned. Otherwise, {@code null} is returned.
     * </p>
     *
     * @param field               the field element to analyze
     * @param isElementCollection whether the field is known to represent an element
     *                            collection
     * @return the fully qualified name of the element type, or {@code null} if it
     *         cannot be determined
     */
    private String resolveRelatedType(TypeMirror field, boolean isElementCollection) {
        if (field instanceof DeclaredType dt) {

            if (!dt.getTypeArguments().isEmpty()) {
                return AnnotationProcessorUtils.getTypeNameWithoutAnnotations(dt.getTypeArguments().getFirst());
            } else if (isElementCollection) {
                return AnnotationProcessorUtils.getTypeNameWithoutAnnotations(dt);
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
     * @return collection metadata describing the kind (scalar, entity, embeddable)
     *         and collection type
     */
    private DirectMapping.CollectionMetadata analyzeCollection(TypeMirror field, String elementType) {
        CollectionKind kind = AnnotationProcessorUtils.determineCollectionKind(elementType, processingEnv);
        CollectionType collectionType = AnnotationProcessorUtils.determineCollectionType(field);

        return new DirectMapping.CollectionMetadata(kind, collectionType);
    }

    /**
     * Extracts the entity class type mirror from the {@link Projection} annotation
     * present on the given DTO type.
     *
     * @param dtoClass the DTO type annotated with {@code @Projection}
     * @return the type mirror of the targeted entity, or {@code null} if not
     *         specified or not resolvable
     */
    private TypeMirror getEntityClass(TypeElement dtoClass) {
        for (AnnotationMirror mirror : dtoClass.getAnnotationMirrors()) {
            if (!Projection.class.getName().equals(mirror.getAnnotationType().toString())) {
                continue;
            }

            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : mirror.getElementValues()
                    .entrySet()) {
                if (entry.getKey().getSimpleName().toString().equals("from")) {
                    try {
                        return (TypeMirror) entry.getValue().getValue();
                    } catch (ClassCastException e) {
                        this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
                    }
                }
            }
        }
        return null;
    }

    /**
     * Validates that a dotted entity field path (e.g. {@code "address.city"})
     * exists
     * and can be navigated using metadata provided by {@link EntityProcessor}.
     * <p>
     * This method walks through entity and embeddable metadata, segment by segment,
     * ensuring
     * that each intermediate segment is non-scalar and that the final segment is
     * present.
     * When {@code withFqcn} is provided and the path is valid, the fully qualified
     * type of
     * the last segment is passed to the consumer.
     * </p>
     *
     * <p>
     * <b>Validation rules:</b>
     * </p>
     * <ul>
     * <li>Root entity must exist in {@link EntityProcessor#getRegistry()}.</li>
     * <li>Each path segment must exist in the current entity's metadata.</li>
     * <li>Intermediate segments must reference non-scalar types (entities or
     * embeddables).</li>
     * <li>Final segment can be any valid field type.</li>
     * </ul>
     *
     * <h3>Examples</h3>
     * 
     * <pre>{@code
     * // Valid paths → returns null
     * String error = validateEntityFieldPath("com.example.User", "address.city", null);
     * assertNull(error);
     *
     * validateEntityFieldPath("com.example.User", "profile.details.name", fqcnConsumer);
     *
     * // Invalid paths → returns error message
     * assertNotNull(validateEntityFieldPath("com.example.User", "name.surname", null)); // name is scalar
     * assertNotNull(validateEntityFieldPath("com.example.User", "address.unknown", null)); // unknown field
     * assertNotNull(validateEntityFieldPath("UnknownEntity", "field", null)); // entity not found
     * }</pre>
     *
     * <h3>Typical usage patterns</h3>
     * 
     * <pre>{@code
     * // 1. Simple validation
     * String error = validateEntityFieldPath("com.example.User", "address.city", null);
     * if (error != null) {
     *     throw new IllegalArgumentException(error);
     * }
     *
     * // 2. Validation + type resolution for dynamic type conversion
     * validateEntityFieldPath("com.example.User", "profile.details.value", typeFqcn -> {
     *     Class<?> targetType = loadClass(typeFqcn);
     *     configureTypeConverter(targetType);
     * });
     * }</pre>
     *
     * @param entityClassName the fully qualified name of the root entity (must
     *                        exist in registry)
     * @param fieldPath       the dotted path to validate (e.g.
     *                        {@code "address.city.name"})
     * @param withFqcn        optional consumer to receive the FQCN of the final
     *                        field's type,
     *                        called only if validation succeeds; can be
     *                        {@code null}
     * @return {@code null} if the path is valid, or an error message describing the
     *         validation failure
     *
     * @see #getSimpleName(String) for the simple class name used in error messages
     */
    public String validateEntityFieldPath(String entityClassName, String fieldPath, Consumer<String> withFqcn) {
        // Get entity metadata from EntityRegistryProcessor
        Map<String, Map<String, EntityProcessor.SimplePersistenceMetadata>> entityRegistry = entityProcessor
                .getRegistry();
        Map<String, Map<String, EntityProcessor.SimplePersistenceMetadata>> embeddableRegistry = entityProcessor
                .getEmbeddableRegistry();
        Map<String, EntityProcessor.SimplePersistenceMetadata> entityMetadata = entityRegistry.get(entityClassName);

        if (entityMetadata == null) {
            return "Entity " + entityClassName + " not found in registry";
        }

        // Handle nested paths (e.g., "address.city")
        String[] segments = fieldPath.split("\\.");
        String currentClassName = entityClassName;

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];

            // Get metadata for current class
            Map<String, EntityProcessor.SimplePersistenceMetadata> currentMetadata = entityRegistry
                    .get(currentClassName);
            if (currentMetadata == null) {
                currentMetadata = embeddableRegistry.get(currentClassName);
            }

            if (currentMetadata == null) {
                return String.format("Field '%s' not found in %s (path: %s)", segment, currentClassName, fieldPath);
            }

            // Check if field exists in metadata
            EntityProcessor.SimplePersistenceMetadata fieldMetadata = currentMetadata.get(segment);
            if (fieldMetadata == null) {
                return String.format("Field '%s' not found in entity %s (path: %s)", segment,
                        getSimpleName(currentClassName), fieldPath);
            }

            // If not the last segment, navigate to related type
            if (i < segments.length - 1) {
                if (BASIC_JPA_TYPES.contains(fieldMetadata.relatedType())) {
                    return String.format("Cannot navigate through scalar field '%s' in %s", segment,
                            getSimpleName(currentClassName));
                }

                currentClassName = fieldMetadata.relatedType();
            } else if (withFqcn != null) {
                // For collections, return the full parameterized type (e.g.,
                // java.util.List<User>)
                if (fieldMetadata.isCollection() && fieldMetadata.collection().isPresent()) {
                    String fullType = buildCollectionTypeName(
                            fieldMetadata.collection().get().collectionType(),
                            fieldMetadata.relatedType());
                    withFqcn.accept(fullType);
                } else {
                    withFqcn.accept(fieldMetadata.relatedType());
                }
            }
        }

        return null;
    }

    /**
     * Finds all dependencies that traverse a collection in their path.
     * <p>
     * A dependency like {@code "orders.total"} is a collection dependency if
     * {@code orders} is a {@code @OneToMany} or {@code @ManyToMany} relationship.
     * A dependency like {@code "address.city"} is NOT a collection if
     * {@code address}
     * is an {@code @Embedded} or {@code @ManyToOne}.
     * </p>
     *
     * @param entityClassName the root entity class name
     * @param dependencies    the list of dependency paths to check
     * @return list of dependency paths that traverse at least one collection
     */
    private List<String> findCollectionDependencies(String entityClassName, List<String> dependencies) {
        List<String> collectionDeps = new ArrayList<>();
        for (String dependency : dependencies) {
            if (isCollectionPath(entityClassName, dependency)) {
                collectionDeps.add(dependency);
            }
        }
        return collectionDeps;
    }

    /**
     * Checks if a path traverses a collection at any segment.
     *
     * @param entityClassName the root entity class name
     * @param path            the dependency path (e.g., "orders.total")
     * @return true if any intermediate segment is a collection
     */
    private boolean isCollectionPath(String entityClassName, String path) {
        if (!path.contains(".")) {
            return false;
        }

        Map<String, Map<String, EntityProcessor.SimplePersistenceMetadata>> entityRegistry = entityProcessor
                .getRegistry();
        Map<String, Map<String, EntityProcessor.SimplePersistenceMetadata>> embeddableRegistry = entityProcessor
                .getEmbeddableRegistry();

        String[] segments = path.split("\\.");
        String currentClassName = entityClassName;

        // Check all segments except the last one
        for (int i = 0; i < segments.length - 1; i++) {
            Map<String, EntityProcessor.SimplePersistenceMetadata> currentMetadata = entityRegistry
                    .get(currentClassName);
            if (currentMetadata == null) {
                currentMetadata = embeddableRegistry.get(currentClassName);
            }
            if (currentMetadata == null) {
                return false;
            }

            EntityProcessor.SimplePersistenceMetadata fieldMetadata = currentMetadata.get(segments[i]);
            if (fieldMetadata == null) {
                return false;
            }

            if (fieldMetadata.isCollection()) {
                return true; // Found a collection in the path
            }

            currentClassName = fieldMetadata.relatedType();
        }
        return false;
    }

    /**
     * Writes the body of the generated
     * {@code ProjectionRegistryProviderImpl} class
     * to the given writer.
     * <p>
     * The generated code initializes a static registry of
     * {@code ProjectionMetadata} entries,
     * one per processed DTO, and implements the provider interface by returning an
     * unmodifiable
     * view of that registry.
     * </p>
     *
     * @param writer the writer used to output Java source code
     * @throws IOException if an error occurs while writing to the underlying stream
     */
    private void writeProjectionRegistry(Writer writer) throws IOException {
        writer.write("package io.github.cyfko.jpametamodel.providers.impl;\n\n");
        writer.write("import io.github.cyfko.jpametamodel.providers.ProjectionRegistryProvider;\n");
        writer.write("import io.github.cyfko.jpametamodel.api.*;\n");
        writer.write("import java.util.Map;\n");
        writer.write("import java.util.HashMap;\n");
        writer.write("import java.util.List;\n");
        writer.write("import java.util.Collections;\n");
        writer.write("import java.util.Optional;\n\n");

        writer.write("/**\n");
        writer.write(" * Generated projection metadata provider implementation.\n");
        writer.write(" * DO NOT EDIT - This file is automatically generated.\n");
        writer.write(" */\n");
        writer.write(
                "public class ProjectionRegistryProviderImpl implements ProjectionRegistryProvider {\n\n");

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
     * Writes a single projection entry into the generated registry initialization
     * block.
     *
     * @param writer the writer used to output Java source code
     * @param entry  the metadata entry keyed by the DTO fully qualified name
     * @throws IOException if an error occurs while writing to the underlying stream
     */
    private void writeProjectionEntry(Writer writer, Map.Entry<String, SimpleProjectionMetadata> entry)
            throws IOException {
        String dtoType = entry.getKey();
        SimpleProjectionMetadata metadata = entry.getValue();

        StringBuilder sb = new StringBuilder();
        sb.append("        // ").append(dtoType).append(" → ").append(metadata.entityClass()).append("\n");
        sb.append("        registry.put(\n");
        sb.append("            ").append(dtoType).append(".class,\n");
        sb.append("            new ProjectionMetadata(\n");
        sb.append("                ").append(metadata.entityClass()).append(".class,\n");

        // Direct mappings
        sb.append("                new DirectMapping[]{");
        if (!metadata.directMappings().isEmpty()) {
            sb.append("\n");
            for (int i = 0; i < metadata.directMappings().size(); i++) {
                sb.append(formatDirectMapping(metadata.directMappings().get(i)));
                sb.append(i < metadata.directMappings().size() - 1 ? ",\n" : "\n");
            }
            sb.append("                ");
        }
        sb.append("},\n");

        // Computed fields
        sb.append("                new ComputedField[]{");
        if (!metadata.computedFields().isEmpty()) {
            sb.append("\n");
            for (int i = 0; i < metadata.computedFields().size(); i++) {
                sb.append(formatComputedField(metadata.computedFields().get(i)));
                sb.append(i < metadata.computedFields().size() - 1 ? ",\n" : "\n");
            }
            sb.append("                ");
        }
        sb.append("},\n");

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
     * Formats a {@link SimpleDirectMapping} instance as a Java code snippet that
     * constructs
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
                m.dtoField(), m.entityField(), m.dtoFieldType(), collection);
    }

    /**
     * Formats a {@link ComputedField} instance as a Java expression suitable for
     * inclusion
     * in the generated registry source code.
     *
     * @param f the computed field descriptor
     * @return a Java expression string constructing a {@code ComputedField}
     */
    private String formatComputedField(SimpleComputedField f) {
        String deps = Arrays.stream(f.dependencies())
                .map(d -> "\"" + d + "\"")
                .collect(Collectors.joining(", "));

        // Build ReducerMapping array: new ComputedField.ReducerMapping(index,
        // "REDUCER")
        StringBuilder reducerMappings = new StringBuilder();
        for (int i = 0; i < f.reducerIndices().length; i++) {
            if (i > 0)
                reducerMappings.append(", ");
            reducerMappings.append(String.format("new ComputedField.ReducerMapping(%d, \"%s\")",
                    f.reducerIndices()[i], f.reducerNames()[i]));
        }

        if (f.methodClass() == null && f.methodName() == null) {
            return String.format(
                    "                    new ComputedField(\"%s\", new String[]{%s}, new ComputedField.ReducerMapping[]{%s})",
                    f.dtoField(),
                    deps,
                    reducerMappings);
        }

        String classArg = f.methodClass() != null ? f.methodClass() + ".class" : "null";
        String methodArg = f.methodName() != null && !f.methodName().isBlank() ? "\"" + f.methodName() + "\"" : "null";

        return String.format(
                "                    new ComputedField(\"%s\", new String[]{%s}, new ComputedField.ReducerMapping[]{%s}, %s, %s)",
                f.dtoField(),
                deps,
                reducerMappings,
                classArg,
                methodArg);
    }

    /**
     * Formats a {@link SimpleComputationProvider} instance as a Java expression
     * constructing
     * a {@code ComputationProvider} in the generated registry.
     *
     * @param c the computation provider metadata
     * @return a Java expression string constructing a {@code ComputationProvider}
     */
    private String formatComputerProvider(SimpleComputationProvider c) {
        return String.format(
                "                    new ComputationProvider(%s.class, \"%s\")",
                c.className(), c.bean());
    }

    /**
     * Returns the simple class name extracted from a fully qualified class name.
     *
     * @param fqcn the fully qualified class name
     * @return the simple name (segment after the last dot) or the original string
     *         if no dot is present
     */
    private String getSimpleName(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        return lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn;
    }

    /**
     * Builds the fully qualified parameterized collection type name from the
     * collection type enum and element type.
     * <p>
     * For example, given {@code CollectionType.LIST} and {@code "io.example.User"},
     * returns {@code "java.util.List<io.example.User>"}.
     * </p>
     *
     * @param collectionType the type of collection (LIST, SET, MAP, COLLECTION)
     * @param elementType    the fully qualified element type name
     * @return the full parameterized type string
     */
    private String buildCollectionTypeName(CollectionType collectionType, String elementType) {
        String containerClass = switch (collectionType) {
            case LIST -> "java.util.List";
            case SET -> "java.util.Set";
            case MAP -> "java.util.Map";
            case COLLECTION -> "java.util.Collection";
            case UNKNOWN -> "java.util.Collection";
        };
        return containerClass + "<" + elementType + ">";
    }

    public void setReferencedProjection(Set<TypeElement> projectionDtos) {
        this.referencedProjections.addAll(projectionDtos);
    }

    /**
     * Lightweight value object describing a direct mapping between a DTO field and
     * an entity field, including optional collection metadata.
     *
     * @param dtoField     the DTO field name
     * @param entityField  the entity field path
     * @param dtoFieldType the DTO field type as a fully qualified name
     * @param collection   optional collection metadata if the field represents a
     *                     collection
     */
    public record SimpleDirectMapping(String dtoField,
            String entityField,
            String dtoFieldType,
            Optional<DirectMapping.CollectionMetadata> collection) {
    }

    /**
     * Lightweight value object describing a computed field view on annotation
     * processor.
     *
     * @param dtoField       the DTO field name
     * @param dependencies   the dependency paths
     * @param reducerIndices the indices of dependencies that have reducers
     * @param reducerNames   the reducer names corresponding to each index
     * @param methodClass    the method class, if specified
     * @param methodName     the method name, if specified
     */
    record SimpleComputedField(String dtoField, String[] dependencies, int[] reducerIndices, String[] reducerNames,
            String methodClass, String methodName) {
    }

    /**
     * Aggregated projection metadata used internally by the processor before being
     * written
     * to generated source code.
     *
     * @param entityClass    the fully qualified name of the projected JPA entity
     * @param directMappings the list of direct property mappings
     * @param computedFields the list of computed fields with their dependencies
     * @param computers      the computation provider descriptors used for
     *                       evaluating computed fields
     */
    public record SimpleProjectionMetadata(String entityClass,
            List<SimpleDirectMapping> directMappings,
            List<SimpleComputedField> computedFields,
            SimpleComputationProvider[] computers) {
    }

    /**
     * Describes a computation provider class and the bean name used to obtain its
     * instance.
     *
     * @param className the fully qualified provider class name
     * @param bean      the bean identifier used to resolve the provider in the
     *                  runtime container
     */
    public record SimpleComputationProvider(String className, String bean) {
    }
}