package io.github.cyfko.tests;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import io.github.cyfko.jpametamodel.processor.MetamodelProcessor;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;

class ProjectionValidationTest {

    @Test
    void testInvalidEntityFieldPath() {
        JavaFileObject userEntity = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.User",
                """
                        package io.github.cyfko.example;
                        import jakarta.persistence.*;

                        @Entity
                        public class User {
                            @Id
                            private Long id;
                            private String name;
                        }
                        """);

        JavaFileObject invalidDTO = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(from = User.class)
                        public class UserDTO {
                            @Projected(from = "nonExistentField")
                            private String field;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(userEntity, invalidDTO);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Field 'nonExistentField' not found");
    }

    @Test
    void testInvalidNestedPath() {
        JavaFileObject userEntity = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.User",
                """
                        package io.github.cyfko.example;
                        import jakarta.persistence.*;

                        @Entity
                        public class User {
                            @Id
                            private Long id;
                            private String name;
                        }
                        """);

        JavaFileObject invalidDTO = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(from = User.class)
                        public class UserDTO {
                            @Projected(from = "name.something")
                            private String field;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(userEntity, invalidDTO);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Cannot navigate through scalar field");
    }

    @Test
    void testInvalidComputedDependency() {
        JavaFileObject userEntity = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.User",
                """
                        package io.github.cyfko.example;
                        import jakarta.persistence.*;

                        @Entity
                        public class User {
                            @Id
                            private Long id;
                            private String name;
                        }
                        """);

        JavaFileObject invalidDTO = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(
                            from = User.class
                        )
                        public class UserDTO {
                            @Computed(dependsOn = {"nonExistent"})
                            private String computed;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(userEntity, invalidDTO);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Field 'nonExistent' not found");
    }

    @Test
    void testEntityNotFound() {
        JavaFileObject invalidDTO = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(from = NonExistentEntity.class)
                        public class UserDTO {
                            @Projected(from = "field")
                            private String field;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(invalidDTO);

        assertThat(compilation).failed();
    }

    @Test
    void testValidEmbeddableNavigation() {
        JavaFileObject addressEmbeddable = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.Address",
                """
                        package io.github.cyfko.example;
                        import jakarta.persistence.Embeddable;

                        @Embeddable
                        public class Address {
                            private String city;
                            private String zipCode;
                        }
                        """);

        JavaFileObject userEntity = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.User",
                """
                        package io.github.cyfko.example;
                        import jakarta.persistence.*;

                        @Entity
                        public class User {
                            @Id
                            private Long id;

                            @Embedded
                            private Address address;
                        }
                        """);

        JavaFileObject validDTO = JavaFileObjects.forSourceString(
                "io.github.cyfko.example.UserDTO",
                """
                        package io.github.cyfko.example;
                        import io.github.cyfko.projection.*;

                        @Projection(from = User.class)
                        public class UserDTO {
                            @Projected(from = "address.city")
                            private String city;

                            @Projected(from = "address.zipCode")
                            private String zipCode;
                        }
                        """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(userEntity, addressEmbeddable, validDTO);

        assertThat(compilation).succeeded();
    }
}