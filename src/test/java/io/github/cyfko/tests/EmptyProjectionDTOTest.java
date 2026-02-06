package io.github.cyfko.tests;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import io.github.cyfko.jpametamodel.processor.MetamodelProcessor;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for edge case: DTO projection with no fields defined.
 * Verifies that empty DTOs are properly registered in both entity and
 * projection registries.
 */
class EmptyProjectionDTOTest {

    @Test
    void testProjectionDTOWithNoFields_succeeds() {
        JavaFileObject user = JavaFileObjects.forSourceString(
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
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.EmptyUserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(from = User.class)
                        public class EmptyUserDTO {
                            // No fields at all - empty projection
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor()).withOptions(Shared.compilerOptions)
                .compile(user, dto);

        // Compilation should succeed
        assertThat(compilation).succeeded();
    }

    @Test
    void testProjectionDTOWithNoFields_entityIsInPersistenceRegistry() throws IOException {
        JavaFileObject user = JavaFileObjects.forSourceString(
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
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.EmptyUserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(from = User.class)
                        public class EmptyUserDTO {
                            // No fields at all
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor()).withOptions(Shared.compilerOptions)
                .compile(user, dto);

        assertThat(compilation).succeeded();

        // Verify entity is in PersistenceMetadataRegistry
        JavaFileObject persistenceRegistry = compilation.generatedSourceFile(
                "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .orElseThrow();

        String persistenceContent = persistenceRegistry.getCharContent(true).toString();

        // Entity should be present in registry
        assertThat(persistenceContent).contains("io.github.cyfko.example.User.class");
        // Entity fields should be present
        assertThat(persistenceContent).contains("\"id\"");
        assertThat(persistenceContent).contains("\"firstName\"");
        assertThat(persistenceContent).contains("\"lastName\"");
    }

    @Test
    void testProjectionDTOWithNoFields_dtoIsInProjectionRegistry() throws IOException {
        JavaFileObject user = JavaFileObjects.forSourceString(
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
                        """);

        JavaFileObject dto = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.EmptyUserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(from = User.class)
                        public class EmptyUserDTO {
                            // No fields at all
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor()).withOptions(Shared.compilerOptions)
                .compile(user, dto);

        assertThat(compilation).succeeded();

        // Verify DTO is in ProjectionMetadataRegistry
        JavaFileObject projectionRegistry = compilation.generatedSourceFile(
                "io.github.cyfko.jpametamodel.providers.impl.ProjectionRegistryProviderImpl")
                .orElseThrow();

        String projectionContent = projectionRegistry.getCharContent(true).toString();

        // DTO should be present in registry
        assertThat(projectionContent).contains("io.github.cyfko.example.EmptyUserDTO.class");
        // DTO should reference the entity
        assertThat(projectionContent).contains("io.github.cyfko.example.User.class");
        // Direct mappings should be empty
        assertThat(projectionContent).contains("new DirectMapping[]{}");
        // Computed fields should be empty
        assertThat(projectionContent).contains("new ComputedField[]{}");
    }
}
