package io.github.cyfko.filterql.jpa.metamodel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a DTO class as a projection from a JPA entity, establishing the mapping
 * between entity fields and DTO fields, including computed fields.
 *
 * <p>This annotation serves as the central declaration point for the entire projection
 * strategy. It defines the source entity and registers all computer classes responsible
 * for calculating derived fields.</p>
 *
 * <h2>Architecture Overview</h2>
 * <p>The projection system follows these principles:</p>
 * <ul>
 *   <li><b>Declarative Mapping:</b> The DTO alone declares all projection requirements</li>
 *   <li><b>Entity-Centric Dependencies:</b> All field dependencies reference the source entity directly</li>
 *   <li><b>Separation of Concerns:</b> Computation logic is externalized to dedicated computer classes</li>
 *   <li><b>IoC Integration:</b> Supports both static methods and dependency-injected beans</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * @Projection(
 *     entity = User.class,
 *     providers = {
 *         @Provider(UserComputations.class),
 *         @Provider(value = DateFormatter.class, bean = "isoDateFormatter")
 *     }
 * )
 * public class UserDTO {
 *     @Projected(from = "email")
 *     private String emailAddress;
 *
 *     @Projected
 *     private String firstName;
 *
 *     @Projected
 *     private String lastName;
 *
 *     @Computed(dependsOn = {"firstName", "lastName"})
 *     private String fullName;
 *
 *     @Computed(dependsOn = {"createdAt"})
 *     private String formattedDate;
 * }
 *
 * // Static computer
 * public class UserComputations {
 *     public static String getFullName(String firstName, String lastName) {
 *         return firstName + " " + lastName;
 *     }
 * }
 *
 * // Bean-based computer (Spring example)
 * @Service("isoDateFormatter")
 * public class DateFormatter {
 *     public String getFormattedDate(LocalDateTime createdAt) {
 *         return createdAt.format(DateTimeFormatter.ISO_DATE);
 *     }
 * }
 * }</pre>
 *
 * <h2>Provider Resolution Strategy</h2>
 * <p>When resolving a computed field, the system searches for a matching method across
 * all registered providers in declaration order:</p>
 * <ol>
 *   <li>Method name must follow the convention: {@code get[FieldName](...)}</li>
 *   <li>Method parameters must match the types of the {@code dependsOn} fields</li>
 *   <li>The first matching method found is used (first-match-wins strategy)</li>
 *   <li>If {@code bean} is specified, the computer is resolved from the IoC container;
 *       otherwise, a static method is required</li>
 * </ol>
 *
 * @since 1.0.0
 * @author Frank KOSSI
 * @see Provider
 * @see Computed
 * @see Projected
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Projection {
    /**
     * The source JPA entity class from which this DTO projects.
     *
     * <p>All {@link Projected} and {@link Computed} field dependencies must reference
     * fields that exist in this entity class.</p>
     *
     * @return the entity class to project from
     */
    Class<?> entity();

    /**
     * Array of provider classes that supply computation logic or other extension points for this projection.
     *
     * <p>Providers are evaluated in declaration order using a first-match-wins strategy.</p>
     *
     * <p><b>Provider Types:</b></p>
     * <ul>
     *   <li><b>Computation Providers:</b> Classes with methods matching {@code get[FieldName](...)}
     *       for {@link Computed} fields</li>
     *   <li><b>Virtual Field Providers:</b> Classes with {@code @ExposedAs} annotated methods
     *       returning {@code PredicateResolverMapping} for filtering</li>
     * </ul>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * providers = {
     *     @Provider(UserComputations.class),      // For @Computed fields
     *     @Provider(UserVirtualFields.class)      // For @ExposedAs virtual fields
     * }
     * }</pre>
     *
     * @return the array of {@link Provider} declarations
     */
    Provider[] providers() default {};
}