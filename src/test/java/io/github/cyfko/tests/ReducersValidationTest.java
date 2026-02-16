package io.github.cyfko.tests;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import io.github.cyfko.jpametamodel.api.ComputedField;
import io.github.cyfko.jpametamodel.processor.MetamodelProcessor;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for @Computed reducers validation and code generation.
 * <p>
 * Each test includes proper computation providers since we're testing
 * reducer validation logic, not computation method resolution.
 */
class ReducersValidationTest {

    // ========== Entity definitions ==========

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

                            @Embedded
                            private Address headquarters;

                            @OneToMany(mappedBy = "company")
                            private List<Department> departments;
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
                        import java.math.BigDecimal;

                        @Entity
                        public class Department {
                            @Id
                            private Long id;
                            private String name;
                            private BigDecimal budget;

                            @ManyToOne
                            private Company company;

                            @OneToMany(mappedBy = "department")
                            private List<Employee> employees;
                        }
                        """);
    }

    private JavaFileObject createEmployeeEntity() {
        return JavaFileObjects.forSourceString(
                "io.github.cyfko.example.Employee",
                """
                        package io.github.cyfko.example;
                        import jakarta.persistence.*;
                        import java.math.BigDecimal;

                        @Entity
                        public class Employee {
                            @Id
                            private Long id;
                            private String name;
                            private BigDecimal salary;

                            @ManyToOne
                            private Department department;
                        }
                        """);
    }

    private JavaFileObject createAddressEmbeddable() {
        return JavaFileObjects.forSourceString(
                "io.github.cyfko.example.Address",
                """
                        package io.github.cyfko.example;
                        import jakarta.persistence.*;

                        @Embeddable
                        public class Address {
                            private String city;
                            private String country;
                        }
                        """);
    }

    private JavaFileObject createCompanyComputers() {
        return JavaFileObjects.forSourceString(
                "io.github.cyfko.example.CompanyComputers",
                """
                        package io.github.cyfko.example;
                        import java.math.BigDecimal;

                        public class CompanyComputers {
                            // For single collection dependency
                            public static BigDecimal toTotalBudget(BigDecimal budget) {
                                return budget;
                            }

                            // For multiple collection dependencies
                            public static BigDecimal toTotalCost(BigDecimal budget, BigDecimal salary) {
                                return budget.add(salary);
                            }

                            public static BigDecimal toStats(BigDecimal budget, BigDecimal salary) {
                                return budget.add(salary);
                            }

                            // For mixed scalar + collection
                            public static String toSummary(String name, BigDecimal budget) {
                                return name + ": " + budget;
                            }

                            // For scalar nested path
                            public static String toLocation(String city, String country) {
                                return city + ", " + country;
                            }
                        }
                        """);
    }

    // ========== Tests: Collection dependency requires reducer ==========

    @Test
    void testCollectionDependencyWithoutReducer_fails() {
        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.CompanyDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;
                        import java.math.BigDecimal;

                        @Projection(from = Company.class, providers = @Provider(CompanyComputers.class))
                        public interface CompanyDTO {
                            @Computed(dependsOn = {"departments.budget"})
                            BigDecimal getTotalBudget();
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(createCompanyEntity(), createDepartmentEntity(), createEmployeeEntity(),
                        createAddressEmbeddable(), createCompanyComputers(), dto);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("reducers count (0) must match collection dependency count (1)");
    }

    @Test
    void testCollectionDependencyWithReducer_succeeds() {
        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.CompanyDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;
                        import java.math.BigDecimal;

                        @Projection(from = Company.class, providers = @Provider(CompanyComputers.class))
                        public interface CompanyDTO {
                            @Computed(dependsOn = {"departments.budget"}, reducers = {"SUM"})
                            BigDecimal getTotalBudget();
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(createCompanyEntity(), createDepartmentEntity(), createEmployeeEntity(),
                        createAddressEmbeddable(), createCompanyComputers(), dto);

        assertThat(compilation).succeeded();
    }

    // ========== Tests: Reducer count mismatch ==========

    @Test
    void testMultipleCollectionDeps_reducerCountMismatch_fails() {
        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.CompanyDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;
                        import java.math.BigDecimal;

                        @Projection(from = Company.class, providers = @Provider(CompanyComputers.class))
                        public interface CompanyDTO {
                            @Computed(
                                dependsOn = {"departments.budget", "departments.employees.salary"},
                                reducers = {"SUM"}
                            )
                            BigDecimal getTotalCost();
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(createCompanyEntity(), createDepartmentEntity(), createEmployeeEntity(),
                        createAddressEmbeddable(), createCompanyComputers(), dto);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("reducers count (1) must match collection dependency count (2)");
    }

    @Test
    void testMultipleCollectionDeps_correctReducerCount_succeeds() {
        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.CompanyDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;
                        import java.math.BigDecimal;

                        @Projection(from = Company.class, providers = @Provider(CompanyComputers.class))
                        public interface CompanyDTO {
                            @Computed(
                                dependsOn = {"departments.budget", "departments.employees.salary"},
                                reducers = {"SUM", "AVG"}
                            )
                            BigDecimal getTotalCost();
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(createCompanyEntity(), createDepartmentEntity(), createEmployeeEntity(),
                        createAddressEmbeddable(), createCompanyComputers(), dto);

        assertThat(compilation).succeeded();
    }

    // ========== Tests: Scalar nested path (no reducer needed) ==========

    @Test
    void testScalarNestedPath_noReducerNeeded() {
        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.CompanyDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(from = Company.class, providers = @Provider(CompanyComputers.class))
                        public interface CompanyDTO {
                            @Computed(dependsOn = {"headquarters.city", "headquarters.country"})
                            String getLocation();
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(createCompanyEntity(), createDepartmentEntity(), createEmployeeEntity(),
                        createAddressEmbeddable(), createCompanyComputers(), dto);

        // Scalar nested paths (via @Embedded) don't require reducers
        assertThat(compilation).succeeded();
    }

    // ========== Tests: Mixed scalar and collection dependencies ==========

    @Test
    void testMixedDeps_onlyCollectionNeedsReducer() {
        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.CompanyDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;
                        import java.math.BigDecimal;

                        @Projection(from = Company.class, providers = @Provider(CompanyComputers.class))
                        public interface CompanyDTO {
                            @Computed(
                                dependsOn = {"name", "departments.budget"},
                                reducers = {"SUM"}
                            )
                            String getSummary();
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(createCompanyEntity(), createDepartmentEntity(), createAddressEmbeddable(),
                        createCompanyComputers(), dto, createEmployeeEntity());

        // 1 scalar (name) + 1 collection (departments.budget) = 1 reducer needed
        assertThat(compilation).succeeded();
    }

    // ========== Tests: Generated code contains reducers ==========

    @Test
    void testGeneratedCodeContainsReducers() throws IOException {
        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.CompanyDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;
                        import java.math.BigDecimal;

                        @Projection(from = Company.class, providers = @Provider(CompanyComputers.class))
                        public interface CompanyDTO {
                            @Computed(dependsOn = {"departments.budget"}, reducers = {"SUM"})
                            BigDecimal getTotalBudget();
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(createCompanyEntity(), createDepartmentEntity(), createAddressEmbeddable(),
                        createCompanyComputers(), dto, createEmployeeEntity());

        assertThat(compilation).succeeded();

        JavaFileObject generatedFile = compilation
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.ProjectionRegistryProviderImpl")
                .orElseThrow();

        String generatedCode = generatedFile.getCharContent(true).toString();

        // Verify reducers are in generated code
        assertThat(generatedCode).contains("new ComputedField.ReducerMapping");
        assertThat(generatedCode).contains("\"SUM\"");
        assertThat(generatedCode).contains("departments.budget");
    }

    @Test
    void testGeneratedCodeWithMultipleReducers() throws IOException {
        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.CompanyDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;
                        import java.math.BigDecimal;

                        @Projection(from = Company.class, providers = @Provider(CompanyComputers.class))
                        public interface CompanyDTO {
                            @Computed(
                                dependsOn = {"departments.budget", "departments.employees.salary"},
                                reducers = {"SUM", "AVG"}
                            )
                            BigDecimal getStats();
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(createCompanyEntity(), createDepartmentEntity(), createEmployeeEntity(),
                        createAddressEmbeddable(), createCompanyComputers(), dto);

        assertThat(compilation).succeeded();

        JavaFileObject generatedFile = compilation
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.ProjectionRegistryProviderImpl")
                .orElseThrow();

        String generatedCode = generatedFile.getCharContent(true).toString();

        // Verify both reducers are in generated code
        assertThat(generatedCode).contains("\"SUM\"");
        assertThat(generatedCode).contains("\"AVG\"");
    }

    // ========== Tests: ComputedField hasReducers method ==========

    @Test
    void testHasReducers_withReducers_returnsTrue() {
        var field = new ComputedField(
                "total", new String[] { "orders.amount" },
                new ComputedField.ReducerMapping[] {
                        new ComputedField.ReducerMapping(0, "SUM")
                });
        assertThat(field.hasReducers()).isTrue();
    }

    @Test
    void testHasReducers_withoutReducers_returnsFalse() {
        var field = new ComputedField(
                "fullName", new String[] { "firstName", "lastName" });
        assertThat(field.hasReducers()).isFalse();
    }

    @Test
    void testHasReducers_emptyReducers_returnsFalse() {
        var field = new ComputedField(
                "fullName", new String[] { "firstName" },
                new ComputedField.ReducerMapping[] {});
        assertThat(field.hasReducers()).isFalse();
    }

    @Test
    void testReducerMappingContent() {
        var field = new ComputedField(
                "stats", new String[] { "name", "a.b", "c.d" },
                new ComputedField.ReducerMapping[] {
                        new ComputedField.ReducerMapping(1,
                                "SUM"),
                        new ComputedField.ReducerMapping(2, "AVG")
                });
        assertThat(field.reducers()).hasSize(2);
        assertThat(field.reducers()[0].dependencyIndex()).isEqualTo(1);
        assertThat(field.reducers()[0].reducer()).isEqualTo("SUM");
        assertThat(field.reducers()[1].dependencyIndex()).isEqualTo(2);
        assertThat(field.reducers()[1].reducer()).isEqualTo("AVG");
    }
}
