package io.github.cyfko.tests;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;

import io.github.cyfko.jpametamodel.processor.MetamodelProcessor;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;

/**
 * Tests explicit method resolution via computedBy in @Computed.
 */
class ComputedByTest {

    @Test
    void testComputedByExternalStaticClass() throws IOException {
        var entity = JavaFileObjects.forSourceString(
            "io.github.cyfko.example.User",
            """
            package io.github.cyfko.example;
            import jakarta.persistence.*;
            @Entity
            public class User {
                @Id
                private Long id;
                private String firstName;
                private String lastName;
            }
            """
        );
        var external = JavaFileObjects.forSourceString(
            "io.github.cyfko.example.ExternalComputer",
            """
            package io.github.cyfko.example;
            public class ExternalComputer {
                public static String joinNames(String first, String last) {
                    return first + ":" + last;
                }
            }
            """
        );
        var dto = JavaFileObjects.forSourceString(
            "io.github.cyfko.example.UserDTO",
            """
            package io.github.cyfko.example;
            import io.github.cyfko.projection.*;
            @Projection(from = User.class)
            public interface UserDTO {
                @Computed(dependsOn = {"firstName", "lastName"}, computedBy = @Method(value = "joinNames", type = ExternalComputer.class))
                String getDisplayName();
            }
            """
        );
        Compilation compilation = Compiler.javac().withProcessors(new MetamodelProcessor()).compile(entity, external, dto);
        assertThat(compilation).succeeded();
    }

    private static final String PROVIDER = "io.github.cyfko.example.UserComputations";

    private static final String PROVIDER_CODE = """
        package io.github.cyfko.example;
        public class UserComputations {
            public static String buildUserDisplayName(String first, String last) {
                return first + "-" + last;
            }
            public static String toFullName(String first, String last) {
                return first + " " + last;
            }
        }
        """;

    @Test
    void testComputedByMethodNameOnly() throws IOException {
        var entity = JavaFileObjects.forSourceString(
            "io.github.cyfko.example.User",
            """
            package io.github.cyfko.example;
            import jakarta.persistence.*;
            @Entity
            public class User {
                @Id
                private Long id;
                private String firstName;
                private String lastName;
            }
            """
        );
        var provider = JavaFileObjects.forSourceString(PROVIDER, PROVIDER_CODE);
        var dto = JavaFileObjects.forSourceString(
            "io.github.cyfko.example.UserDTO",
            """
            package io.github.cyfko.example;
            import io.github.cyfko.projection.*;
            @Projection(from = User.class, providers = {@Provider(UserComputations.class)})
            public interface UserDTO {
                @Computed(dependsOn = {"firstName", "lastName"}, computedBy = @Method("buildUserDisplayName"))
                String getDisplayName();
            }
            """
        );
        Compilation compilation = Compiler.javac().withProcessors(new MetamodelProcessor()).compile(entity, provider, dto);
        assertThat(compilation).succeeded();
        // If compilation succeeds, computedBy resolved correctly
    }

    @Test
    void testComputedByTypeAndMethod() throws IOException {
        var entity = JavaFileObjects.forSourceString(
            "io.github.cyfko.example.User",
            """
            package io.github.cyfko.example;
            import jakarta.persistence.*;
            @Entity
            public class User {
                @Id
                private Long id;
                private String firstName;
                private String lastName;
            }
            """
        );
        var provider = JavaFileObjects.forSourceString(PROVIDER, PROVIDER_CODE);
        var dto = JavaFileObjects.forSourceString(
            "io.github.cyfko.example.UserDTO",
            """
            package io.github.cyfko.example;
            import io.github.cyfko.projection.*;
            @Projection(from = User.class, providers = {@Provider(UserComputations.class)})
            public interface UserDTO {
                @Computed(dependsOn = {"firstName", "lastName"}, computedBy = @Method(value = "buildUserDisplayName", type = UserComputations.class))
                String getDisplayName();
            }
            """
        );
        Compilation compilation = Compiler.javac().withProcessors(new MetamodelProcessor()).compile(entity, provider, dto);
        assertThat(compilation).succeeded();
    }

    @Test
    void testComputedByTypeOnly() throws IOException {
        var entity = JavaFileObjects.forSourceString(
            "io.github.cyfko.example.User",
            """
            package io.github.cyfko.example;
            import jakarta.persistence.*;
            @Entity
            public class User {
                @Id
                private Long id;
                private String firstName;
                private String lastName;
            }
            """
        );
        var provider = JavaFileObjects.forSourceString(PROVIDER, PROVIDER_CODE);
        var dto = JavaFileObjects.forSourceString(
            "io.github.cyfko.example.UserDTO",
            """
            package io.github.cyfko.example;
            import io.github.cyfko.projection.*;
            @Projection(from = User.class, providers = {@Provider(UserComputations.class)})
            public interface UserDTO {
                @Computed(dependsOn = {"firstName", "lastName"}, computedBy = @Method(type = UserComputations.class))
                String getFullName();
            }
            """
        );
        Compilation compilation = Compiler.javac().withProcessors(new MetamodelProcessor()).compile(entity, provider, dto);
        assertThat(compilation).succeeded();
    }

    @Test
    void testComputedByMethodNotFoundFails() throws IOException {
        var entity = JavaFileObjects.forSourceString(
            "io.github.cyfko.example.User",
            """
            package io.github.cyfko.example;
            import jakarta.persistence.*;
            @Entity
            public class User {
                @Id
                private Long id;
                private String firstName;
                private String lastName;
            }
            """
        );
        var provider = JavaFileObjects.forSourceString(PROVIDER, PROVIDER_CODE);
        var dto = JavaFileObjects.forSourceString(
            "io.github.cyfko.example.UserDTO",
            """
            package io.github.cyfko.example;
            import io.github.cyfko.projection.*;
            @Projection(from = User.class, providers = {@Provider(UserComputations.class)})
            public interface UserDTO {
                @Computed(dependsOn = {"firstName", "lastName"}, computedBy = @Method("doesNotExist"))
                String getDisplayName();
            }
            """
        );
        Compilation compilation = Compiler.javac().withProcessors(new MetamodelProcessor()).compile(entity, provider, dto);
        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("doesNotExist");
    }

    // ==================== Autoboxing/Unboxing Tests ====================

    @Test
    void testAutoboxingIntegerToPrimitive() throws IOException {
        var entity = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.Product",
                """
                package io.github.cyfko.example;
                import jakarta.persistence.*;
                @Entity
                public class Product {
                    @Id
                    private Long id;
                    private Integer quantity;  // Wrapper type
                }
                """
        );
        var provider = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.ProductComputer",
                """
                package io.github.cyfko.example;
                public class ProductComputer {
                    public static String formatQuantity(int qty) {  // Primitive type
                        return "Qty: " + qty;
                    }
                }
                """
        );
        var dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.ProductDTO",
                """
                package io.github.cyfko.example;
                import io.github.cyfko.projection.*;
                @Projection(from = Product.class)
                public interface ProductDTO {
                    @Computed(dependsOn = {"quantity"}, computedBy = @Method(value = "formatQuantity", type = ProductComputer.class))
                    String getFormattedQuantity();
                }
                """
        );
        Compilation compilation = Compiler.javac().withProcessors(new MetamodelProcessor()).compile(entity, provider, dto);
        assertThat(compilation).succeeded();
    }

    @Test
    void testUnboxingPrimitiveToWrapper() throws IOException {
        var entity = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.Product",
                """
                package io.github.cyfko.example;
                import jakarta.persistence.*;
                @Entity
                public class Product {
                    @Id
                    private Long id;
                    private int stock;  // Primitive type
                }
                """
        );
        var provider = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.ProductComputer",
                """
                package io.github.cyfko.example;
                public class ProductComputer {
                    public static String formatStock(Integer stock) {  // Wrapper type
                        return "Stock: " + stock;
                    }
                }
                """
        );
        var dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.ProductDTO",
                """
                package io.github.cyfko.example;
                import io.github.cyfko.projection.*;
                @Projection(from = Product.class)
                public interface ProductDTO {
                    @Computed(dependsOn = {"stock"}, computedBy = @Method(value = "formatStock", type = ProductComputer.class))
                    String getFormattedStock();
                }
                """
        );
        Compilation compilation = Compiler.javac().withProcessors(new MetamodelProcessor()).compile(entity, provider, dto);
        assertThat(compilation).succeeded();
    }

    // ==================== Numeric Promotion Tests ====================

    @Test
    void testNumericPromotionIntToLong() throws IOException {
        var entity = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.User",
                """
                package io.github.cyfko.example;
                import jakarta.persistence.*;
                @Entity
                public class User {
                    @Id
                    private Long id;
                    private Integer age;  // Integer wrapper
                }
                """
        );
        var provider = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputer",
                """
                package io.github.cyfko.example;
                public class UserComputer {
                    public static String formatAge(long age) {  // long primitive
                        return "Age: " + age;
                    }
                }
                """
        );
        var dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                package io.github.cyfko.example;
                import io.github.cyfko.projection.*;
                @Projection(from = User.class)
                public interface UserDTO {
                    @Computed(dependsOn = {"age"}, computedBy = @Method(value = "formatAge", type = UserComputer.class))
                    String getFormattedAge();
                }
                """
        );
        Compilation compilation = Compiler.javac().withProcessors(new MetamodelProcessor()).compile(entity, provider, dto);
        assertThat(compilation).succeeded();
    }

    @Test
    void testNumericPromotionByteToDouble() throws IOException {
        var entity = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.Sensor",
                """
                package io.github.cyfko.example;
                import jakarta.persistence.*;
                @Entity
                public class Sensor {
                    @Id
                    private Long id;
                    private Byte temperature;  // Byte wrapper
                }
                """
        );
        var provider = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.SensorComputer",
                """
                package io.github.cyfko.example;
                public class SensorComputer {
                    public static String formatTemperature(double temp) {  // double primitive
                        return String.format("%.2f°C", temp);
                    }
                }
                """
        );
        var dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.SensorDTO",
                """
                package io.github.cyfko.example;
                import io.github.cyfko.projection.*;
                @Projection(from = Sensor.class)
                public interface SensorDTO {
                    @Computed(dependsOn = {"temperature"}, computedBy = @Method(value = "formatTemperature", type = SensorComputer.class))
                    String getFormattedTemperature();
                }
                """
        );
        Compilation compilation = Compiler.javac().withProcessors(new MetamodelProcessor()).compile(entity, provider, dto);
        assertThat(compilation).succeeded();
    }

    @Test
    void testNumericPromotionShortToFloat() throws IOException {
        var entity = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.Measurement",
                """
                package io.github.cyfko.example;
                import jakarta.persistence.*;
                @Entity
                public class Measurement {
                    @Id
                    private Long id;
                    private short value;  // short primitive
                }
                """
        );
        var provider = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.MeasurementComputer",
                """
                package io.github.cyfko.example;
                public class MeasurementComputer {
                    public static String formatValue(float val) {  // float primitive
                        return "Value: " + val;
                    }
                }
                """
        );
        var dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.MeasurementDTO",
                """
                package io.github.cyfko.example;
                import io.github.cyfko.projection.*;
                @Projection(from = Measurement.class)
                public interface MeasurementDTO {
                    @Computed(dependsOn = {"value"}, computedBy = @Method(value = "formatValue", type = MeasurementComputer.class))
                    String getFormattedValue();
                }
                """
        );
        Compilation compilation = Compiler.javac().withProcessors(new MetamodelProcessor()).compile(entity, provider, dto);
        assertThat(compilation).succeeded();
    }

    @Test
    void testNumericPromotionCharToInt() throws IOException {
        var entity = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.Symbol",
                """
                package io.github.cyfko.example;
                import jakarta.persistence.*;
                @Entity
                public class Symbol {
                    @Id
                    private Long id;
                    private Character symbol;  // Character wrapper
                }
                """
        );
        var provider = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.SymbolComputer",
                """
                package io.github.cyfko.example;
                public class SymbolComputer {
                    public static String formatCode(int code) {  // int primitive
                        return "Code: " + code;
                    }
                }
                """
        );
        var dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.SymbolDTO",
                """
                package io.github.cyfko.example;
                import io.github.cyfko.projection.*;
                @Projection(from = Symbol.class)
                public interface SymbolDTO {
                    @Computed(dependsOn = {"symbol"}, computedBy = @Method(value = "formatCode", type = SymbolComputer.class))
                    String getFormattedCode();
                }
                """
        );
        Compilation compilation = Compiler.javac().withProcessors(new MetamodelProcessor()).compile(entity, provider, dto);
        assertThat(compilation).succeeded();
    }

    // ==================== Combined Tests ====================

    @Test
    void testAutoboxingWithNumericPromotion() throws IOException {
        var entity = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.Order",
                """
                package io.github.cyfko.example;
                import jakarta.persistence.*;
                @Entity
                public class Order {
                    @Id
                    private Long id;
                    private Integer itemCount;  // Wrapper
                    private Float unitPrice;    // Wrapper
                }
                """
        );
        var provider = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.OrderComputer",
                """
                package io.github.cyfko.example;
                import java.math.BigDecimal;
                public class OrderComputer {
                    public static BigDecimal calculateTotal(long count, double price) {  // Primitives with promotion
                        return BigDecimal.valueOf(count * price);
                    }
                }
                """
        );
        var dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.OrderDTO",
                """
                package io.github.cyfko.example;
                import io.github.cyfko.projection.*;
                import java.math.BigDecimal;
                @Projection(from = Order.class)
                public interface OrderDTO {
                    @Computed(dependsOn = {"itemCount", "unitPrice"}, computedBy = @Method(value = "calculateTotal", type = OrderComputer.class))
                    BigDecimal getTotal();
                }
                """
        );
        Compilation compilation = Compiler.javac().withProcessors(new MetamodelProcessor()).compile(entity, provider, dto);
        assertThat(compilation).succeeded();
    }

    // ==================== Negative Tests ====================

    @Test
    void testIncompatibleTypesWithoutPromotionFails() throws IOException {
        var entity = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.Product",
                """
                package io.github.cyfko.example;
                import jakarta.persistence.*;
                @Entity
                public class Product {
                    @Id
                    private Long id;
                    private String name;
                }
                """
        );
        var provider = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.ProductComputer",
                """
                package io.github.cyfko.example;
                public class ProductComputer {
                    public static String formatName(int name) {  // Wrong type: String cannot convert to int
                        return "Name: " + name;
                    }
                }
                """
        );
        var dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.ProductDTO",
                """
                package io.github.cyfko.example;
                import io.github.cyfko.projection.*;
                @Projection(from = Product.class)
                public interface ProductDTO {
                    @Computed(dependsOn = {"name"}, computedBy = @Method(value = "formatName", type = ProductComputer.class))
                    String getFormattedName();
                }
                """
        );
        Compilation compilation = Compiler.javac().withProcessors(new MetamodelProcessor()).compile(entity, provider, dto);
        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("incompatible type");
    }

    @Test
    void testInvalidNumericPromotionFails() throws IOException {
        var entity = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.Data",
                """
                package io.github.cyfko.example;
                import jakarta.persistence.*;
                @Entity
                public class Data {
                    @Id
                    private Long id;
                    private long value;  // long primitive
                }
                """
        );
        var provider = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.DataComputer",
                """
                package io.github.cyfko.example;
                public class DataComputer {
                    public static String formatValue(int val) {  // int - narrowing not allowed
                        return "Value: " + val;
                    }
                }
                """
        );
        var dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.DataDTO",
                """
                package io.github.cyfko.example;
                import io.github.cyfko.projection.*;
                @Projection(from = Data.class)
                public interface DataDTO {
                    @Computed(dependsOn = {"value"}, computedBy = @Method(value = "formatValue", type = DataComputer.class))
                    String getFormattedValue();
                }
                """
        );
        Compilation compilation = Compiler.javac().withProcessors(new MetamodelProcessor()).compile(entity, provider, dto);
        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("incompatible type");
    }

    @Test
    void testWrapperToWrapperPromotionFails() throws IOException {
        var entity = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.Account",
                """
                package io.github.cyfko.example;
                import jakarta.persistence.*;
                @Entity
                public class Account {
                    @Id
                    private Long id;
                    private Integer balance;  // Integer wrapper
                }
                """
        );
        var provider = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.AccountComputer",
                """
                package io.github.cyfko.example;
                public class AccountComputer {
                    public static String formatBalance(Long balance) {  // Long wrapper - no direct wrapper-to-wrapper conversion
                        return "Balance: " + balance;
                    }
                }
                """
        );
        var dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.AccountDTO",
                """
                package io.github.cyfko.example;
                import io.github.cyfko.projection.*;
                @Projection(from = Account.class)
                public interface AccountDTO {
                    @Computed(dependsOn = {"balance"}, computedBy = @Method(value = "formatBalance", type = AccountComputer.class))
                    String getFormattedBalance();
                }
                """
        );
        Compilation compilation = Compiler.javac().withProcessors(new MetamodelProcessor()).compile(entity, provider, dto);
        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("incompatible type");
    }
}
