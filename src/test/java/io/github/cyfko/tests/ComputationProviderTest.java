package io.github.cyfko.tests;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import io.github.cyfko.jpametamodel.processor.MetamodelProcessor;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;

/**
 * Exhaustive test suite for computation provider functionality.
 * 
 * This test suite covers:
 * - Method resolution and validation
 * - Return type validation
 * - Parameter type and count validation
 * - Multiple computers resolution order
 * - Static vs instance methods
 * - Bean-based computers
 * - Nested dependencies
 * - Various data types (primitives, objects, collections, embeddables)
 * - Error scenarios and compilation failures
 */
class ComputationProviderTest {

    // ==================== Test Data: Entities ====================

    private JavaFileObject createUserEntity() {
        return JavaFileObjects.forSourceString(
                "io.github.cyfko.example.User",
                """
                        package io.github.cyfko.example;
                        import jakarta.persistence.*;
                        import java.time.LocalDate;
                        import java.math.BigDecimal;

                        @Entity
                        public class User {
                            @Id
                            private Long id;
                            private String firstName;
                            private String lastName;
                            private String email;
                            private LocalDate birthDate;
                            private Integer age;
                            private BigDecimal salary;
                        }
                        """);
    }

    private JavaFileObject createProductEntity() {
        return JavaFileObjects.forSourceString(
                "io.github.cyfko.example.Product",
                """
                        package io.github.cyfko.example;
                        import jakarta.persistence.*;
                        import java.math.BigDecimal;

                        @Entity
                        public class Product {
                            @Id
                            private Long id;
                            private String name;
                            private BigDecimal price;
                            private Integer quantity;
                        }
                        """);
    }

    private JavaFileObject createOrderEntity() {
        return JavaFileObjects.forSourceString(
                "io.github.cyfko.example.Order",
                """
                        package io.github.cyfko.example;
                        import jakarta.persistence.*;
                        import java.math.BigDecimal;
                        import java.time.LocalDateTime;

                        @Entity
                        public class Order {
                            @Id
                            private Long id;
                            private BigDecimal totalAmount;
                            private LocalDateTime createdAt;
                            private String status;
                        }
                        """);
    }

    // ==================== Test Category 1: Missing Method ====================

    @Test
    void testMissingComputationMethod() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            // No getFullName method
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(UserComputations.class)}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"firstName", "lastName"})
                            private String fullName;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("No matching provider method found for computed field 'fullName'");
    }

    @Test
    void testMissingComputationMethodWithNoComputers() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(from = User.class)
                        public class UserDTO {
                            @Computed(dependsOn = {"firstName", "lastName"})
                            private String fullName;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, dto);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("No matching provider method found for computed field 'fullName'");
    }

    // ==================== Test Category 2: Wrong Method Name ====================

    @Test
    void testWrongMethodName() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            // Wrong method name: should be getFullName, not computeFullName
                            public static String computeFullName(String firstName, String lastName) {
                                return firstName + " " + lastName;
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(UserComputations.class)}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"firstName", "lastName"})
                            private String fullName;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("No matching provider method found for computed field 'fullName'");
    }

    @Test
    void testMethodNameCaseSensitivity() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            // Wrong case: should be getFullName, not getfullName
                            public static String getfullName(String firstName, String lastName) {
                                return firstName + " " + lastName;
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(UserComputations.class)}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"firstName", "lastName"})
                            private String fullName;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("No matching provider method found for computed field 'fullName'");
    }

    // ==================== Test Category 3: Return Type Mismatch
    // ====================

    @Test
    void testReturnTypeMismatch() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            // Wrong return type: should be String, not Integer
                            public static Integer getFullName(String firstName, String lastName) {
                                return firstName.length() + lastName.length();
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(UserComputations.class)}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"firstName", "lastName"})
                            private String fullName;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                ".getFullName has incompatible return type. Required: java.lang.String, Found: java.lang.Integer");
    }

    @Test
    void testReturnTypeMismatchPrimitive() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            // Wrong return type: should be Integer (boxed), not int (primitive)
                            public static int getAge(java.time.LocalDate birthDate) {
                                return 25;
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(UserComputations.class)}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"birthDate"})
                            private Integer age;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).failed();
        assertThat(compilation)
                .hadErrorContaining("has incompatible return type. Required: java.lang.Integer, Found: int");
    }

    // ==================== Test Category 4: Parameter Count Mismatch
    // ====================

    @Test
    void testParameterCountTooFew() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            // Missing parameter: should have 2 parameters, only has 1
                            public static String getFullName(String firstName) {
                                return firstName;
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(UserComputations.class)}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"firstName", "lastName"})
                            private String fullName;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).failed();
        assertThat(compilation)
                .hadErrorContaining(".getFullName has incompatible parameters count. Required: 2, Found: 1");
    }

    @Test
    void testParameterCountTooMany() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            // Too many parameters: should have 2, has 3
                            public static String getFullName(String firstName, String lastName, String middleName) {
                                return firstName + " " + middleName + " " + lastName;
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(UserComputations.class)}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"firstName", "lastName"})
                            private String fullName;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).failed();
        assertThat(compilation)
                .hadErrorContaining(".getFullName has incompatible parameters count. Required: 2, Found: 3");
    }

    @Test
    void testParameterCountZero() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            // Should have 1 parameter but has 0
                            public static Integer getAge() {
                                return 25;
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(UserComputations.class)}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"birthDate"})
                            private Integer age;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("has incompatible parameters count. Required: 1, Found: 0");
    }

    // ==================== Test Category 5: Parameter Type Mismatch
    // ====================

    @Test
    void testParameterTypeMismatch() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            // Wrong parameter type: should be String, not Integer
                            public static String getFullName(Integer firstName, String lastName) {
                                return firstName.toString() + " " + lastName;
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(UserComputations.class)}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"firstName", "lastName"})
                            private String fullName;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                ".getFullName has incompatible type on parameter at position 0. Required: java.lang.String, Found: java.lang.Integer");
    }

    @Test
    void testParameterNamesIgnored() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            // Parameters in good order (parameter's names does not matter): should be firstName, lastName
                            public static String getFullName(String lastName, String firstName) {
                                return firstName + " " + lastName;
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(UserComputations.class)}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"firstName", "lastName"})
                            private String fullName;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).succeeded();
    }

    @Test
    void testParameterTypeMismatchWithPrimitives() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            // Wrong: expects Integer (boxed) but entity has int (primitive)
                            public static String getAgeString(int age) {
                                return age.toString();
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(UserComputations.class)}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"age"})
                            private String ageString;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        // This should not succeed even if Integer and int are compatible in JPA context
        // But let's verify the actual behavior
        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "has incompatible type on parameter at position 0. Required: java.lang.Integer, Found: int");
    }

    // ==================== Test Category 6: Multiple Computers Resolution
    // ====================

    @Test
    void testMultipleComputersFirstMatchWins() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer1 = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations1",
                """
                        package io.github.cyfko.example;

                        public class UserComputations1 {
                            public static String getFullName(String firstName, String lastName) {
                                return "FIRST: " + firstName + " " + lastName;
                            }
                        }
                        """);

        JavaFileObject computer2 = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations2",
                """
                        package io.github.cyfko.example;

                        public class UserComputations2 {
                            public static String getFullName(String firstName, String lastName) {
                                return "SECOND: " + firstName + " " + lastName;
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {
                                @Provider(UserComputations1.class),
                                @Provider(UserComputations2.class)
                            }
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"firstName", "lastName"})
                            private String fullName;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer1, computer2, dto);

        assertThat(compilation).succeeded();
        // First computer should be used (first-match-wins strategy)
    }

    @Test
    void testMultipleComputersSecondHasCorrectMethod() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer1 = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations1",
                """
                        package io.github.cyfko.example;

                        public class UserComputations1 {
                            // Wrong method name
                            public static String computeFullName(String firstName, String lastName) {
                                return firstName + " " + lastName;
                            }
                        }
                        """);

        JavaFileObject computer2 = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations2",
                """
                        package io.github.cyfko.example;

                        public class UserComputations2 {
                            // Correct method
                            public static String getFullName(String firstName, String lastName) {
                                return firstName + " " + lastName;
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {
                                @Provider(UserComputations1.class),
                                @Provider(UserComputations2.class)
                            }
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"firstName", "lastName"})
                            private String fullName;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer1, computer2, dto);

        assertThat(compilation).succeeded();
        // Second computer should be used since first doesn't have matching method
    }

    // ==================== Test Category 7: Valid Computations ====================

    @Test
    void testValidStaticComputation() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            public static String getFullName(String firstName, String lastName) {
                                return firstName + " " + lastName;
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(UserComputations.class)}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"firstName", "lastName"})
                            private String fullName;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).succeeded();
    }

    @Test
    void testValidInstanceComputation() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            // Instance method (for bean-based resolution)
                            public String getFullName(String firstName, String lastName) {
                                return firstName + " " + lastName;
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(value = UserComputations.class, bean = "userComputations")}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"firstName", "lastName"})
                            private String fullName;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).succeeded();
    }

    @Test
    void testValidComputationWithSingleDependency() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;
                        import java.time.LocalDate;

                        public class UserComputations {
                            public static Integer getAge(LocalDate birthDate) {
                                return java.time.Period.between(birthDate, java.time.LocalDate.now()).getYears();
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(UserComputations.class)}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"birthDate"})
                            private Integer age;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).succeeded();
    }

    @Test
    void testValidComputationWithMultipleDependencies() {
        JavaFileObject entity = createProductEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.ProductComputations",
                """
                        package io.github.cyfko.example;
                        import java.math.BigDecimal;

                        public class ProductComputations {
                            public static BigDecimal getTotalValue(BigDecimal price, Integer quantity) {
                                return price.multiply(BigDecimal.valueOf(quantity));
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.ProductDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;
                        import java.math.BigDecimal;

                        @Projection(
                            from = Product.class,
                            providers = {@Provider(ProductComputations.class)}
                        )
                        public class ProductDTO {
                            @Computed(dependsOn = {"price", "quantity"})
                            private BigDecimal totalValue;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).succeeded();
    }

    // ==================== Test Category 8: Multiple Computed Fields
    // ====================

    @Test
    void testMultipleComputedFieldsInSameDTO() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;
                        import java.time.LocalDate;

                        public class UserComputations {
                            public static String getFullName(String firstName, String lastName) {
                                return firstName + " " + lastName;
                            }

                            public static Integer getAge(LocalDate birthDate) {
                                return java.time.Period.between(birthDate, java.time.LocalDate.now()).getYears();
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(UserComputations.class)}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"firstName", "lastName"})
                            private String fullName;

                            @Computed(dependsOn = {"birthDate"})
                            private Integer age;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).succeeded();
    }

    @Test
    void testMultipleComputedFieldsWithDifferentComputers() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer1 = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.NameComputations",
                """
                        package io.github.cyfko.example;

                        public class NameComputations {
                            public static String getFullName(String firstName, String lastName) {
                                return firstName + " " + lastName;
                            }
                        }
                        """);

        JavaFileObject computer2 = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.AgeComputations",
                """
                        package io.github.cyfko.example;
                        import java.time.LocalDate;

                        public class AgeComputations {
                            public static Integer getAge(LocalDate birthDate) {
                                return java.time.Period.between(birthDate, java.time.LocalDate.now()).getYears();
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {
                                @Provider(NameComputations.class),
                                @Provider(AgeComputations.class)
                            }
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"firstName", "lastName"})
                            private String fullName;

                            @Computed(dependsOn = {"birthDate"})
                            private Integer age;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer1, computer2, dto);

        assertThat(compilation).succeeded();
    }

    // ==================== Test Category 9: Different Return Types
    // ====================

    @Test
    void testComputationReturningPrimitive() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            public static int getAge(java.time.LocalDate birthDate) {
                                return java.time.Period.between(birthDate, java.time.LocalDate.now()).getYears();
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(UserComputations.class)}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"birthDate"})
                            private int age;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).succeeded();
    }

    @Test
    void testComputationReturningBigDecimal() {
        JavaFileObject entity = createProductEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.ProductComputations",
                """
                        package io.github.cyfko.example;
                        import java.math.BigDecimal;

                        public class ProductComputations {
                            public static BigDecimal getDiscountedPrice(BigDecimal price) {
                                return price.multiply(BigDecimal.valueOf(0.9));
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.ProductDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;
                        import java.math.BigDecimal;

                        @Projection(
                            from = Product.class,
                            providers = {@Provider(ProductComputations.class)}
                        )
                        public class ProductDTO {
                            @Computed(dependsOn = {"price"})
                            private BigDecimal discountedPrice;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).succeeded();
    }

    @Test
    void testComputationReturningLocalDateTime() {
        JavaFileObject entity = createOrderEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.OrderComputations",
                """
                        package io.github.cyfko.example;
                        import java.time.LocalDateTime;

                        public class OrderComputations {
                            public static LocalDateTime getProcessedAt(LocalDateTime createdAt) {
                                return createdAt.plusHours(1);
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.OrderDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;
                        import java.time.LocalDateTime;

                        @Projection(
                            from = Order.class,
                            providers = {@Provider(OrderComputations.class)}
                        )
                        public class OrderDTO {
                            @Computed(dependsOn = {"createdAt"})
                            private LocalDateTime processedAt;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).succeeded();
    }

    // ==================== Test Category 10: Empty Dependencies
    // ====================

    @Test
    void testComputedFieldWithEmptyDependencies() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(from = User.class)
                        public class UserDTO {
                            @Computed(dependsOn = {})
                            private String computed;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, dto);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("does not declare any dependency");
    }

    // ==================== Test Category 11: Non-existent Computer Class
    // ====================

    @Test
    void testNonExistentComputerClass() {
        JavaFileObject entity = createUserEntity();

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(NonExistentComputer.class)}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"firstName", "lastName"})
                            private String fullName;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, dto);

        assertThat(compilation).failed();
        // Should fail because computer class doesn't exist
    }

    // ==================== Test Category 12: Generated Code Verification
    // ====================

    @Test
    void testGeneratedCodeContainsComputationProviders() throws IOException {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            public static String getFullName(String firstName, String lastName) {
                                return firstName + " " + lastName;
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(UserComputations.class)}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"firstName", "lastName"})
                            private String fullName;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).succeeded();

        String generatedCode = compilation
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.ProjectionRegistryProviderImpl")
                .orElseThrow(() -> new AssertionError("Generated projection provider not found"))
                .getCharContent(true)
                .toString();

        // Verify computation provider is registered
        assert generatedCode.contains("UserComputations.class");
        assert generatedCode.contains("ComputationProvider");
        assert generatedCode.contains("fullName");
        assert generatedCode.contains("firstName");
        assert generatedCode.contains("lastName");
    }

    @Test
    void testGeneratedCodeContainsBeanName() throws IOException {
        JavaFileObject entity = createUserEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            public String getFullName(String firstName, String lastName) {
                                return firstName + " " + lastName;
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(value = UserComputations.class, bean = "userComputationsBean")}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"firstName", "lastName"})
                            private String fullName;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, computer, dto);

        assertThat(compilation).succeeded();

        String generatedCode = compilation
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.ProjectionRegistryProviderImpl")
                .orElseThrow(() -> new AssertionError("Generated projection provider not found"))
                .getCharContent(true)
                .toString();

        // Verify bean name is included
        assert generatedCode.contains("userComputationsBean");
        assert generatedCode.contains("UserComputations.class");
        assert generatedCode
                .contains(
                        "new ComputedField(\"fullName\", new String[]{\"firstName\", \"lastName\"}, new ComputedField.ReducerMapping[]{}");
    }
}
