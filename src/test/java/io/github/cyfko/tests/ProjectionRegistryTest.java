package io.github.cyfko.tests;

/**
 * Exhaustive test suite for ProjectionRegistry functionality.
 * 
 * This test suite covers:
 * - Direct field mappings (@Projected)
 * - Computed field mappings (@Computed)
 * - Path resolution (toEntityPath)
 * - Required entity fields extraction
 * - Implicit projection metadata for entities
 * - Cache functionality
 * - Error scenarios
 * - Nested paths
 * - Comma-separated paths
 * - Case sensitivity
 */
class ProjectionRegistryTest {

//    // ==================== Test Data: Entities ====================
//
//    private JavaFileObject createUserEntity() {
//        return JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.User",
//            """
//            package io.github.cyfko.example;
//            import jakarta.persistence.*;
//            import java.time.LocalDate;
//
//            @Entity
//            public class User {
//                @Id
//                private Long id;
//                private String firstName;
//                private String lastName;
//                private String email;
//                private LocalDate birthDate;
//            }
//            """
//        );
//    }
//
//    private JavaFileObject createAddressEmbeddable() {
//        return JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.Address",
//            """
//            package io.github.cyfko.example;
//            import jakarta.persistence.Embeddable;
//
//            @Embeddable
//            public class Address {
//                private String city;
//                private String street;
//                private String zipCode;
//            }
//            """
//        );
//    }
//
//    private JavaFileObject createUserWithAddressEntity() {
//        return JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserWithAddress",
//            """
//            package io.github.cyfko.example;
//            import jakarta.persistence.*;
//
//            @Entity
//            public class UserWithAddress {
//                @Id
//                private Long id;
//                private String name;
//
//                @Embedded
//                private Address address;
//            }
//            """
//        );
//    }
//
//    private JavaFileObject createComputationProvider() {
//        return JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserComputations",
//            """
//            package io.github.cyfko.example;
//            import java.time.LocalDate;
//
//            public class UserComputations {
//                public static String getFullName(String firstName, String lastName) {
//                    return firstName + " " + lastName;
//                }
//
//                public static Integer getAge(LocalDate birthDate) {
//                    return java.time.Period.between(birthDate, java.time.LocalDate.now()).getYears();
//                }
//            }
//            """
//        );
//    }
//
//    @BeforeEach
//    void setUp() {
//        // Clear cache before each test to ensure clean state
//        // Note: This requires a package-visible or public method to clear cache
//        // If not available, we'll work around it
//    }
//
//    // ==================== Test Category 1: Direct Field Mappings ====================
//
//    @Test
//    void testGetMetadataForDirectMapping() throws IOException {
//        JavaFileObject entity = createUserEntity();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(entity = User.class)
//            public class UserDTO {
//                @Projected
//                private String firstName;
//
//                @Projected
//                private String lastName;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, dto);
//
//        assertThat(compilation).succeeded();
//
//        ProjectionMetadata metadata = ProjectionRegistry.getMetadataFor(
//            loadClass("io.github.cyfko.example.UserDTO")
//        );
//
//        assertNotNull(metadata);
//        assertTrue(metadata.getDirectMapping("firstName", false).isPresent());
//        assertTrue(metadata.getDirectMapping("lastName", false).isPresent());
//        assertEquals("firstName", metadata.getDirectMapping("firstName", false).get().entityField());
//    }
//
//    @Test
//    void testDirectMappingWithRenaming() throws IOException {
//        JavaFileObject entity = createUserEntity();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(entity = User.class)
//            public class UserDTO {
//                @Projected(from = "email")
//                private String userEmail;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, dto);
//
//        assertThat(compilation).succeeded();
//
//        ProjectionMetadata metadata = ProjectionRegistry.getMetadataFor(
//            loadClass("io.github.cyfko.example.UserDTO")
//        );
//
//        assertNotNull(metadata);
//        assertTrue(metadata.getDirectMapping("userEmail", false).isPresent());
//        assertEquals("email", metadata.getDirectMapping("userEmail", false).get().entityField());
//    }
//
//    @Test
//    void testDirectMappingNestedPath() throws IOException {
//        JavaFileObject address = createAddressEmbeddable();
//        JavaFileObject entity = createUserWithAddressEntity();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserWithAddressDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(entity = UserWithAddress.class)
//            public class UserWithAddressDTO {
//                @Projected(from = "address.city")
//                private String city;
//
//                @Projected(from = "address.street")
//                private String street;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(address, entity, dto);
//
//        assertThat(compilation).succeeded();
//
//        ProjectionMetadata metadata = ProjectionRegistry.getMetadataFor(
//            loadClass("io.github.cyfko.example.UserWithAddressDTO")
//        );
//
//        assertNotNull(metadata);
//        assertTrue(metadata.getDirectMapping("city", false).isPresent());
//        assertEquals("address.city", metadata.getDirectMapping("city", false).get().entityField());
//    }
//
//    // ==================== Test Category 2: Computed Field Mappings ====================
//
//    @Test
//    void testGetMetadataForComputedField() throws IOException {
//        JavaFileObject entity = createUserEntity();
//        JavaFileObject computer = createComputationProvider();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(
//                entity = User.class,
//                computers = {@Computer(UserComputations.class)}
//            )
//            public class UserDTO {
//                @Computed(dependsOn = {"firstName", "lastName"})
//                private String fullName;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, computer, dto);
//
//        assertThat(compilation).succeeded();
//
//        ProjectionMetadata metadata = ProjectionRegistry.getMetadataFor(
//            loadClass("io.github.cyfko.example.UserDTO")
//        );
//
//        assertNotNull(metadata);
//        assertTrue(metadata.getComputedField("fullName", false).isPresent());
//        assertTrue(metadata.isComputedField("fullName", false));
//        assertFalse(metadata.isDirectMapping("fullName", false));
//
//        var computedField = metadata.getComputedField("fullName", false).get();
//        assertEquals(2, computedField.dependencyCount());
//        assertTrue(computedField.dependsOn("firstName"));
//        assertTrue(computedField.dependsOn("lastName"));
//    }
//
//    @Test
//    void testComputedFieldWithSingleDependency() throws IOException {
//        JavaFileObject entity = createUserEntity();
//        JavaFileObject computer = createComputationProvider();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(
//                entity = User.class,
//                computers = {@Computer(UserComputations.class)}
//            )
//            public class UserDTO {
//                @Computed(dependsOn = {"birthDate"})
//                private Integer age;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, computer, dto);
//
//        assertThat(compilation).succeeded();
//
//        ProjectionMetadata metadata = ProjectionRegistry.getMetadataFor(
//            loadClass("io.github.cyfko.example.UserDTO")
//        );
//
//        assertNotNull(metadata);
//        assertTrue(metadata.getComputedField("age", false).isPresent());
//        var computedField = metadata.getComputedField("age", false).get();
//        assertEquals(1, computedField.dependencyCount());
//        assertTrue(computedField.dependsOn("birthDate"));
//    }
//
//    @Test
//    void testComputedFieldWithNestedDependencies() throws IOException {
//        JavaFileObject address = createAddressEmbeddable();
//        JavaFileObject entity = createUserWithAddressEntity();
//
//        JavaFileObject computer = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.AddressComputations",
//            """
//            package io.github.cyfko.example;
//
//            public class AddressComputations {
//                public static String getLocation(String city, String zipCode) {
//                    return city + " " + zipCode;
//                }
//            }
//            """
//        );
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserWithAddressDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(
//                entity = UserWithAddress.class,
//                computers = {@Computer(AddressComputations.class)}
//            )
//            public class UserWithAddressDTO {
//                @Computed(dependsOn = {"address.city", "address.zipCode"})
//                private String location;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(address, entity, computer, dto);
//
//        assertThat(compilation).succeeded();
//
//        ProjectionMetadata metadata = ProjectionRegistry.getMetadataFor(
//            loadClass("io.github.cyfko.example.UserWithAddressDTO")
//        );
//
//        assertNotNull(metadata);
//        assertTrue(metadata.getComputedField("location", false).isPresent());
//        var computedField = metadata.getComputedField("location", false).get();
//        assertEquals(2, computedField.dependencyCount());
//        assertTrue(computedField.dependsOn("address.city"));
//        assertTrue(computedField.dependsOn("address.zipCode"));
//        assertTrue(computedField.hasNestedDependencies());
//    }
//
//    // ==================== Test Category 3: toEntityPath - Direct Fields ====================
//
//    @Test
//    void testToEntityPathDirectField() throws IOException {
//        JavaFileObject entity = createUserEntity();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(entity = User.class)
//            public class UserDTO {
//                @Projected
//                private String firstName;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, dto);
//
//        assertThat(compilation).succeeded();
//
//        Class<?> dtoClass = loadClass("io.github.cyfko.example.UserDTO");
//        String entityPath = ProjectionRegistry.toEntityPath("firstName", dtoClass, false);
//
//        assertEquals("firstName", entityPath);
//    }
//
//    @Test
//    void testToEntityPathDirectFieldWithRenaming() throws IOException {
//        JavaFileObject entity = createUserEntity();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(entity = User.class)
//            public class UserDTO {
//                @Projected(from = "email")
//                private String userEmail;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, dto);
//
//        assertThat(compilation).succeeded();
//
//        Class<?> dtoClass = loadClass("io.github.cyfko.example.UserDTO");
//        String entityPath = ProjectionRegistry.toEntityPath("userEmail", dtoClass, false);
//
//        assertEquals("email", entityPath);
//    }
//
//    @Test
//    void testToEntityPathNestedDirectField() throws IOException {
//        JavaFileObject address = createAddressEmbeddable();
//        JavaFileObject entity = createUserWithAddressEntity();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserWithAddressDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(entity = UserWithAddress.class)
//            public class UserWithAddressDTO {
//                @Projected(from = "address.city")
//                private String city;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(address, entity, dto);
//
//        assertThat(compilation).succeeded();
//
//        Class<?> dtoClass = loadClass("io.github.cyfko.example.UserWithAddressDTO");
//        String entityPath = ProjectionRegistry.toEntityPath("city", dtoClass, false);
//
//        assertEquals("address.city", entityPath);
//    }
//
//    // ==================== Test Category 4: toEntityPath - Computed Fields ====================
//
//    @Test
//    void testToEntityPathComputedField() throws IOException {
//        JavaFileObject entity = createUserEntity();
//        JavaFileObject computer = createComputationProvider();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(
//                entity = User.class,
//                computers = {@Computer(UserComputations.class)}
//            )
//            public class UserDTO {
//                @Computed(dependsOn = {"firstName", "lastName"})
//                private String fullName;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, computer, dto);
//
//        assertThat(compilation).succeeded();
//
//        Class<?> dtoClass = loadClass("io.github.cyfko.example.UserDTO");
//        String entityPath = ProjectionRegistry.toEntityPath("fullName", dtoClass, false);
//
//        // Computed field should resolve to its dependencies joined by dots
//        assertEquals("firstName.lastName", entityPath);
//    }
//
//    @Test
//    void testToEntityPathComputedFieldWithSingleDependency() throws IOException {
//        JavaFileObject entity = createUserEntity();
//        JavaFileObject computer = createComputationProvider();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(
//                entity = User.class,
//                computers = {@Computer(UserComputations.class)}
//            )
//            public class UserDTO {
//                @Computed(dependsOn = {"birthDate"})
//                private Integer age;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, computer, dto);
//
//        assertThat(compilation).succeeded();
//
//        Class<?> dtoClass = loadClass("io.github.cyfko.example.UserDTO");
//        String entityPath = ProjectionRegistry.toEntityPath("age", dtoClass, false);
//
//        assertEquals("birthDate", entityPath);
//    }
//
//    @Test
//    void testToEntityPathComputedFieldWithNestedDependencies() throws IOException {
//        JavaFileObject address = createAddressEmbeddable();
//        JavaFileObject entity = createUserWithAddressEntity();
//
//        JavaFileObject computer = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.AddressComputations",
//            """
//            package io.github.cyfko.example;
//
//            public class AddressComputations {
//                public static String getLocation(String city, String zipCode) {
//                    return city + " " + zipCode;
//                }
//            }
//            """
//        );
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserWithAddressDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(
//                entity = UserWithAddress.class,
//                computers = {@Computer(AddressComputations.class)}
//            )
//            public class UserWithAddressDTO {
//                @Computed(dependsOn = {"address.city", "address.zipCode"})
//                private String location;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(address, entity, computer, dto);
//
//        assertThat(compilation).succeeded();
//
//        Class<?> dtoClass = loadClass("io.github.cyfko.example.UserWithAddressDTO");
//        String entityPath = ProjectionRegistry.toEntityPath("location", dtoClass, false);
//
//        assertEquals("address.city.address.zipCode", entityPath);
//    }
//
//    @Test
//    void testToEntityPathComputedFieldMustBeTopLevel() throws IOException {
//        JavaFileObject entity = createUserEntity();
//        JavaFileObject computer = createComputationProvider();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(
//                entity = User.class,
//                computers = {@Computer(UserComputations.class)}
//            )
//            public class UserDTO {
//                @Computed(dependsOn = {"firstName", "lastName"})
//                private String fullName;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, computer, dto);
//
//        assertThat(compilation).succeeded();
//
//        Class<?> dtoClass = loadClass("io.github.cyfko.example.UserDTO");
//
//        // Computed field should work at top level
//        assertDoesNotThrow(() -> ProjectionRegistry.toEntityPath("fullName", dtoClass, false));
//
//        // But should fail if used in nested path
//        assertThrows(IllegalArgumentException.class, () ->
//            ProjectionRegistry.toEntityPath("someField.fullName", dtoClass, false)
//        );
//    }
//
//    // ==================== Test Category 5: toEntityPath - Comma-separated Paths ====================
//
//    @Test
//    void testToEntityPathCommaSeparatedDirectFields() throws IOException {
//        JavaFileObject entity = createUserEntity();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(entity = User.class)
//            public class UserDTO {
//                @Projected
//                private String firstName;
//
//                @Projected
//                private String lastName;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, dto);
//
//        assertThat(compilation).succeeded();
//
//        Class<?> dtoClass = loadClass("io.github.cyfko.example.UserDTO");
//        String entityPath = ProjectionRegistry.toEntityPath("firstName,lastName", dtoClass, false);
//
//        assertEquals("firstName,lastName", entityPath);
//    }
//
//    @Test
//    void testToEntityPathCommaSeparatedWithRenaming() throws IOException {
//        JavaFileObject entity = createUserEntity();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(entity = User.class)
//            public class UserDTO {
//                @Projected(from = "email")
//                private String userEmail;
//
//                @Projected
//                private String firstName;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, dto);
//
//        assertThat(compilation).succeeded();
//
//        Class<?> dtoClass = loadClass("io.github.cyfko.example.UserDTO");
//        String entityPath = ProjectionRegistry.toEntityPath("userEmail,firstName", dtoClass, false);
//
//        assertEquals("email,firstName", entityPath);
//    }
//
//    // ==================== Test Category 6: getRequiredEntityFields ====================
//
//    @Test
//    void testGetRequiredEntityFieldsDirectOnly() throws IOException {
//        JavaFileObject entity = createUserEntity();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(entity = User.class)
//            public class UserDTO {
//                @Projected
//                private String firstName;
//
//                @Projected
//                private String lastName;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, dto);
//
//        assertThat(compilation).succeeded();
//
//        Class<?> dtoClass = loadClass("io.github.cyfko.example.UserDTO");
//        var requiredFields = ProjectionRegistry.getRequiredEntityFields(dtoClass);
//
//        assertEquals(2, requiredFields.size());
//        assertTrue(requiredFields.contains("firstName"));
//        assertTrue(requiredFields.contains("lastName"));
//    }
//
//    @Test
//    void testGetRequiredEntityFieldsComputedOnly() throws IOException {
//        JavaFileObject entity = createUserEntity();
//        JavaFileObject computer = createComputationProvider();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(
//                entity = User.class,
//                computers = {@Computer(UserComputations.class)}
//            )
//            public class UserDTO {
//                @Computed(dependsOn = {"firstName", "lastName"})
//                private String fullName;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, computer, dto);
//
//        assertThat(compilation).succeeded();
//
//        Class<?> dtoClass = loadClass("io.github.cyfko.example.UserDTO");
//        var requiredFields = ProjectionRegistry.getRequiredEntityFields(dtoClass);
//
//        assertEquals(2, requiredFields.size());
//        assertTrue(requiredFields.contains("firstName"));
//        assertTrue(requiredFields.contains("lastName"));
//    }
//
//    @Test
//    void testGetRequiredEntityFieldsMixed() throws IOException {
//        JavaFileObject entity = createUserEntity();
//        JavaFileObject computer = createComputationProvider();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(
//                entity = User.class,
//                computers = {@Computer(UserComputations.class)}
//            )
//            public class UserDTO {
//                @Projected
//                private String email;
//
//                @Computed(dependsOn = {"firstName", "lastName"})
//                private String fullName;
//
//                @Computed(dependsOn = {"birthDate"})
//                private Integer age;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, computer, dto);
//
//        assertThat(compilation).succeeded();
//
//        Class<?> dtoClass = loadClass("io.github.cyfko.example.UserDTO");
//        var requiredFields = ProjectionRegistry.getRequiredEntityFields(dtoClass);
//
//        // Should include: email, firstName, lastName, birthDate
//        assertEquals(4, requiredFields.size());
//        assertTrue(requiredFields.contains("email"));
//        assertTrue(requiredFields.contains("firstName"));
//        assertTrue(requiredFields.contains("lastName"));
//        assertTrue(requiredFields.contains("birthDate"));
//    }
//
//    @Test
//    void testGetRequiredEntityFieldsDeduplicates() throws IOException {
//        JavaFileObject entity = createUserEntity();
//        JavaFileObject computer = createComputationProvider();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(
//                entity = User.class,
//                computers = {@Computer(UserComputations.class)}
//            )
//            public class UserDTO {
//                @Projected
//                private String firstName;
//
//                @Computed(dependsOn = {"firstName", "lastName"})
//                private String fullName;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, computer, dto);
//
//        assertThat(compilation).succeeded();
//
//        Class<?> dtoClass = loadClass("io.github.cyfko.example.UserDTO");
//        var requiredFields = ProjectionRegistry.getRequiredEntityFields(dtoClass);
//
//        // firstName should appear only once
//        assertEquals(2, requiredFields.size());
//        assertTrue(requiredFields.contains("firstName"));
//        assertTrue(requiredFields.contains("lastName"));
//    }
//
//    // ==================== Test Category 7: Case Sensitivity ====================
//
//    @Test
//    void testToEntityPathCaseSensitive() throws IOException {
//        JavaFileObject entity = createUserEntity();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(entity = User.class)
//            public class UserDTO {
//                @Projected
//                private String firstName;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, dto);
//
//        assertThat(compilation).succeeded();
//
//        Class<?> dtoClass = loadClass("io.github.cyfko.example.UserDTO");
//
//        // Case-sensitive should work
//        String path1 = ProjectionRegistry.toEntityPath("firstName", dtoClass, false);
//        assertEquals("firstName", path1);
//
//        // Case-insensitive should also work for exact match
//        String path2 = ProjectionRegistry.toEntityPath("firstName", dtoClass, true);
//        assertEquals("firstName", path2);
//    }
//
//    @Test
//    void testToEntityPathCaseInsensitive() throws IOException {
//        JavaFileObject entity = createUserEntity();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(entity = User.class)
//            public class UserDTO {
//                @Projected
//                private String firstName;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, dto);
//
//        assertThat(compilation).succeeded();
//
//        Class<?> dtoClass = loadClass("io.github.cyfko.example.UserDTO");
//
//        // Case-insensitive should match
//        String path = ProjectionRegistry.toEntityPath("FIRSTNAME", dtoClass, true);
//        assertEquals("firstName", path);
//    }
//
//    // ==================== Test Category 8: Error Scenarios ====================
//
//    @Test
//    void testToEntityPathInvalidField() throws IOException {
//        JavaFileObject entity = createUserEntity();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(entity = User.class)
//            public class UserDTO {
//                @Projected
//                private String firstName;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, dto);
//
//        assertThat(compilation).succeeded();
//
//        Class<?> dtoClass = loadClass("io.github.cyfko.example.UserDTO");
//
//        assertThrows(IllegalArgumentException.class, () ->
//            ProjectionRegistry.toEntityPath("nonExistent", dtoClass, false)
//        );
//    }
//
//    @Test
//    void testToEntityPathNullDtoClass() {
//        assertThrows(NullPointerException.class, () ->
//            ProjectionRegistry.toEntityPath("field", null, false)
//        );
//    }
//
//    @Test
//    void testGetMetadataForNonExistentClass() {
//        ProjectionMetadata metadata = ProjectionRegistry.getMetadataFor(NonExistentDTO.class);
//        assertNull(metadata);
//    }
//
//    @Test
//    void testHasProjection() throws IOException {
//        JavaFileObject entity = createUserEntity();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(entity = User.class)
//            public class UserDTO {
//                @Projected
//                private String firstName;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, dto);
//
//        assertThat(compilation).succeeded();
//
//        Class<?> dtoClass = loadClass("io.github.cyfko.example.UserDTO");
//        assertTrue(ProjectionRegistry.hasProjection(dtoClass));
//        assertFalse(ProjectionRegistry.hasProjection(NonExistentDTO.class));
//    }
//
//    // ==================== Test Category 9: Implicit Projection Metadata ====================
//
//    @Test
//    void testGetMetadataForEntityClass() throws IOException {
//        JavaFileObject entity = createUserEntity();
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity);
//
//        assertThat(compilation).succeeded();
//
//        Class<?> entityClass = loadClass("io.github.cyfko.example.User");
//        ProjectionMetadata metadata = ProjectionRegistry.getMetadataFor(entityClass);
//
//        // Should return implicit metadata
//        assertNotNull(metadata);
//        // Should have direct mappings for all entity fields
//        assertTrue(metadata.getDirectMapping("id", false).isPresent());
//        assertTrue(metadata.getDirectMapping("firstName", false).isPresent());
//        assertTrue(metadata.getDirectMapping("lastName", false).isPresent());
//        assertTrue(metadata.getDirectMapping("email", false).isPresent());
//        assertTrue(metadata.getDirectMapping("birthDate", false).isPresent());
//    }
//
//    // ==================== Test Category 10: Cache Functionality ====================
//
//    @Test
//    void testToEntityPathCaching() throws IOException {
//        JavaFileObject entity = createUserEntity();
//
//        JavaFileObject dto = JavaFileObjects.forSourceString(
//            "io.github.cyfko.example.UserDTO",
//            """
//            package io.github.cyfko.example;
//            import io.github.cyfko.filterql.jpa.metamodel.annotations.*;
//
//            @Projection(entity = User.class)
//            public class UserDTO {
//                @Projected
//                private String firstName;
//            }
//            """
//        );
//
//        Compilation compilation = Compiler.javac()
//            .withProcessors(new MetamodelProcessor())
//            .compile(entity, dto);
//
//        assertThat(compilation).succeeded();
//
//        Class<?> dtoClass = loadClass("io.github.cyfko.example.UserDTO");
//
//        // First call
//        String path1 = ProjectionRegistry.toEntityPath("firstName", dtoClass, false);
//
//        // Second call should use cache
//        String path2 = ProjectionRegistry.toEntityPath("firstName", dtoClass, false);
//
//        assertEquals("firstName", path1);
//        assertEquals("firstName", path2);
//        assertEquals(path1, path2);
//    }
//
//    // ==================== Helper Methods ====================
//
//    /**
//     * Helper method to load a class from the compilation result.
//     * Note: This requires the class to be available in the classpath.
//     * For integration tests, we use the actual test data classes.
//     */
//    private Class<?> loadClass(String targetClass) {
//        try {
//            return Class.forName(targetClass);
//        } catch (ClassNotFoundException e) {
//            throw new RuntimeException("Failed to load class: " + targetClass +
//                ". Make sure the class is compiled and in the classpath.", e);
//        }
//    }
//
//    // Dummy class for testing non-existent scenarios
//    private static class NonExistentDTO {
//        // Empty class for testing
//    }
}

