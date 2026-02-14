package io.github.cyfko.jpametamodel.processor;

import io.github.cyfko.jpametamodel.api.CollectionKind;
import io.github.cyfko.jpametamodel.api.CollectionType;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor14;
import java.util.*;
import java.util.function.Consumer;

/**
 * Utility class containing common helpers for the annotation processing layer.
 * <p>
 * This class centralizes logic for:
 * </p>
 * <ul>
 * <li>Determining collection kind and collection type for JPA-related
 * types.</li>
 * <li>Inspecting elements for specific annotations by fully qualified
 * name.</li>
 * <li>Extracting strongly-typed values from annotation mirrors using visitor
 * APIs.</li>
 * <li>Performing generic tasks such as capitalization of identifiers.</li>
 * </ul>
 *
 * @author Frank KOSSI
 * @since 1.0.0
 */
public class AnnotationProcessorUtils {

    /**
     * Set of basic JPA types considered scalar (not entities or embeddables).
     */
    public static final Set<String> BASIC_JPA_TYPES = Set.of(
            "java.lang.String",
            "java.lang.Boolean", "java.lang.Integer", "java.lang.Long",
            "java.lang.Short", "java.lang.Byte", "java.lang.Character",
            "java.lang.Float", "java.lang.Double",
            "java.time.LocalDate", "java.time.LocalTime", "java.time.LocalDateTime",
            "java.time.OffsetTime", "java.time.OffsetDateTime",
            "java.time.Instant", "java.time.ZonedDateTime",
            "java.util.Date", "java.sql.Date", "java.sql.Time", "java.sql.Timestamp",
            "java.util.Calendar",
            "java.math.BigDecimal", "java.math.BigInteger",
            "java.util.UUID",
            "byte[]", "java.lang.Byte[]",
            "char[]", "java.lang.Character[]",
            "byte", "short", "int", "long", "float", "double", "boolean", "char");

    /**
     * Returns the fully qualified type name without type-use annotations.
     * <p>
     * When using {@code TypeMirror.toString()}, type-use annotations such as
     * {@code @NotNull} may be included in the output, producing invalid code like:
     * {@code com.example.@NotNull MyClass.class}. This method extracts only the
     * canonical type name.
     * </p>
     *
     * @param type the type mirror to extract the name from
     * @return the fully qualified type name without annotations
     */
    public static String getTypeNameWithoutAnnotations(TypeMirror type) {
        if (type instanceof DeclaredType dt) {
            Element element = dt.asElement();
            if (element instanceof TypeElement te) {
                return te.getQualifiedName().toString();
            }
        }
        // Fallback: strip annotations using regex for primitives and other types
        return type.toString().replaceAll("@\\S+\\s+", "");
    }

    /**
     * Determines the kind of collection for the given element type.
     * <p>
     * Returns:
     * <ul>
     * <li>{@link CollectionKind#SCALAR} if the element type is a basic JPA type or
     * an enum.</li>
     * <li>{@link CollectionKind#ENTITY} if the element type is annotated
     * with @Entity.</li>
     * <li>{@link CollectionKind#EMBEDDABLE} if the element type is annotated
     * with @Embeddable.</li>
     * <li>{@link CollectionKind#UNKNOWN} otherwise.</li>
     * </ul>
     * </p>
     *
     * @param elementType the fully qualified name of the element type * @param
     *                    processingEnv the processing environment
     * @return the collection kind
     */
    public static CollectionKind determineCollectionKind(String elementType, ProcessingEnvironment processingEnv) {
        if (elementType == null) {
            return CollectionKind.UNKNOWN;
        }

        if (BASIC_JPA_TYPES.contains(elementType)) {
            return CollectionKind.SCALAR;
        }

        TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(elementType);
        if (typeElement != null) {
            if (typeElement.getKind() == ElementKind.ENUM) {
                return CollectionKind.SCALAR;
            }

            if (hasAnnotation(typeElement, "jakarta.persistence.Entity")) {
                return CollectionKind.ENTITY;
            }

            if (hasAnnotation(typeElement, "jakarta.persistence.Embeddable")) {
                return CollectionKind.EMBEDDABLE;
            }
        }

        return CollectionKind.UNKNOWN;
    }

    /**
     * Checks if the given element has an annotation with the specified fully
     * qualified name.
     *
     * @param element          the element to check
     * @param fqAnnotationName the fully qualified name of the annotation
     * @return true if the element has the annotation, false otherwise
     */
    public static boolean hasAnnotation(Element element, String fqAnnotationName) {
        return element.getAnnotationMirrors().stream()
                .anyMatch(am -> {
                    Element annotationElement = am.getAnnotationType().asElement();
                    return annotationElement instanceof TypeElement
                            && ((TypeElement) annotationElement).getQualifiedName().toString().equals(fqAnnotationName);
                });
    }

    /**
     * Determines the collection type for the given type mirror.
     * <p>
     * Returns:
     * <ul>
     * <li>{@link CollectionType#LIST} for java.util.List</li>
     * <li>{@link CollectionType#SET} for java.util.Set</li>
     * <li>{@link CollectionType#MAP} for java.util.Map</li>
     * <li>{@link CollectionType#COLLECTION} for java.util.Collection</li>
     * <li>{@link CollectionType#UNKNOWN} otherwise</li>
     * </ul>
     * </p>
     *
     * @param type the type mirror to check * @return the collection type
     */
    public static CollectionType determineCollectionType(TypeMirror type) {
        if (!(type instanceof DeclaredType dt)) {
            return CollectionType.UNKNOWN;
        }

        String typeName = dt.asElement().toString();

        return switch (typeName) {
            case "java.util.List" -> CollectionType.LIST;
            case "java.util.Set" -> CollectionType.SET;
            case "java.util.Map" -> CollectionType.MAP;
            case "java.util.Collection" -> CollectionType.COLLECTION;
            default -> CollectionType.UNKNOWN;
        };
    }

    /**
     * Processes the explicit annotation fields for a given element and annotation
     * type.
     * <p>
     * This method searches for an annotation with the provided fully qualified name
     * on the
     * given element. If found, it extracts all attribute values (including nested
     * annotations,
     * arrays, enums and types) into a {@link Map} and passes it to the
     * {@code ifPresent} consumer.
     * If the annotation is not present, the optional {@code orElse} runnable is
     * executed.
     * </p>
     *
     * <h3>Usage example</h3>
     * 
     * <pre>{@code
     * ProcessorUtils.processExplicitFields(
     *         element,
     *         "io.github.cyfko.filterql.jpa.metamodel.Projected",
     *         fields -> {
     *             String from = (String) fields.get("from");
     *             // handle explicit mapping
     *         },
     *         () -> {
     *             // annotation not found on element
     *         });
     * }</pre>
     *
     * @param element          the annotated element to inspect
     * @param fqAnnotationName the fully qualified annotation name to look for
     * @param ifPresent        consumer invoked with a map of annotation attribute
     *                         names to values when present
     * @param orElse           runnable executed when the annotation is absent; may
     *                         be {@code null}
     */
    public static void processExplicitFields(Element element,
            String fqAnnotationName,
            Consumer<Map<String, Object>> ifPresent,
            Runnable orElse) {
        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            Name qualifiedName = ((TypeElement) am.getAnnotationType().asElement()).getQualifiedName();
            if (!qualifiedName.contentEquals(fqAnnotationName))
                continue;

            Map<String, Object> fields = new HashMap<>();

            Map<? extends ExecutableElement, ? extends AnnotationValue> values = am.getElementValues();

            // Si c’est une liste d’AnnotationValue (cas des tableaux)
            for (ExecutableElement key : values.keySet()) {
                fields.put(key.getSimpleName().toString(), extractValue(values.get(key)));
            }

            ifPresent.accept(fields);
            return;
        }

        if (orElse != null)
            orElse.run();
    }

    /**
     * Recursively extracts a strongly typed value from an {@link AnnotationValue}
     * using a {@link SimpleAnnotationValueVisitor14}.
     * <p>
     * The visitor handles:
     * </p>
     * <ul>
     * <li>Nested annotations (as {@link Map} structures).</li>
     * <li>Arrays of annotation values (as {@link java.util.List}).</li>
     * <li>Class literals (represented by their fully qualified name).</li>
     * <li>Enum constants (represented by their simple name).</li>
     * <li>Strings and other primitive-compatible values.</li>
     * </ul>
     *
     * @param av the annotation value to extract
     * @return a Java representation of the annotation value suitable for further
     *         processing
     */
    private static Object extractValue(AnnotationValue av) {
        return av.accept(new SimpleAnnotationValueVisitor14<Object, Void>() {
            @Override
            public Object visitAnnotation(AnnotationMirror a, Void unused) {
                Map<String, Object> nested = new HashMap<>();
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : a.getElementValues()
                        .entrySet()) {
                    nested.put(entry.getKey().getSimpleName().toString(), extractValue(entry.getValue()));
                }
                return nested;
            }

            @Override
            public Object visitArray(List<? extends AnnotationValue> vals, Void unused) {
                List<Object> list = new ArrayList<>();
                for (AnnotationValue val : vals) {
                    list.add(extractValue(val)); // récursivité ici
                }
                return list;
            }

            @Override
            public Object visitType(TypeMirror t, Void unused) {
                return t.toString(); // pour les Class<?>
            }

            @Override
            public Object visitEnumConstant(VariableElement c, Void unused) {
                return c.getSimpleName().toString();
            }

            @Override
            public Object visitString(String s, Void unused) {
                return s;
            }

            @Override
            protected Object defaultAction(Object o, Void unused) {
                return o;
            }
        }, null);
    }

    /**
     * Finds a method in a type element matching the given signature.
     * <p>
     * This method performs compile-time verification without using reflection,
     * making it suitable for annotation processors. It checks:
     * </p>
     * <ul>
     *   <li>Method name match</li>
     *   <li>Method modifiers (static, public, etc.)</li>
     *   <li>Return type compatibility</li>
     *   <li>Parameter types and count</li>
     * </ul>
     * <p>
     * The search scope depends on the {@code includeInherited} parameter:
     * </p>
     * <ul>
     *   <li>If {@code false}: searches only in the specified class itself</li>
     *   <li>If {@code true}: searches in the class, all superclasses, and all interfaces</li>
     * </ul>
     *
     * <h3>Usage Examples</h3>
     *
     * <h4>Search Only in Declared Methods</h4>
     * <pre>{@code
     * // Only find methods declared directly in the class
     * Optional<ExecutableElement> method = findMethodWithSignature(
     *     targetClass,
     *     "processData",
     *     Set.of(Modifier.PUBLIC, Modifier.STATIC),
     *     stringType,
     *     List.of(stringType, intType),
     *     false,  // Don't include inherited methods
     *     processingEnv
     * );
     * }</pre>
     *
     * <h4>Search Including Inherited Methods</h4>
     * <pre>{@code
     * // Find methods from class, superclasses, and interfaces
     * Optional<ExecutableElement> method = findMethodWithSignature(
     *     targetClass,
     *     "transform",
     *     Set.of(Modifier.PUBLIC),
     *     filterRequestType,
     *     List.of(filterRequestType),
     *     true,  // Include inherited methods
     *     processingEnv
     * );
     * }</pre>
     *
     * <h4>Validate Method is Directly Declared</h4>
     * <pre>{@code
     * // Ensure the method is explicitly declared, not inherited
     * Optional<ExecutableElement> method = findMethodWithSignature(
     *     myClass,
     *     "customHandler",
     *     Set.of(Modifier.PUBLIC, Modifier.STATIC),
     *     pageType,
     *     List.of(filterRequestType, pageableType),
     *     false,  // Must be declared in myClass itself
     *     processingEnv
     * );
     *
     * method.ifPresentOrElse(
     *     m -> System.out.println("Handler declared in class"),
     *     () -> System.out.println("Handler must be declared, not inherited")
     * );
     * }</pre>
     *
     * @param typeElement the type element to search in
     * @param methodName the name of the method to find
     * @param requiredModifiers set of modifiers the method must have; null to skip check
     * @param expectedReturnType the expected return type; null to skip check
     * @param parameterTypes list of expected parameter types in order; null to skip check
     * @param includeInherited whether to search in superclasses and interfaces
     * @param processingEnv the processing environment
     * @return an Optional containing the matching ExecutableElement, or empty if not found
     */
    public static Optional<ExecutableElement> findMethodWithSignature(
            TypeElement typeElement,
            String methodName,
            Set<Modifier> requiredModifiers,
            TypeMirror expectedReturnType,
            List<TypeMirror> parameterTypes,
            boolean includeInherited,
            ProcessingEnvironment processingEnv) {

        if (typeElement == null || methodName == null || methodName.isEmpty()) {
            return Optional.empty();
        }

        // If not including inherited, just search in the current class
        if (!includeInherited) {
            return searchInElement(
                    typeElement,
                    methodName,
                    requiredModifiers,
                    expectedReturnType,
                    parameterTypes,
                    processingEnv,
                    false  // Don't skip private methods in the current class
            );
        }

        // Track visited types to avoid infinite loops in interface hierarchies
        Set<String> visitedTypes = new HashSet<>();

        // 1. Search in current class and all superclasses
        TypeElement currentClass = typeElement;
        while (currentClass != null) {
            String qualifiedName = currentClass.getQualifiedName().toString();

            // Stop at Object
            if (qualifiedName.equals("java.lang.Object")) {
                break;
            }

            visitedTypes.add(qualifiedName);

            // Search in current class
            Optional<ExecutableElement> found = searchInElement(
                    currentClass,
                    methodName,
                    requiredModifiers,
                    expectedReturnType,
                    parameterTypes,
                    processingEnv,
                    !currentClass.equals(typeElement) // skipPrivate if not the original class
            );

            if (found.isPresent()) {
                return found;
            }

            // Move to superclass
            TypeMirror superclass = currentClass.getSuperclass();
            if (superclass.getKind() == javax.lang.model.type.TypeKind.DECLARED) {
                Element superElement = ((DeclaredType) superclass).asElement();
                if (superElement instanceof TypeElement) {
                    currentClass = (TypeElement) superElement;
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        // 2. Search in all interfaces (including inherited ones)
        return searchInInterfaces(
                typeElement,
                methodName,
                requiredModifiers,
                expectedReturnType,
                parameterTypes,
                processingEnv,
                visitedTypes
        );
    }

    /**
     * Searches for a method in a single element (class or interface).
     *
     * @param element the element to search in
     * @param methodName the method name
     * @param requiredModifiers required modifiers
     * @param expectedReturnType expected return type
     * @param parameterTypes expected parameter types
     * @param processingEnv processing environment
     * @param skipPrivate whether to skip private methods
     * @return Optional containing the found method, or empty
     */
    private static Optional<ExecutableElement> searchInElement(
            TypeElement element,
            String methodName,
            Set<Modifier> requiredModifiers,
            TypeMirror expectedReturnType,
            List<TypeMirror> parameterTypes,
            ProcessingEnvironment processingEnv,
            boolean skipPrivate) {

        for (Element enclosedElement : element.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD) {
                continue;
            }

            ExecutableElement method = (ExecutableElement) enclosedElement;

            // Skip private methods if requested (from superclasses)
            if (skipPrivate && method.getModifiers().contains(Modifier.PRIVATE)) {
                continue;
            }

            // Check method name
            if (!method.getSimpleName().toString().equals(methodName)) {
                continue;
            }

            // Check modifiers
            if (requiredModifiers != null && !method.getModifiers().containsAll(requiredModifiers)) {
                continue;
            }

            // Check return type
            if (expectedReturnType != null) {
                TypeMirror actualReturnType = method.getReturnType();
                if (!processingEnv.getTypeUtils().isSameType(actualReturnType, expectedReturnType)) {
                    continue;
                }
            }

            // Check parameter count and types
            List<? extends VariableElement> parameters = method.getParameters();
            if (parameterTypes != null) {
                if (parameters.size() != parameterTypes.size()) {
                    continue;
                }

                boolean parametersMatch = true;
                for (int i = 0; i < parameterTypes.size(); i++) {
                    TypeMirror expectedParamType = parameterTypes.get(i);
                    TypeMirror actualParamType = parameters.get(i).asType();

                    if (!processingEnv.getTypeUtils().isSameType(actualParamType, expectedParamType)) {
                        parametersMatch = false;
                        break;
                    }
                }

                if (!parametersMatch) {
                    continue;
                }
            }

            return Optional.of(method);
        }

        return Optional.empty();
    }

    /**
     * Recursively searches for a method in all interfaces implemented by the type.
     *
     * @param typeElement the type element whose interfaces to search
     * @param methodName the method name
     * @param requiredModifiers required modifiers
     * @param expectedReturnType expected return type
     * @param parameterTypes expected parameter types
     * @param processingEnv processing environment
     * @param visitedTypes set of already visited type names to avoid cycles
     * @return Optional containing the found method, or empty
     */
    private static Optional<ExecutableElement> searchInInterfaces(
            TypeElement typeElement,
            String methodName,
            Set<Modifier> requiredModifiers,
            TypeMirror expectedReturnType,
            List<TypeMirror> parameterTypes,
            ProcessingEnvironment processingEnv,
            Set<String> visitedTypes) {

        // Get all directly implemented/extended interfaces
        List<? extends TypeMirror> interfaces = typeElement.getInterfaces();

        for (TypeMirror interfaceType : interfaces) {
            if (interfaceType.getKind() != javax.lang.model.type.TypeKind.DECLARED) {
                continue;
            }

            Element interfaceElement = ((DeclaredType) interfaceType).asElement();
            if (!(interfaceElement instanceof TypeElement interfaceTypeElement)) {
                continue;
            }

            String qualifiedName = interfaceTypeElement.getQualifiedName().toString();

            // Skip if already visited (avoid cycles)
            if (visitedTypes.contains(qualifiedName)) {
                continue;
            }

            visitedTypes.add(qualifiedName);

            // Search in this interface
            Optional<ExecutableElement> found = searchInElement(
                    interfaceTypeElement,
                    methodName,
                    requiredModifiers,
                    expectedReturnType,
                    parameterTypes,
                    processingEnv,
                    false  // Interfaces don't have private methods (before Java 9) or they're not inherited anyway
            );

            if (found.isPresent()) {
                return found;
            }

            // Recursively search in super-interfaces
            Optional<ExecutableElement> foundInSuper = searchInInterfaces(
                    interfaceTypeElement,
                    methodName,
                    requiredModifiers,
                    expectedReturnType,
                    parameterTypes,
                    processingEnv,
                    visitedTypes
            );

            if (foundInSuper.isPresent()) {
                return foundInSuper;
            }
        }

        return Optional.empty();
    }
}
