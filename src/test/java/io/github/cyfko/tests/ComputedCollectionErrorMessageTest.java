package io.github.cyfko.tests;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import io.github.cyfko.jpametamodel.processor.MetamodelProcessor;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;

/**
 * Test suite for verifying error messages for @Computed fields with collection
 * dependencies.
 * 
 * This test suite ensures that error messages are:
 * - Clear and informative
 * - Include the full collection type (e.g., java.util.List<Order>)
 * - Provide expected method signatures
 * - List available computation providers
 */
class ComputedCollectionErrorMessageTest {

    // ==================== Test Data: Entities ====================

    private JavaFileObject createUserEntity() {
        return JavaFileObjects.forSourceString(
                "io.github.cyfko.example.User",
                """
                        package io.github.cyfko.example;
                        import jakarta.persistence.*;
                        import java.util.List;
                        import java.util.Set;

                        @Entity
                        public class User {
                            @Id
                            private Long id;
                            private String firstName;
                            private String lastName;

                            @OneToMany(mappedBy = "user")
                            private List<Order> orders;

                            @ElementCollection
                            private Set<String> tags;
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

                        @Entity
                        public class Order {
                            @Id
                            private Long id;
                            private BigDecimal amount;
                            private String status;

                            @ManyToOne
                            private User user;
                        }
                        """);
    }

    // ==================== Test 1: Wrong Collection Container Type (List vs Set)
    // ====================

    @Test
    void errorMessageForWrongCollectionContainerType() {
        JavaFileObject user = createUserEntity();
        JavaFileObject order = createOrderEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;
                        import java.util.Set;

                        public class UserComputations {
                            // Wrong: entity has List<Order>, but method expects Set<Order>
                            public static Integer getOrderCount(Set<Order> orders) {
                                return orders != null ? orders.size() : 0;
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
                            @Computed(dependsOn = {"orders"})
                            private Integer orderCount;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor()).withOptions(Shared.compilerOptions)
                .compile(user, order, computer, dto);

        assertThat(compilation).failed();
        // Verify error message contains both the required and found types with full
        // collection specification
        assertThat(compilation).hadErrorContaining("incompatible type on parameter");
        assertThat(compilation).hadErrorContaining("java.util.List<io.github.cyfko.example.Order>");
        assertThat(compilation).hadErrorContaining("java.util.Set<io.github.cyfko.example.Order>");
    }

    // ==================== Test 2: Wrong Element Type in Collection
    // ====================

    @Test
    void errorMessageForWrongElementTypeInCollection() {
        JavaFileObject user = createUserEntity();
        JavaFileObject order = createOrderEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;
                        import java.util.List;

                        public class UserComputations {
                            // Wrong: entity has List<Order>, but method expects List<String>
                            public static Integer getOrderCount(List<String> orders) {
                                return orders != null ? orders.size() : 0;
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
                            @Computed(dependsOn = {"orders"})
                            private Integer orderCount;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor()).withOptions(Shared.compilerOptions)
                .withOptions(Shared.compilerOptions)
                .compile(user, order, computer, dto);

        assertThat(compilation).failed();
        // Error should show the element type mismatch
        assertThat(compilation).hadErrorContaining("incompatible type on parameter");
        assertThat(compilation).hadErrorContaining("java.util.List<io.github.cyfko.example.Order>");
        assertThat(compilation).hadErrorContaining("java.util.List<java.lang.String>");
    }

    // ==================== Test 3: Missing Provider Method for Collection
    // Dependency ====================

    @Test
    void errorMessageForMissingProviderMethodWithCollectionDependency() {
        JavaFileObject user = createUserEntity();
        JavaFileObject order = createOrderEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;
                        import java.util.List;

                        public class UserComputations {
                            // Wrong method name: should be getOrderCount, not computeOrderCount
                            public static Integer computeOrderCount(List<Order> orders) {
                                return orders != null ? orders.size() : 0;
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
                            @Computed(dependsOn = {"orders"})
                            private Integer orderCount;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor()).withOptions(Shared.compilerOptions)
                .compile(user, order, computer, dto);

        assertThat(compilation).failed();
        // Error should indicate no matching method and show expected signature with
        // collection type
        assertThat(compilation).hadErrorContaining("No matching provider method found");
        assertThat(compilation).hadErrorContaining("orderCount");
        assertThat(compilation).hadErrorContaining("Expected computer's method");
        assertThat(compilation).hadErrorContaining("java.util.List<io.github.cyfko.example.Order>");
    }

    // ==================== Test 4: Wrong Return Type with Collection Dependency
    // ====================

    @Test
    void errorMessageForWrongReturnTypeWithCollectionDependency() {
        JavaFileObject user = createUserEntity();
        JavaFileObject order = createOrderEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;
                        import java.util.List;

                        public class UserComputations {
                            // Wrong return type: should be Integer, not String
                            public static String getOrderCount(List<Order> orders) {
                                return orders != null ? String.valueOf(orders.size()) : "0";
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
                            @Computed(dependsOn = {"orders"})
                            private Integer orderCount;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor()).withOptions(Shared.compilerOptions)
                .compile(user, order, computer, dto);

        assertThat(compilation).failed();
        // Error should show return type mismatch
        assertThat(compilation).hadErrorContaining("incompatible return type");
        assertThat(compilation).hadErrorContaining("java.lang.Integer");
        assertThat(compilation).hadErrorContaining("java.lang.String");
    }

    // ==================== Test 5: Error Message Shows Provider Class
    // ====================

    @Test
    void errorMessageIncludesProviderClassName() {
        JavaFileObject user = createUserEntity();
        JavaFileObject order = createOrderEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.MyCustomComputations",
                """
                        package io.github.cyfko.example;

                        public class MyCustomComputations {
                            // No methods at all
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(MyCustomComputations.class)}
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"orders"})
                            private Integer orderCount;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor()).withOptions(Shared.compilerOptions)
                .compile(user, order, computer, dto);

        assertThat(compilation).failed();
        // Error should include the provider class name
        assertThat(compilation).hadErrorContaining("Providers:");
        assertThat(compilation).hadErrorContaining("io.github.cyfko.example.MyCustomComputations");
    }

    // ==================== Test 6: Error Message Shows DTO Source Class
    // ====================

    @Test
    void errorMessageIncludesDtoSourceClass() {
        JavaFileObject user = createUserEntity();
        JavaFileObject order = createOrderEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            // Empty - no methods
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.MySpecialUserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class,
                            providers = {@Provider(UserComputations.class)}
                        )
                        public class MySpecialUserDTO {
                            @Computed(dependsOn = {"orders"})
                            private Integer orderCount;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor()).withOptions(Shared.compilerOptions)
                .compile(user, order, computer, dto);

        assertThat(compilation).failed();
        // Error should include the DTO source class name
        assertThat(compilation).hadErrorContaining("Source:");
        assertThat(compilation).hadErrorContaining("io.github.cyfko.example.MySpecialUserDTO");
    }

    // ==================== Test 7: Mixed Dependencies Error Shows All Types
    // ====================

    @Test
    void errorMessageForMixedDependenciesShowsAllTypes() {
        JavaFileObject user = createUserEntity();
        JavaFileObject order = createOrderEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;
                        import java.util.List;

                        public class UserComputations {
                            // Wrong: second param should be String, not Integer
                            public static String getUserSummary(String firstName, Integer lastName, List<Order> orders) {
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
                            @Computed(dependsOn = {"firstName", "lastName", "orders"})
                            private String userSummary;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor()).withOptions(Shared.compilerOptions)
                .compile(user, order, computer, dto);

        assertThat(compilation).failed();
        // Error should show the specific parameter position with type mismatch
        assertThat(compilation).hadErrorContaining("incompatible type on parameter at position 1");
        assertThat(compilation).hadErrorContaining("java.lang.String");
        assertThat(compilation).hadErrorContaining("java.lang.Integer");
    }

    // ==================== Test 8: Expected Signature Shows Collection Parameter
    // ====================

    @Test
    void expectedSignatureInErrorShowsCollectionParameter() {
        JavaFileObject user = createUserEntity();
        JavaFileObject order = createOrderEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            // Empty - no methods
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
                            @Computed(dependsOn = {"orders"})
                            private Integer orderCount;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor()).withOptions(Shared.compilerOptions)
                .compile(user, order, computer, dto);

        assertThat(compilation).failed();
        // The expected signature should show: getOrderCount(java.util.List<...Order>
        // orders)
        assertThat(compilation).hadErrorContaining("Expected computer's method:");
        assertThat(compilation).hadErrorContaining("getOrderCount");
        assertThat(compilation).hadErrorContaining("java.util.List<io.github.cyfko.example.Order> orders");
    }

    // ==================== Test 9: Parameter Names Use Last Segment for Nested
    // Paths ====================

    @Test
    void parameterNamesUseLastSegmentForNestedPaths() {
        JavaFileObject user = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.User",
                """
                        package io.github.cyfko.example;
                        import jakarta.persistence.*;
                        import java.util.List;

                        @Entity
                        public class User {
                            @Id
                            private Long id;
                            private String firstName;

                            @ManyToOne
                            private Department department;
                        }
                        """);

        JavaFileObject department = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.Department",
                """
                        package io.github.cyfko.example;
                        import jakarta.persistence.*;
                        import java.util.List;

                        @Entity
                        public class Department {
                            @Id
                            private Long id;
                            private String name;

                            @OneToMany(mappedBy = "department")
                            private List<User> employees;
                        }
                        """);

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            // Empty - no methods
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
                            @Computed(dependsOn = {"department.employees"})
                            private Integer colleagueCount;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor()).withOptions(Shared.compilerOptions)
                .compile(user, department, computer, dto);

        assertThat(compilation).failed();
        // For nested path "department.employees", parameter name should be "employees"
        // (last segment)
        assertThat(compilation).hadErrorContaining("employees");
        // Full type should include the collection type
        assertThat(compilation).hadErrorContaining("java.util.List<io.github.cyfko.example.User> employees");
    }

    // ==================== Test 10: Parameter Names for Mixed Dependencies
    // ====================

    @Test
    void parameterNamesCorrectForMixedDependencies() {
        JavaFileObject user = createUserEntity();
        JavaFileObject order = createOrderEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;

                        public class UserComputations {
                            // Empty - no methods, force error to see expected signature
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
                            @Computed(dependsOn = {"firstName", "lastName", "orders"})
                            private String userSummary;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor()).withOptions(Shared.compilerOptions)
                .compile(user, order, computer, dto);

        assertThat(compilation).failed();
        // Expected signature should show all parameter names correctly:
        // getUserSummary(String firstName, String lastName, List<Order> orders)
        assertThat(compilation).hadErrorContaining("java.lang.String firstName");
        assertThat(compilation).hadErrorContaining("java.lang.String lastName");
        assertThat(compilation).hadErrorContaining("java.util.List<io.github.cyfko.example.Order> orders");
    }
}
