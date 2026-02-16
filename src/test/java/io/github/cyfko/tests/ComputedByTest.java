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
}
