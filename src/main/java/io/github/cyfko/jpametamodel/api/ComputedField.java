package io.github.cyfko.jpametamodel.api;

import java.util.Arrays;
import java.util.Objects;

/**
 * Metadata describing a computed DTO field and its dependencies.
 * <p>
 * A computed field represents a DTO property whose value is derived from one or
 * more
 * entity or projection fields rather than mapped directly. It is typically
 * declared
 * via a dedicated annotation (e.g. {@code @ComputedField}) and may optionally
 * be
 * associated with a method reference that performs the computation.
 * </p>
 *
 * <p>
 * <b>Invariants:</b>
 * </p>
 * <ul>
 * <li>{@code dtoField} must be non-null and non-blank.</li>
 * <li>{@code dependencies} must be non-null and contain at least one
 * element.</li>
 * <li>{@code reducers} must be non-null (can be empty for non-collection
 * dependencies).</li>
 * <li>{@code methodReference} is optional and may be {@code null} when
 * computation
 * is resolved by convention or an external resolver.</li>
 * </ul>
 *
 * <p>
 * <b>Reducers:</b>
 * </p>
 * <p>
 * When a dependency traverses a collection (e.g., {@code "orders.total"}), a
 * reducer
 * must be provided to specify how to aggregate the values. Common reducers
 * include
 * {@code "SUM"}, {@code "AVG"}, {@code "COUNT"}, {@code "MIN"}, {@code "MAX"}.
 * </p>
 *
 * <p>
 * <b>Typical usage:</b>
 * </p>
 * 
 * <pre>{@code
 * // Single scalar dependency, no reducer needed
 * ComputedField fullName = new ComputedField(
 *         "fullName",
 *         new String[] { "firstName", "lastName" });
 *
 * // Collection dependency with reducer
 * ComputedField totalAmount = new ComputedField(
 *         "totalAmount",
 *         new String[] { "orders.amount" },
 *         new String[] { "SUM" });
 * }</pre>
 *
 * @param dtoField        name of the DTO property exposed to clients (must not
 *                        be null or blank)
 * @param dependencies    non-empty array of entity/projection field paths
 *                        required to compute the value
 * @param reducers        array of reducer mappings linking reducers to their
 *                        target dependency indices
 * @param methodReference optional method reference metadata describing how the
 *                        value is computed;
 *                        may be {@code null} if the computation is resolved
 *                        elsewhere
 */
public record ComputedField(String dtoField, String[] dependencies, ReducerMapping[] reducers,
        MethodReference methodReference) {

    public ComputedField {
        Objects.requireNonNull(dtoField, "dtoField cannot be null");
        Objects.requireNonNull(dependencies, "dependencies cannot be null");
        Objects.requireNonNull(reducers, "reducers cannot be null");
        // methodMeta peut être null (optionnel)
        if (dtoField.isBlank()) {
            throw new IllegalArgumentException("dtoField cannot be blank");
        }
        if (dependencies.length == 0) {
            throw new IllegalArgumentException("dependencies cannot be empty");
        }
    }

    /**
     * Creates a computed field with required dependencies and no explicit method
     * reference.
     * <p>
     * This constructor is suitable when computation is handled by:
     * </p>
     * <ul>
     * <li>a default resolver,</li>
     * <li>naming conventions, or</li>
     * <li>a framework-level <strong>instance resolver</strong>.</li>
     * </ul>
     *
     * @param dtoField     name of the DTO property (must not be null or blank)
     * @param dependencies non-empty array of dependency paths
     */
    public ComputedField(String dtoField, String[] dependencies) {
        this(dtoField, dependencies, new ReducerMapping[0], null);
    }

    /**
     * Creates a computed field whose value is computed by a method declared on the
     * given class.
     * <p>
     * The method name may be resolved by convention (e.g. based on
     * {@code dtoField}) by higher-level
     * components if not explicitly specified.
     * </p>
     *
     * @param dtoField             name of the DTO property (must not be null or
     *                             blank)
     * @param dependencies         non-empty array of dependency paths
     * @param methodReferenceClass target class declaring the compute method (must
     *                             not be null)
     */
    public ComputedField(String dtoField, String[] dependencies, Class<?> methodReferenceClass) {
        this(dtoField, dependencies, new ReducerMapping[0], new MethodReference(methodReferenceClass, null));
    }

    /**
     * Creates a computed field whose value is computed by a method with the given
     * name.
     * <p>
     * The target class may be resolved externally (for example, a default resolver
     * or the DTO type).
     * </p>
     *
     * @param dtoField          name of the DTO property (must not be null or blank)
     * @param dependencies      non-empty array of dependency paths
     * @param computeMethodName name of the compute method (must not be null)
     */
    public ComputedField(String dtoField, String[] dependencies, String computeMethodName) {
        this(dtoField, dependencies, new ReducerMapping[0], new MethodReference(null, computeMethodName));
    }

    /**
     * Creates a computed field whose value is computed by a specific method on a
     * specific class.
     *
     * @param dtoField             name of the DTO property (must not be null or
     *                             blank)
     * @param dependencies         non-empty array of dependency paths
     * @param methodReferenceClass target class declaring the compute method (may be
     *                             {@code null}
     *                             if resolved elsewhere)
     * @param computeMethodName    name of the compute method (may be {@code null}
     *                             if resolved by convention)
     */
    public ComputedField(String dtoField, String[] dependencies, Class<?> methodReferenceClass,
            String computeMethodName) {
        this(dtoField, dependencies, new ReducerMapping[0],
                new MethodReference(methodReferenceClass, computeMethodName));
    }

    /**
     * Creates a computed field with reducer mappings for collection dependencies.
     *
     * @param dtoField     name of the DTO property (must not be null or blank)
     * @param dependencies non-empty array of dependency paths
     * @param reducers     array of reducer mappings
     */
    public ComputedField(String dtoField, String[] dependencies, ReducerMapping[] reducers) {
        this(dtoField, dependencies, reducers, null);
    }

    /**
     * Creates a computed field with reducer mappings and explicit method reference.
     * <p>
     * This constructor is typically used by generated code.
     * </p>
     *
     * @param dtoField             name of the DTO property (must not be null or
     *                             blank)
     * @param dependencies         non-empty array of dependency paths
     * @param reducers             array of reducer mappings
     * @param methodReferenceClass target class declaring the compute method
     * @param computeMethodName    name of the compute method
     */
    public ComputedField(String dtoField, String[] dependencies, ReducerMapping[] reducers,
            Class<?> methodReferenceClass, String computeMethodName) {
        this(dtoField, dependencies, reducers,
                (methodReferenceClass != null || computeMethodName != null)
                        ? new MethodReference(methodReferenceClass, computeMethodName)
                        : null);
    }

    /**
     * Checks if this computed field has any reducers defined.
     *
     * @return {@code true} if at least one reducer is specified
     */
    public boolean hasReducers() {
        return reducers != null && reducers.length > 0;
    }

    /**
     * Metadata describing the Java method used to compute a {@link ComputedField}.
     * <p>
     * Both components are optional individually, but at least one of
     * {@code targetClass}
     * or {@code methodName} must be non-null. This allows:
     * </p>
     * <ul>
     * <li>a known class with convention-based method resolution,</li>
     * <li>a known method name on a default resolver, or</li>
     * <li>a fully specified {@code (class, method)} pair.</li>
     * </ul>
     *
     * @param targetClass target class holding the compute method, or {@code null}
     *                    if resolved elsewhere
     * @param methodName  name of the compute method, or {@code null} if resolved by
     *                    convention
     */
    public record MethodReference(
            Class<?> targetClass, // target class, or null
            String methodName // method name, or null
    ) {
        public MethodReference {
            if (targetClass == null && methodName == null) {
                throw new IllegalArgumentException("targetClass and methodName cannot be null at the same time");
            }
        }
    }

    /**
     * Maps a reducer to its target dependency index.
     * <p>
     * Each reducer applies to a specific collection dependency identified by its
     * index in the {@link #dependencies()} array.
     * </p>
     *
     * @param dependencyIndex index in the dependencies array (0-based)
     * @param reducer         the reducer name (e.g., "SUM", "AVG", "COUNT")
     */
    public record ReducerMapping(int dependencyIndex, String reducer) {
        public ReducerMapping {
            if (dependencyIndex < 0) {
                throw new IllegalArgumentException("dependencyIndex must be >= 0");
            }
            Objects.requireNonNull(reducer, "reducer cannot be null");
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