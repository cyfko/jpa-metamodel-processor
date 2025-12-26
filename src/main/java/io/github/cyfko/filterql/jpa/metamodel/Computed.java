package io.github.cyfko.filterql.jpa.metamodel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
 *   <li><b>Validation:</b> Ensures the matching computer method has parameters of correct types</li>
 * </ul>
 *
 * <h2>Method Resolution</h2>
 * <p>For a field annotated with {@code @Computed}, the system locates a method by:</p>
 * <ol>
 *   <li>Searching all {@link Provider} classes in {@link Projection#providers()} order</li>
 *   <li>Looking for a method named {@code get[FieldName]}, where {@code FieldName} is the
 *       capitalized field name (e.g., {@code fullName} → {@code getFullName})</li>
 *   <li>Matching method parameters to the types of {@code dependsOn} fields <b>in order</b></li>
 *   <li>Accepting additional parameters for injected services (if using bean-based computer)</li>
 * </ol>
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
 *     computers = {@Computer(UserComputations.class)}
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
 *     computers = {@Computer(value = CurrencyConverter.class, bean = "converter")}
 * )
 * public class OrderDTO {
 *     @Computed(dependsOn = {"amount", "currencyCode"})
 *     private BigDecimal amountInUSD;
 * }
 *
 * @Service("converter")
 * public class CurrencyConverter {
 *     @Autowired
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
 *     computers = {@Computer(DateUtils.class)}
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
 * <h2>Error Scenarios</h2>
 * <p>Compilation will fail if:</p>
 * <ul>
 *   <li>Any field in {@code dependsOn} does not exist in the source entity</li>
 *   <li>No matching computer method is found across all registered computers</li>
 *   <li>The matching method's parameter types don't align with dependency field types</li>
 *   <li>The matching method's return type is incompatible with the computed field type</li>
 * </ul>
 *
 * @since 1.0.0
 * @author Frank KOSSI
 * @see Projection
 * @see Provider
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
     *   <li>The expected parameter types and order for the computer method</li>
     *   <li>The validation contract for compile-time checking</li>
     * </ul>
     *
     * <h3>Order Matters</h3>
     * <p>The order of field names must match the parameter order in the computer method:</p>
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
     * The computer method can perform any necessary intermediate calculations internally.</p>
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
