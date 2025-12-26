package io.github.cyfko.filterql.jpa.metamodel.utils;

import io.github.cyfko.filterql.jpa.metamodel.ProjectionRegistry;
import io.github.cyfko.filterql.jpa.metamodel.model.projection.ComputedField;
import io.github.cyfko.filterql.jpa.metamodel.model.projection.ProjectionMetadata;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Utility methods for working with projections and computed fields at runtime.
 * <p>
 * This class provides helper operations that complement the generated projection metadata,
 * such as delegating the execution of computed fields to their configured computation providers
 * and generic string utilities used across the projection layer.
 * </p>
 *
 * <p>
 * All methods are static and side effect free, making this class safe to use from any
 * projection execution context.
 * </p>
 *
 * @author  Frank KOSSI
 * @since   4.0.0
 */
public abstract class ProjectionUtils {

    private ProjectionUtils() {}

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
     * Computes the value of a projection field declared as <em>computed</em> using the registered
     * computation providers associated with the given projection class.
     * <p>
     * The resolution process is:
     * </p>
     * <ol>
     *   <li>Load {@link ProjectionMetadata} for the given projection class.</li>
     *   <li>Verify that the specified field is declared as a computed field.</li>
     *   <li>Derive the expected method name using the {@code get} + capitalized field convention.</li>
     *   <li>Iterate over all registered computation providers for this projection.</li>
     *   <li>For each provider, attempt to locate a compatible method via reflection and invoke it:
     *     <ul>
     *       <li>If {@code providerResolver} returns {@code null}, the method is invoked as a static method.</li>
     *       <li>Otherwise, the returned instance is used as the invocation target.</li>
     *     </ul>
     *   </li>
     *   <li>Return the first successfully computed result.</li>
     * </ol>
     *
     * <p>
     * If no metadata exists for the given projection class, or the field is not declared as computed,
     * or no matching method can be found on any provider, an exception is thrown to signal a
     * configuration or wiring error.
     * </p>
     *
     * @param providerResolver a resolver that, given a provider class and bean name, returns a
     *                         concrete provider instance or {@code null} to indicate static access
     * @param projectionClazz  the projection class declaring the computed field
     * @param field            the logical name of the computed field, without {@code get} prefix
     * @param dependencies     ordered dependency values that will be passed as arguments to the
     *                         compute method; the parameter types must match the method signature
     * @param <T>              the type returned by the {@code providerResolver}
     * @return the computed field value as returned by the resolved provider method
     * @throws IllegalArgumentException if no projection metadata is found for the class or if the
     *                                  field is not declared as computed
     * @throws IllegalStateException    if no suitable provider method (static or instance) can be found
     * @throws Exception                if the underlying reflective invocation fails
     */
    public static <T> Object computeField(BiFunction<Class<?>, String, T> providerResolver,
                                          Class<?> projectionClazz,
                                          String field,
                                          Object... dependencies) throws Exception {

        final ProjectionMetadata metadata = ProjectionRegistry.getMetadataFor(projectionClazz);

        if (metadata == null) {
            throw new IllegalArgumentException("The supposed projection class is not a projection nor an entity: " + projectionClazz.getSimpleName());
        }

        Optional<ComputedField> computedField = metadata.getComputedField(field, true);
        if (computedField.isEmpty()) {
            throw new IllegalArgumentException("No computed field found with the given name: " + field);
        }

        String methodName = "get" + capitalize(computedField.get().dtoField());

        for (var cp : metadata.computers()) {
            Method method = findMethod(cp.clazz(), methodName, dependencies);
            if (method == null) {
                continue;
            }

            Object computerInstance = providerResolver.apply(cp.clazz(), cp.bean());
            if (computerInstance == null) {
                return method.invoke(null, dependencies);
            } else {
                return method.invoke(computerInstance, dependencies);
            }
        }

        throw new IllegalStateException("No Bean nor static methods found to resolve computed field");
    }

    /**
     * Attempts to locate a public method with the given name and argument types on the specified class.
     * <p>
     * Parameter types are inferred from the runtime classes of the provided arguments. {@code null}
     * dependencies are treated as {@link Object} for lookup purposes. Only exact signature matches
     * resolved by {@link Class#getMethod(String, Class[])} are considered.
     * </p>
     *
     * @param clazz      the class on which the method lookup is performed
     * @param methodName the name of the method to search
     * @param args       the argument values that will be passed to the method upon invocation
     * @return the corresponding {@link Method} if found, or {@code null} if no matching method exists
     */
    static private Method findMethod(Class<?> clazz, String methodName, Object[] args) {
        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
        }
        try {
            return clazz.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

}