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

class MetamodelProcessorTest {

    @Test
    void testSuccessfulCompilation() {
        JavaFileObject userEntity = JavaFileObjects.forResource("testdata/User.java");
        JavaFileObject addressEmbeddable = JavaFileObjects.forResource("testdata/Address.java");
        JavaFileObject departmentEntity = JavaFileObjects.forResource("testdata/Department.java");
        JavaFileObject orderEntity = JavaFileObjects.forResource("testdata/Order.java");
        JavaFileObject userDTO = JavaFileObjects.forResource("testdata/UserDTO.java");
        JavaFileObject orderDTO = JavaFileObjects.forResource("testdata/OrderDTO.java");
        JavaFileObject computationProvider = JavaFileObjects.forResource("testdata/TestComputationProvider.java");

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .withOptions(Shared.compilerOptions)
                .compile(userEntity, addressEmbeddable, departmentEntity, orderEntity, userDTO, orderDTO, computationProvider);

        assertThat(compilation).succeeded();
        assertThat(compilation).hadNoteContaining("Phase 1: Entity Metadata Extraction");
        assertThat(compilation).hadNoteContaining("Phase 2: Projection Processing");
    }

    @Test
    void testGeneratedEntityProviderExists() throws IOException {
        JavaFileObject userEntity = JavaFileObjects.forResource("testdata/User.java");
        JavaFileObject addressEmbeddable = JavaFileObjects.forResource("testdata/Address.java");
        JavaFileObject departmentEntity = JavaFileObjects.forResource("testdata/Department.java");
        JavaFileObject orderEntity = JavaFileObjects.forResource("testdata/Order.java");
        JavaFileObject userDTO = JavaFileObjects.forResource("testdata/UserDTO.java");
        JavaFileObject orderDTO = JavaFileObjects.forResource("testdata/OrderDTO.java");
        JavaFileObject orderSummaryDTO = JavaFileObjects.forResource("testdata/OrderSummaryDTO.java");
        JavaFileObject computationProvider = JavaFileObjects.forResource("testdata/TestComputationProvider.java");

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .withOptions(Shared.compilerOptions)
                .compile(userEntity, addressEmbeddable, departmentEntity, orderEntity, userDTO, orderDTO, orderSummaryDTO, computationProvider);

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl");

        String generatedCode = compilation
                .generatedSourceFile("io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .get()
                .getCharContent(true)
                .toString();

        assertTrue(generatedCode.contains("PersistenceRegistryProvider"));
    }

    @Test
    void testGeneratedProjectionProviderExists() throws IOException {
        JavaFileObject userEntity = JavaFileObjects.forResource("testdata/User.java");
        JavaFileObject addressEmbeddable = JavaFileObjects.forResource("testdata/Address.java");
        JavaFileObject departmentEntity = JavaFileObjects.forResource("testdata/Department.java");
        JavaFileObject orderEntity = JavaFileObjects.forResource("testdata/Order.java");
        JavaFileObject userDTO = JavaFileObjects.forResource("testdata/UserDTO.java");
        JavaFileObject orderDTO = JavaFileObjects.forResource("testdata/OrderDTO.java");
        JavaFileObject computationProvider = JavaFileObjects.forResource("testdata/TestComputationProvider.java");

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .withOptions(Shared.compilerOptions)
                .compile(userEntity, addressEmbeddable, departmentEntity, orderEntity, userDTO, orderDTO, computationProvider);

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("io.github.cyfko.jpametamodel.providers.impl.ProjectionRegistryProviderImpl");

        String generatedCode = compilation
                .generatedSourceFile("io.github.cyfko.jpametamodel.providers.impl.ProjectionRegistryProviderImpl")
                .get()
                .getCharContent(true)
                .toString();

        assertTrue(generatedCode.contains("ProjectionRegistryProvider"));
    }

    @Test
    void testEntityMetadataGeneration() throws IOException {
        JavaFileObject userEntity = JavaFileObjects.forResource("testdata/User.java");
        JavaFileObject addressEmbeddable = JavaFileObjects.forResource("testdata/Address.java");
        JavaFileObject departmentEntity = JavaFileObjects.forResource("testdata/Department.java");
        JavaFileObject orderEntity = JavaFileObjects.forResource("testdata/Order.java");
        JavaFileObject userDTO = JavaFileObjects.forResource("testdata/UserDTO.java");
        JavaFileObject orderDTO = JavaFileObjects.forResource("testdata/OrderDTO.java");
        JavaFileObject orderSummaryDTO = JavaFileObjects.forResource("testdata/OrderSummaryDTO.java");
        JavaFileObject computationProvider = JavaFileObjects.forResource("testdata/TestComputationProvider.java");

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .withOptions(Shared.compilerOptions)
                .compile(userEntity, addressEmbeddable, departmentEntity, orderEntity, userDTO, orderDTO, orderSummaryDTO, computationProvider);

        assertThat(compilation).succeeded();

        String generatedCode = compilation
                .generatedSourceFile("io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .get()
                .getCharContent(true)
                .toString();

        // Verify entity fields are present
        assertTrue(generatedCode.contains("firstName"));
        assertTrue(generatedCode.contains("lastName"));
        assertTrue(generatedCode.contains("email"));
        assertTrue(generatedCode.contains("address"));
    }

    @Test
    void testProjectionMetadataGeneration() throws IOException {
        JavaFileObject userEntity = JavaFileObjects.forResource("testdata/User.java");
        JavaFileObject addressEmbeddable = JavaFileObjects.forResource("testdata/Address.java");
        JavaFileObject departmentEntity = JavaFileObjects.forResource("testdata/Department.java");
        JavaFileObject orderEntity = JavaFileObjects.forResource("testdata/Order.java");
        JavaFileObject userDTO = JavaFileObjects.forResource("testdata/UserDTO.java");
        JavaFileObject orderDTO = JavaFileObjects.forResource("testdata/OrderDTO.java");
        JavaFileObject computationProvider = JavaFileObjects.forResource("testdata/TestComputationProvider.java");

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .withOptions(Shared.compilerOptions)
                .compile(userEntity, addressEmbeddable, departmentEntity, orderEntity, userDTO, orderDTO, computationProvider);

        assertThat(compilation).succeeded();

        String generatedCode = compilation
                .generatedSourceFile("io.github.cyfko.jpametamodel.providers.impl.ProjectionRegistryProviderImpl")
                .get()
                .getCharContent(true)
                .toString();

        // Verify direct mappings
        assertTrue(generatedCode.contains("userEmail"));
        assertTrue(generatedCode.contains("email"));
        assertTrue(generatedCode.contains("city"));
        assertTrue(generatedCode.contains("address.city"));
        assertTrue(generatedCode.contains("java.lang.String.class"));
        assertTrue(generatedCode.contains("Long.class"));
        assertTrue(generatedCode.contains("io.github.cyfko.example.User.class"));
        assertTrue(generatedCode.contains("io.github.cyfko.example.UserDTO.class"));
        assertTrue(generatedCode.contains("io.github.cyfko.example.Order.class"));
        assertTrue(generatedCode.contains("io.github.cyfko.example.OrderDTO.class"));
        assertFalse(generatedCode.contains("io.github.cyfko.example.OrderSummaryDTO.class"));
        assertTrue(generatedCode.contains("io.github.cyfko.example.TestComputationProvider.class"));

        // Verify computed fields
        assertTrue(generatedCode.contains("fullName"));
        assertTrue(generatedCode.contains("firstName"));
        assertTrue(generatedCode.contains("lastName"));
        assertTrue(generatedCode.contains("age"));
        assertTrue(generatedCode.contains("birthDate"));
    }
}