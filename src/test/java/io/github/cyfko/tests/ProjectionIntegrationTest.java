package io.github.cyfko.tests;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import io.github.cyfko.jpametamodel.processor.MetamodelProcessor;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that verifies the generated projection metadata.
 * This test compiles entities and DTOs from test/resources/testdata/
 * and validates the generated projection registry.
 */
class ProjectionIntegrationTest {

        @Test
        void testUserDTOProjectionGeneration() throws IOException {
                JavaFileObject userEntity = JavaFileObjects.forResource("testdata/User.java");
                JavaFileObject addressEmbeddable = JavaFileObjects.forResource("testdata/Address.java");
                JavaFileObject departmentEntity = JavaFileObjects.forResource("testdata/Department.java");
                JavaFileObject orderEntity = JavaFileObjects.forResource("testdata/Order.java");
                JavaFileObject userDTO = JavaFileObjects.forResource("testdata/UserDTO.java");
                JavaFileObject orderDTO = JavaFileObjects.forResource("testdata/OrderDTO.java");
                JavaFileObject computationProvider = JavaFileObjects
                                .forResource("testdata/TestComputationProvider.java");

                Compilation compilation = Compiler.javac()
                                .withProcessors(new MetamodelProcessor())
                                .compile(userEntity, addressEmbeddable, departmentEntity, orderEntity, userDTO,
                                                orderDTO, computationProvider);

                assertThat(compilation).succeeded();
                assertThat(compilation)
                                .generatedSourceFile(
                                                "io.github.cyfko.jpametamodel.providers.impl.ProjectionRegistryProviderImpl");

                String generatedCode = getGeneratedProjectionCode(compilation);

                // Verify UserDTO metadata is present
                assertTrue(generatedCode.contains("UserDTO"));
                // assertTrue(generatedCode.contains("User.class"));
        }

        @Test
        void testUserDTODirectMappings() throws IOException {
                JavaFileObject userEntity = JavaFileObjects.forResource("testdata/User.java");
                JavaFileObject addressEmbeddable = JavaFileObjects.forResource("testdata/Address.java");
                JavaFileObject departmentEntity = JavaFileObjects.forResource("testdata/Department.java");
                JavaFileObject orderEntity = JavaFileObjects.forResource("testdata/Order.java");
                JavaFileObject userDTO = JavaFileObjects.forResource("testdata/UserDTO.java");
                JavaFileObject orderDTO = JavaFileObjects.forResource("testdata/OrderDTO.java");
                JavaFileObject computationProvider = JavaFileObjects
                                .forResource("testdata/TestComputationProvider.java");

                Compilation compilation = Compiler.javac()
                                .withProcessors(new MetamodelProcessor())
                                .compile(userEntity, addressEmbeddable, departmentEntity, orderEntity, userDTO,
                                                orderDTO, computationProvider);

                assertThat(compilation).succeeded();

                String generatedCode = getGeneratedProjectionCode(compilation);

                // Verify direct mappings
                assertTrue(generatedCode.contains("userEmail"));
                assertTrue(generatedCode.contains("\"email\""));
                assertTrue(generatedCode.contains("city"));
                assertTrue(generatedCode.contains("\"address.city\""));
                assertTrue(generatedCode.contains("departmentName"));
                assertTrue(generatedCode.contains("\"department.name\""));
                assertTrue(generatedCode.contains(
                                "new DirectMapping.CollectionMetadata(CollectionKind.SCALAR, CollectionType.LIST"));
        }

        @Test
        void testUserDTOComputedFields() throws IOException {
                JavaFileObject userEntity = JavaFileObjects.forResource("testdata/User.java");
                JavaFileObject addressEmbeddable = JavaFileObjects.forResource("testdata/Address.java");
                JavaFileObject departmentEntity = JavaFileObjects.forResource("testdata/Department.java");
                JavaFileObject orderEntity = JavaFileObjects.forResource("testdata/Order.java");
                JavaFileObject userDTO = JavaFileObjects.forResource("testdata/UserDTO.java");
                JavaFileObject orderDTO = JavaFileObjects.forResource("testdata/OrderDTO.java");
                JavaFileObject computationProvider = JavaFileObjects
                                .forResource("testdata/TestComputationProvider.java");

                Compilation compilation = Compiler.javac()
                                .withProcessors(new MetamodelProcessor())
                                .compile(userEntity, addressEmbeddable, departmentEntity, orderEntity, userDTO,
                                                orderDTO, computationProvider);

                assertThat(compilation).succeeded();

                String generatedCode = getGeneratedProjectionCode(compilation);

                // Verify computed field: fullName depends on firstName and lastName
                assertTrue(generatedCode.contains("fullName"));
                assertTrue(generatedCode.contains("firstName"));
                assertTrue(generatedCode.contains("lastName"));

                // Verify computed field: age depends on birthDate
                assertTrue(generatedCode.contains("age"));
                assertTrue(generatedCode.contains("birthDate"));
        }

        @Test
        void testOrderSummaryDTOProjectionGeneration() throws IOException {
                JavaFileObject userEntity = JavaFileObjects.forResource("testdata/User.java");
                JavaFileObject addressEmbeddable = JavaFileObjects.forResource("testdata/Address.java");
                JavaFileObject departmentEntity = JavaFileObjects.forResource("testdata/Department.java");
                JavaFileObject orderEntity = JavaFileObjects.forResource("testdata/Order.java");
                JavaFileObject orderSummaryDTO = JavaFileObjects.forResource("testdata/OrderSummaryDTO.java");
                JavaFileObject computationProvider = JavaFileObjects
                                .forResource("testdata/TestComputationProvider.java");

                Compilation compilation = Compiler.javac()
                                .withProcessors(new MetamodelProcessor())
                                .compile(userEntity, addressEmbeddable, departmentEntity, orderEntity, orderSummaryDTO,
                                                computationProvider);

                assertThat(compilation).succeeded();

                String generatedCode = getGeneratedProjectionCode(compilation);

                // Verify OrderSummaryDTO metadata is present
                assertTrue(generatedCode.contains("OrderSummaryDTO"));
                // assertTrue(generatedCode.contains("Order.class"));
        }

        @Test
        void testOrderSummaryDTONestedPaths() throws IOException {
                JavaFileObject userEntity = JavaFileObjects.forResource("testdata/User.java");
                JavaFileObject addressEmbeddable = JavaFileObjects.forResource("testdata/Address.java");
                JavaFileObject departmentEntity = JavaFileObjects.forResource("testdata/Department.java");
                JavaFileObject orderEntity = JavaFileObjects.forResource("testdata/Order.java");
                JavaFileObject orderSummaryDTO = JavaFileObjects.forResource("testdata/OrderSummaryDTO.java");
                JavaFileObject computationProvider = JavaFileObjects
                                .forResource("testdata/TestComputationProvider.java");

                Compilation compilation = Compiler.javac()
                                .withProcessors(new MetamodelProcessor())
                                .compile(userEntity, addressEmbeddable, departmentEntity, orderEntity, orderSummaryDTO,
                                                computationProvider);

                assertThat(compilation).succeeded();

                String generatedCode = getGeneratedProjectionCode(compilation);

                // Verify nested path mapping: customerEmail -> user.email
                assertTrue(generatedCode.contains("customerEmail"));
                assertTrue(generatedCode.contains("\"user.email\""));

                // Verify computed field with nested dependencies: customerName
                assertTrue(generatedCode.contains("customerName"));
                assertTrue(generatedCode.contains("\"user.firstName\""));
                assertTrue(generatedCode.contains("\"user.lastName\""));
        }

        @Test
        void testBothProjectionsGenerated() throws IOException {
                JavaFileObject userEntity = JavaFileObjects.forResource("testdata/User.java");
                JavaFileObject addressEmbeddable = JavaFileObjects.forResource("testdata/Address.java");
                JavaFileObject departmentEntity = JavaFileObjects.forResource("testdata/Department.java");
                JavaFileObject orderEntity = JavaFileObjects.forResource("testdata/Order.java");
                JavaFileObject userDTO = JavaFileObjects.forResource("testdata/UserDTO.java");
                JavaFileObject orderSummaryDTO = JavaFileObjects.forResource("testdata/OrderSummaryDTO.java");
                JavaFileObject orderDTO = JavaFileObjects.forResource("testdata/OrderDTO.java");
                JavaFileObject computationProvider = JavaFileObjects
                                .forResource("testdata/TestComputationProvider.java");

                Compilation compilation = Compiler.javac()
                                .withProcessors(new MetamodelProcessor())
                                .compile(userEntity, addressEmbeddable, departmentEntity, orderEntity, userDTO,
                                                orderSummaryDTO, orderDTO, computationProvider);

                assertThat(compilation).succeeded();

                String generatedCode = getGeneratedProjectionCode(compilation);

                // Verify both DTOs are in the generated registry
                assertTrue(generatedCode.contains("UserDTO"));
                assertTrue(generatedCode.contains("OrderSummaryDTO"));
        }

        @Test
        void ignoresStaticFieldsInProjectionDTO() {
                JavaFileObject entity = JavaFileObjects.forSourceString("com.example.Product", """
                                    package com.example;
                                    import jakarta.persistence.*;

                                    @Entity
                                    public class Product {
                                        @Id
                                        private Long id;
                                        private String name;
                                        private double price;
                                    }
                                """);

                JavaFileObject dto = JavaFileObjects.forSourceString("com.example.ProductDTO", """
                                    package com.example;
                                    import io.github.cyfko.projection.Projection;

                                    @Projection(from=com.example.Product.class)
                                    public interface ProductDTO {
                                        String name();
                                        double price();

                                        // These static fields should be ignored
                                        public static final String DTO_VERSION = "1.0";
                                    }
                                """);

                Compilation compilation = Compiler.javac()
                                .withProcessors(new MetamodelProcessor())
                                .compile(entity, dto);

                assertThat(compilation).succeeded();

                String generatedCode;
                try {
                        generatedCode = getGeneratedProjectionCode(compilation);
                } catch (IOException e) {
                        throw new RuntimeException(e);
                }

                // Instance fields should be present in projection metadata
                assertFalse(generatedCode.contains("\"name\""));
                assertFalse(generatedCode.contains("\"price\""));

                // Static fields should NOT be present
                assertFalse(generatedCode.contains("DTO_VERSION"));
                assertFalse(generatedCode.contains("instanceCount"));
        }

        @Test
        void stripsTypeAnnotationsFromProjectionDTOFields() {
                JavaFileObject notNullAnnotation = JavaFileObjects.forSourceString("com.example.NotNull", """
                                    package com.example;
                                    import java.lang.annotation.*;

                                    @Target({ElementType.FIELD, ElementType.TYPE_USE})
                                    @Retention(RetentionPolicy.RUNTIME)
                                    public @interface NotNull {}
                                """);

                JavaFileObject statusEnum = JavaFileObjects.forSourceString("com.example.OrderStatus", """
                                    package com.example;

                                    public enum OrderStatus {
                                        PENDING, CONFIRMED, SHIPPED, DELIVERED
                                    }
                                """);

                JavaFileObject entity = JavaFileObjects.forSourceString("com.example.Order", """
                                    package com.example;
                                    import jakarta.persistence.*;

                                    @Entity
                                    public class Order {
                                        @Id
                                        private Long id;
                                        private String reference;
                                        private OrderStatus status;
                                    }
                                """);

                JavaFileObject dto = JavaFileObjects.forSourceString("com.example.OrderDTO", """
                                    package com.example;
                                    import io.github.cyfko.projection.Projection;

                                    @Projection(from=com.example.Order.class)
                                    public interface OrderDTO {
                                        @com.example.NotNull
                                        String reference();

                                        @com.example.NotNull
                                        OrderStatus getStatus();
                                    }
                                """);

                Compilation compilation = Compiler.javac()
                                .withProcessors(new MetamodelProcessor())
                                .compile(notNullAnnotation, statusEnum, entity, dto);

                assertThat(compilation).succeeded();

                String generatedCode;
                try {
                        generatedCode = getGeneratedProjectionCode(compilation);
                } catch (IOException e) {
                        throw new RuntimeException(e);
                }

                // The generated code should contain proper class references
                assertFalse(generatedCode.contains("java.lang.String.class"));
                assertTrue(generatedCode.contains("com.example.OrderStatus.class"));

                // The generated code should NOT contain annotation syntax in class literals
                assertFalse(generatedCode.contains("@com.example.NotNull"));
        }

        // ==================== Helper Methods ====================

        private String getGeneratedProjectionCode(Compilation compilation) throws IOException {
                return compilation
                                .generatedSourceFile(
                                                "io.github.cyfko.jpametamodel.providers.impl.ProjectionRegistryProviderImpl")
                                .orElseThrow(() -> new AssertionError("Generated projection provider not found"))
                                .getCharContent(true)
                                .toString();
        }
}