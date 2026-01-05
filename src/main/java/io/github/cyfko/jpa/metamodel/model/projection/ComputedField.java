package io.github.cyfko.jpa.metamodel.model.projection;

import java.util.Arrays;
import java.util.Objects;


/**
 * Metadata describing a method reference for a computed field (@MethodReference).
 * Both fields may be null if not specified.
 */

public record ComputedField(String dtoField, String[] dependencies, MethodReference methodReference) {

    public ComputedField {
        Objects.requireNonNull(dtoField, "dtoField cannot be null");
        Objects.requireNonNull(dependencies, "dependencies cannot be null");
        // methodMeta peut être null (optionnel)
        if (dtoField.isBlank()) {
            throw new IllegalArgumentException("dtoField cannot be blank");
        }
        if (dependencies.length == 0) {
            throw new IllegalArgumentException("dependencies cannot be empty");
        }
    }

    public ComputedField(String dtoField, String[] dependencies) {
        this(dtoField, dependencies, (MethodReference) null);
    }

    public ComputedField(String dtoField, String[] dependencies, Class<?> methodReferenceClass) {
        this(dtoField, dependencies, new MethodReference(methodReferenceClass, null));
    }

    public ComputedField(String dtoField, String[] dependencies, String computeMethodName) {
        this(dtoField, dependencies, new MethodReference(null, computeMethodName));
    }

    public ComputedField(String dtoField, String[] dependencies, Class<?> methodReferenceClass, String computeMethodName) {
        this(dtoField, dependencies, new MethodReference(methodReferenceClass, computeMethodName));
    }

    /**
     * Metadata describing a method reference for a computed field (@MethodReference).
     * Both fields may be null if not specified.
     */
    public record MethodReference(
        Class<?> className, // target class, or null
        String methodName // method name, or null
    ) {
        public MethodReference {
            if (className == null && methodName == null) {
                throw new IllegalArgumentException("className and methodName cannot be null at the same time");
            }
        }
    }

    /**
     * Checks if this computed field depends on a specific entity field.
     *
     * @param entityField the entity field path
     * @return true if dependent, false otherwise
     */
    public boolean dependsOn(String entityField) {
        return Arrays.asList(dependencies).contains(entityField);
    }

    /**
     * Gets the number of dependencies.
     *
     * @return dependency count
     */
    public int dependencyCount() {
        return dependencies.length;
    }

    /**
     * Checks if any dependency is a nested path.
     *
     * @return true if any dependency contains a dot
     */
    public boolean hasNestedDependencies() {
        return Arrays.stream(dependencies).anyMatch(d -> d.contains("."));
    }
}