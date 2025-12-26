package io.github.cyfko.filterql.jpa.metamodel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a provider class responsible for computed fields
 * or other projection-related functionality.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({}) // Only usable within @Projection
public @interface Provider {
    /**
     * The class containing provider methods.
     *
     * <p>This class will be searched for:
     * <ul>
     *   <li>Methods matching {@code get[FieldName](...)} for computed fields</li>
     *   <li>Methods annotated with {@code @ExposedAs} for virtual fields</li>
     * </ul>
     * </p>
     *
     * @return the provider class
     */
    Class<?> value();

    /**
     * The bean name for IoC container lookup (optional).
     *
     * <p>When specified, the provider class will be resolved as a managed bean from the
     * IoC container. When empty, lookup is made only by type or static methods are expected.</p>
     *
     * @return the bean name for IoC lookup, or empty string for static method resolution
     */
    String bean() default "";
}