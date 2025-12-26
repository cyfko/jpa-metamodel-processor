package io.github.cyfko.filterql.jpa.metamodel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.BiFunction;

/**
 * Marks a DTO field as a computed field whose value is derived from one or more
 * entity fields through a computation method.
 *
 * <p>Computed fields are <b>not directly mapped</b> from the entity. Instead, their values
 * are calculated at projection time by invoking a method from a registered {@link Provider}
 * class, using entity field values as inputs.</p>
 *
 * <h2>Core Concepts</h2>
 *
 * <h3>Entity-Centric Dependencies</h3>
 * <p><b>Critical principle:</b> All dependencies declared in {@link #dependsOn()} must
 * reference fields from the source entity specified in {@link Projection#entity()}.
 * Dependencies on other computed fields are <b>not supported</b>.</p>
 *
 * <p>This design choice ensures:</p>
 * <ul>
 *   <li>Deterministic resolution order (no dependency graphs needed)</li>
 *   <li>Independent computation of each field</li>
 *   <li>Simplified code generation logic</li>
 * </ul>
 *
 * <h3>Validation Role</h3>
 * <p>The {@code dependsOn} array serves as both:</p>
 * <ul>
 *   <li><b>Declaration:</b> Documents which entity fields are required</li>
 *   <li><b>Validation:</b> Ensures the matching provider method has parameters of correct types</li>
 * </ul>
 *
 * <h2>Method Resolution</h2>
 * <p>For a field annotated with {@code @Computed}, the system locates a computation method by:</p>
 * <ol>
 *   <li>Searching all {@link Provider} classes in {@link Projection#providers()} order</li>
 *   <li>Looking for a method named {@code get[FieldName]}, where {@code FieldName} is the
 *       capitalized field name (e.g., {@code fullName} → {@code getFullName})</li>
 *   <li>Matching method parameters to the types of {@code dependsOn} fields <b>in order</b></li>
 *   <li>Accepting additional parameters for injected services (if using bean-based provider)</li>
 * </ol>
 *
 * <h3>Provider Method Requirements</h3>
 * <p>For a provider method to be valid, it must:</p>
 * <ul>
 *   <li><b>Naming:</b> Follow the convention {@code get[FieldName](...)}
 *       <br><i>Example:</i> For field {@code fullName}, method must be {@code getFullName}</li>
 *   <li><b>Parameters:</b> Accept parameters matching {@code dependsOn} types <b>in the same order</b>
 *       <br><i>Example:</i> {@code dependsOn = {"firstName", "lastName"}} requires
 *       {@code (String firstName, String lastName)}</li>
 *   <li><b>Return Type:</b> Return a type compatible with the computed field's type</li>
 *   <li><b>Modifiers:</b> Can be either {@code static} or instance method. The runtime resolver
 *       determines which to use based on bean availability.</li>
 * </ul>
 *
 * <h3>Runtime Resolution Strategy</h3>
 * <p>At runtime, the resolution process works as follows:</p>
 * <ol>
 *   <li>A {@code providerResolver} (example: {@link io.github.cyfko.filterql.jpa.metamodel.utils.ProjectionUtils#computeField(BiFunction, Class, String, Object...)})
 *   is invoked with the provider class and bean hint</li>
 *   <li>If the resolver returns {@code null}: the method is invoked <b>statically</b></li>
 *   <li>If the resolver returns an instance: the method is invoked on that <b>instance</b></li>
 * </ol>
 *
 * <p><b>Important:</b> The {@link Provider#bean()} parameter is a <b>hint</b> for bean lookup,
 * not a strict requirement. Both static and instance methods can coexist in the same provider class.
 * The runtime resolver decides which approach to use based on IoC container availability.</p>
 *
 * <table border="1">
 *   <caption>Runtime Resolution Examples</caption>
 *   <tr>
 *     <th>Scenario</th>
 *     <th>Resolver Behavior</th>
 *     <th>Method Invocation</th>
 *   </tr>
 *   <tr>
 *     <td>IoC available, bean found</td>
 *     <td>Returns bean instance</td>
 *     <td>Instance method on bean</td>
 *   </tr>
 *   <tr>
 *     <td>IoC available, bean not found</td>
 *     <td>Returns {@code null}</td>
 *     <td>Static method fallback</td>
 *   </tr>
 *   <tr>
 *     <td>No IoC framework</td>
 *     <td>Returns {@code null}</td>
 *     <td>Static method</td>
 *   </tr>
 *   <tr>
 *     <td>Explicit static resolution</td>
 *     <td>Returns {@code null}</td>
 *     <td>Static method</td>
 *   </tr>
 * </table>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Simple Computation (String Concatenation)</h3>
 * <pre>{@code
 * @Entity
 * public class User {
 *     private String firstName;
 *     private String lastName;
 * }
 *
 * @Projection(
 *     entity = User.class,
 *     providers = {@Provider(UserComputations.class)}
 * )
 * public class UserDTO {
 *     @Computed(dependsOn = {"firstName", "lastName"})
 *     private String fullName;
 * }
 *
 * public class UserComputations {
 *     public static String getFullName(String firstName, String lastName) {
 *         return (firstName + " " + lastName).trim();
 *     }
 * }
 * }</pre>
 *
 * <h3>Complex Computation with External Services</h3>
 * <pre>{@code
 * @Entity
 * public class Order {
 *     private BigDecimal amount;
 *     private String currencyCode;
 * }
 *
 * @Projection(
 *     entity = Order.class,
 *     providers = {@Provider(value = CurrencyConverter.class, bean = "converter")}
 * )
 * public class OrderDTO {
 *     @Computed(dependsOn = {"amount", "currencyCode"})
 *     private BigDecimal amountInUSD;
 * }
 *
 * // Assumes that CurrencyConverter is a bean in an IoC context
 * public class CurrencyConverter {
 *     private ExchangeRateService exchangeRateService;
 *
 *     public BigDecimal getAmountInUSD(BigDecimal amount, String currencyCode) {
 *         if ("USD".equals(currencyCode)) return amount;
 *         BigDecimal rate = exchangeRateService.getRate(currencyCode, "USD");
 *         return amount.multiply(rate);
 *     }
 * }
 * }</pre>
 *
 * <h3>Date Formatting</h3>
 * <pre>{@code
 * @Entity
 * public class Event {
 *     private LocalDateTime startTime;
 * }
 *
 * @Projection(
 *     entity = Event.class,
 *     providers = {@Provider(DateUtils.class)}
 * )
 * public class EventDTO {
 *     @Computed(dependsOn = {"startTime"})
 *     private String formattedStart;
 * }
 *
 * public class DateUtils {
 *     public static String getFormattedStart(LocalDateTime startTime) {
 *         return startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
 *     }
 * }
 * }</pre>
 *
 * <h3>Multiple Providers with Flexible Resolution</h3>
 * <pre>{@code
 * @Projection(
 *     entity = User.class,
 *     providers = {
 *         @Provider(value = UserComputations.class, bean = "userComputer"),  // Hint for IoC lookup
 *         @Provider(CommonComputations.class)                                // No hint, static fallback
 *     }
 * )
 * public class UserDTO {
 *     @Computed(dependsOn = {"firstName", "lastName"})
 *     private String fullName;
 * }
 *
 * // Provider can have both static and instance methods
 * // Assumes that UserComputations is the bean "userComputer" in an IoC context
 * public class UserComputations {
 *     // Instance method - used if bean is found by resolver
 *     public String getFullName(String firstName, String lastName) {
 *         return (firstName + " " + lastName).trim();
 *     }
 *
 *     // Static method - used if resolver returns null
 *     public static String getFullName(String firstName, String lastName) {
 *         return (firstName + " " + lastName).trim();
 *     }
 * }
 * }</pre>
 *
 * <h2>Parameter Type Matching</h2>
 * <p>The annotation processor validates parameter types at compile time to ensure they match
 * the entity field types:</p>
 * <pre>{@code
 * @Entity
 * public class User {
 *     private String firstName;      // Type: String
 *     private String lastName;       // Type: String
 *     private LocalDate birthDate;   // Type: LocalDate
 * }
 *
 * public class UserComputations {
 *     // ✅ CORRECT: Parameter types match dependsOn field types
 *     public static String getFullName(String firstName, String lastName) { ... }
 *
 *     // ✅ CORRECT: Parameter type matches birthDate type
 *     public static Integer getAge(LocalDate birthDate) { ... }
 *
 *     // ❌ COMPILATION ERROR: Parameter type doesn't match (expects LocalDate, not String)
 *     public static Integer getAge(String birthDate) { ... }
 *
 *     // ❌ COMPILATION ERROR: Wrong parameter types
 *     public static String getFullName(Object firstName, Object lastName) { ... }
 * }
 * }</pre>
 *
 * <h2>Compilation Errors</h2>
 * <p>The annotation processor will generate compilation errors in these scenarios:</p>
 * <ul>
 *   <li><b>Missing Dependency:</b> Any field in {@code dependsOn} does not exist in the source entity</li>
 *   <li><b>No Matching Method:</b> No provider has a method matching the required signature</li>
 *   <li><b>Parameter Type Mismatch:</b> Method parameters don't match {@code dependsOn} field types</li>
 *   <li><b>Parameter Order Mismatch:</b> Method parameters are in wrong order</li>
 *   <li><b>Return Type Incompatible:</b> Method return type is not assignable to the computed field type</li>
 * </ul>
 *
 * <h3>Example Error Messages</h3>
 * <pre>
 * ❌ No matching provider method found for computed field 'fullName'.
 *    Expected: String getFullName(String firstName, String lastName)
 *
 * ❌ Method MyComputations.getAge has incompatible return type.
 *    Expected: Integer, Found: String
 *
 * ❌ Computed field 'fullName': Field 'firstName' not found in entity User
 * </pre>
 *
 * <h2>Best Practices</h2>
 * <ol>
 *   <li><b>Provide Both Static and Instance Methods</b> when possible, for maximum flexibility
 *       across different runtime environments</li>
 *   <li><b>Use Bean Hints</b> ({@link Provider#bean()}) to guide IoC resolution when multiple
 *       beans of the same type exist</li>
 *   <li><b>Group Related Computations</b> in a single provider class for better organization</li>
 *   <li><b>Order Providers</b> from most specific to most general for efficient resolution</li>
 *   <li><b>Keep Methods Pure</b> when possible (no side effects) for easier testing and debugging</li>
 *   <li><b>Document Dependencies</b> clearly in your provider class JavaDoc</li>
 * </ol>
 *
 * <h2>Filtering Limitations</h2>
 * <p><b>Important:</b> Computed fields <b>cannot be used in filters</b> because they are calculated
 * in-memory after database retrieval. This is a fundamental architectural constraint shared by all
 * major frameworks (GraphQL resolvers, Spring Data projections, Hibernate formulas).</p>
 *
 * <p>To filter on computed field logic, filter on its dependencies instead:</p>
 * <pre>{@code
 * // ❌ WRONG: Cannot filter on computed field
 * query.filter("fullName = 'John Doe'")
 *
 * // ✅ CORRECT: Filter on dependencies
 * query.filter("firstName = 'John' AND lastName = 'Doe'")
 * }</pre>
 *
 * @since 1.0.0
 * @author Frank KOSSI
 * @see Projection
 * @see Provider
 * @see Projected
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface Computed {
    /**
     * Array of entity field names that this computed field depends on.
     *
     * <p><b>Critical constraint:</b> All field names must reference fields from the source
     * entity declared in {@link Projection#entity()}. References to other computed fields
     * or DTO-specific fields are not permitted.</p>
     *
     * <p>This array defines:</p>
     * <ul>
     *   <li>Which entity fields will be fetched from the database</li>
     *   <li>The expected parameter types and order for the provider method</li>
     *   <li>The validation contract for compile-time checking</li>
     * </ul>
     *
     * <h3>Order Matters</h3>
     * <p>The order of field names must match the parameter order in the provider method:</p>
     * <pre>{@code
     * @Computed(dependsOn = {"firstName", "lastName"})  // Order: firstName, then lastName
     * private String fullName;
     *
     * // Correct method signature
     * public static String getFullName(String firstName, String lastName) { ... }
     *
     * // WRONG - parameters in wrong order
     * public static String getFullName(String lastName, String firstName) { ... }
     * }</pre>
     *
     * <h3>Why Entity-Only Dependencies?</h3>
     * <p>Restricting dependencies to entity fields (no computed-to-computed dependencies):</p>
     * <ul>
     *   <li>Eliminates dependency graph resolution complexity</li>
     *   <li>Prevents circular dependency issues</li>
     *   <li>Ensures each field can be computed independently</li>
     *   <li>Simplifies code generation and debugging</li>
     * </ul>
     *
     * <p><b>Design rationale:</b> If a computed field needs values that would come from
     * another computed field, both should declare their dependencies directly from the entity.
     * The provider method can perform any necessary intermediate calculations internally.</p>
     *
     * <h3>Examples</h3>
     * <pre>{@code
     * // Simple dependency
     * @Computed(dependsOn = {"createdAt"})
     * private String formattedDate;
     *
     * // Multiple dependencies
     * @Computed(dependsOn = {"firstName", "lastName", "middleName"})
     * private String fullNameWithMiddle;
     *
     * // Nested entity field access (assuming User has Address relationship)
     * @Computed(dependsOn = {"address.city", "address.country"})
     * private String location;
     * }</pre>
     *
     * @return array of entity field paths that this computed field requires
     */
    String[] dependsOn();
}