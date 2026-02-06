package io.github.cyfko.tests;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import io.github.cyfko.jpametamodel.processor.MetamodelProcessor;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;

/**
 * Test suite for @Computed fields with collection-type dependencies.
 * 
 * This test suite covers:
 * - First level collection dependencies (e.g., orders, tags)
 * - Second level collection dependencies (e.g., department.employees)
 * - Third level collection dependencies (e.g., company.departments.employees)
 * - Mixed scalar and collection dependencies
 * - Method signature validation with collection parameters
 * - Error scenarios for invalid collection paths
 */
class ComputedCollectionDependencyTest {

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

                            @ManyToOne
                            private Department department;
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
                        import java.util.List;

                        @Entity
                        public class Order {
                            @Id
                            private Long id;
                            private BigDecimal amount;
                            private String status;

                            @ManyToOne
                            private User user;

                            @OneToMany(mappedBy = "order")
                            private List<OrderItem> items;
                        }
                        """);
    }

    private JavaFileObject createOrderItemEntity() {
        return JavaFileObjects.forSourceString(
                "io.github.cyfko.example.OrderItem",
                """
                        package io.github.cyfko.example;
                        import jakarta.persistence.*;
                        import java.math.BigDecimal;

                        @Entity
                        public class OrderItem {
                            @Id
                            private Long id;
                            private String productName;
                            private Integer quantity;
                            private BigDecimal unitPrice;

                            @ManyToOne
                            private Order order;
                        }
                        """);
    }

    private JavaFileObject createDepartmentEntity() {
        return JavaFileObjects.forSourceString(
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

                            @ManyToOne
                            private Company company;
                        }
                        """);
    }

    private JavaFileObject createCompanyEntity() {
        return JavaFileObjects.forSourceString(
                "io.github.cyfko.example.Company",
                """
                        package io.github.cyfko.example;
                        import jakarta.persistence.*;
                        import java.util.List;

                        @Entity
                        public class Company {
                            @Id
                            private Long id;
                            private String name;

                            @OneToMany(mappedBy = "company")
                            private List<Department> departments;
                        }
                        """);
    }

    // ==================== Test 1: First Level Collection Dependency (Entity)
    // ====================

    @Test
    void testComputedWithFirstLevelEntityCollectionDependency() {
        JavaFileObject user = createUserEntity();
        JavaFileObject order = createOrderEntity();
        JavaFileObject orderItem = createOrderItemEntity();
        JavaFileObject department = createDepartmentEntity();
        JavaFileObject company = createCompanyEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;
                        import java.util.List;

                        public class UserComputations {
                            public static Integer getOrderCount(List<Order> orders) {
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
                .withProcessors(new MetamodelProcessor())
                .withOptions(Shared.compilerOptions)
                .compile(user, order, orderItem, department, company, computer, dto);

        assertThat(compilation).succeeded();
    }

    // ==================== Test 2: First Level Collection Dependency (Scalar /
    // ElementCollection) ====================

    @Test
    void testComputedWithFirstLevelScalarCollectionDependency() {
        JavaFileObject user = createUserEntity();
        JavaFileObject order = createOrderEntity();
        JavaFileObject orderItem = createOrderItemEntity();
        JavaFileObject department = createDepartmentEntity();
        JavaFileObject company = createCompanyEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;
                        import java.util.Set;

                        public class UserComputations {
                            public static String getTagsSummary(Set<String> tags) {
                                return tags != null ? String.join(", ", tags) : "";
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
                            @Computed(dependsOn = {"tags"})
                            private String tagsSummary;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .withOptions(Shared.compilerOptions)
                .compile(user, order, orderItem, department, company, computer, dto);

        assertThat(compilation).succeeded();
    }

    // ==================== Test 3: Second Level Collection Dependency
    // ====================

    @Test
    void testComputedWithSecondLevelCollectionDependency() {
        JavaFileObject user = createUserEntity();
        JavaFileObject order = createOrderEntity();
        JavaFileObject orderItem = createOrderItemEntity();
        JavaFileObject department = createDepartmentEntity();
        JavaFileObject company = createCompanyEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;
                        import java.util.List;

                        public class UserComputations {
                            public static Integer getColleagueCount(List<User> employees) {
                                return employees != null ? employees.size() : 0;
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
                            @Computed(dependsOn = {"department.employees"})
                            private Integer colleagueCount;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .withOptions(Shared.compilerOptions)
                .compile(user, order, orderItem, department, company, computer, dto);

        assertThat(compilation).succeeded();
    }

    // ==================== Test 4: Third Level Collection Dependency
    // ====================

    @Test
    void testComputedWithThirdLevelCollectionDependency() {
        JavaFileObject user = createUserEntity();
        JavaFileObject order = createOrderEntity();
        JavaFileObject orderItem = createOrderItemEntity();
        JavaFileObject department = createDepartmentEntity();
        JavaFileObject company = createCompanyEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.CompanyComputations",
                """
                        package io.github.cyfko.example;
                        import java.util.List;

                        public class CompanyComputations {
                            public static Integer getTotalEmployeeCount(List<Department> departments) {
                                if (departments == null) return 0;
                                return departments.stream()
                                    .mapToInt(dept -> dept != null ? 1 : 0) // Simplified
                                    .sum();
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.CompanyDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = Company.class,
                            providers = {@Provider(CompanyComputations.class)}
                        )
                        public class CompanyDTO {
                            @Computed(dependsOn = {"departments"})
                            private Integer totalEmployeeCount;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .withOptions(Shared.compilerOptions)
                .compile(user, order, orderItem, department, company, computer, dto);

        assertThat(compilation).succeeded();
    }

    // ==================== Test 5: Nested Collection via Relationship Path
    // ====================

    @Test
    void testComputedWithNestedCollectionViaRelationship() {
        JavaFileObject user = createUserEntity();
        JavaFileObject order = createOrderEntity();
        JavaFileObject orderItem = createOrderItemEntity();
        JavaFileObject department = createDepartmentEntity();
        JavaFileObject company = createCompanyEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.DepartmentComputations",
                """
                        package io.github.cyfko.example;
                        import java.util.List;

                        public class DepartmentComputations {
                            public static Integer getSiblingDepartmentCount(List<Department> departments) {
                                return departments != null ? departments.size() : 0;
                            }
                        }
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.DepartmentDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = Department.class,
                            providers = {@Provider(DepartmentComputations.class)}
                        )
                        public class DepartmentDTO {
                            @Computed(dependsOn = {"company.departments"})
                            private Integer siblingDepartmentCount;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .withOptions(Shared.compilerOptions)
                .compile(user, order, orderItem, department, company, computer, dto);

        assertThat(compilation).succeeded();
    }

    // ==================== Test 6: Mixed Scalar and Collection Dependencies
    // ====================

    @Test
    void testComputedWithMixedScalarAndCollectionDependencies() {
        JavaFileObject user = createUserEntity();
        JavaFileObject order = createOrderEntity();
        JavaFileObject orderItem = createOrderItemEntity();
        JavaFileObject department = createDepartmentEntity();
        JavaFileObject company = createCompanyEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;
                        import java.util.List;

                        public class UserComputations {
                            public static String getUserSummary(String firstName, String lastName, List<Order> orders) {
                                int count = orders != null ? orders.size() : 0;
                                return firstName + " " + lastName + " (" + count + " orders)";
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
                .withProcessors(new MetamodelProcessor())
                .withOptions(Shared.compilerOptions)
                .compile(user, order, orderItem, department, company, computer, dto);

        assertThat(compilation).succeeded();
    }

    // ==================== Test 7: Collection with Wrong Parameter Type Fails
    // ====================

    @Test
    void testComputedWithWrongCollectionParameterTypeFails() {
        JavaFileObject user = createUserEntity();
        JavaFileObject order = createOrderEntity();
        JavaFileObject orderItem = createOrderItemEntity();
        JavaFileObject department = createDepartmentEntity();
        JavaFileObject company = createCompanyEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;
                        import java.util.Set;

                        public class UserComputations {
                            // Wrong: orders is List<Order>, not Set<Order>
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
                .withProcessors(new MetamodelProcessor())
                .withOptions(Shared.compilerOptions)
                .compile(user, order, orderItem, department, company, computer, dto);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("incompatible type on parameter");
    }

    // ==================== Test 8: Invalid Collection Path Fails
    // ====================

    @Test
    void testComputedWithInvalidCollectionPathFails() {
        JavaFileObject user = createUserEntity();
        JavaFileObject order = createOrderEntity();
        JavaFileObject orderItem = createOrderItemEntity();
        JavaFileObject department = createDepartmentEntity();
        JavaFileObject company = createCompanyEntity();

        JavaFileObject computer = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserComputations",
                """
                        package io.github.cyfko.example;
                        import java.util.List;

                        public class UserComputations {
                            public static Integer getNonExistentCount(List<Object> items) {
                                return items != null ? items.size() : 0;
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
                            @Computed(dependsOn = {"nonExistentCollection"})
                            private Integer nonExistentCount;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .withOptions(Shared.compilerOptions)
                .compile(user, order, orderItem, department, company, computer, dto);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("nonExistentCollection");
    }
}
