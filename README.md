# JPA Metamodel Processor

[![Maven Central](https://img.shields.io/maven-central/v/io.github.cyfko/jpa-metamodel-processor)](https://search.maven.org/artifact/io.github.cyfko/jpa-metamodel-processor)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)

**JPA Metamodel Processor** is a Java annotation processor that generates a compile-time type-safe metadata registry for JPA entities and their DTO projections. It automatically extracts structural information from entities (fields, identifiers, relationships, collections) and enables declarative mapping definitions between entities and DTOs with support for computed fields.

## 🎯 Goals

This project provides:

- **Automatic JPA metadata extraction**: Analyzes entities and embeddables to build a compile-time metadata registry
- **DTO projection management**: Declarative mapping definitions between JPA entities and DTOs with compile-time validation
- **Computed field support**: Integration of computation providers for derived fields
- **Type-safe registries**: Generation of immutable registries accessible at runtime via simple APIs

## 📋 Prerequisites

- **Java 21+**
- **Maven 3.6+**
- **Jakarta Persistence API 3.1.0+**

## 🚀 Installation

### Maven

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>jpa-metamodel-processor</artifactId>
    <version>1.0.0</version>
</dependency>
```

The annotation processor will be automatically detected and executed during compilation thanks to `auto-service`.

## 📖 Usage Guide

### 1. Define Your JPA Entities

The processor automatically analyzes all classes annotated with `@Entity` and `@Embeddable`:

```java
@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String firstName;
    private String lastName;
    private String email;
    private LocalDate birthDate;
    
    @Embedded
    private Address address;
    
    @ManyToOne
    private Department department;
    
    @OneToMany(mappedBy = "user")
    private List<Order> orders;
    
    // Getters and setters...
}
```

### 2. Create DTO Projections

Use the `@Projection`, `@Projected`, and `@Computed` annotations to define your DTOs:

```java
@Projection(
    entity = User.class,
    providers = {
        @Provider(value = UserComputations.class)
    }
)
public class UserDTO {
    
    // Direct mapping with field renaming
    @Projected(from = "email")
    private String userEmail;
    
    // Nested path to an embeddable field
    @Projected(from = "address.city")
    private String city;
    
    // Nested path to a relationship
    @Projected(from = "department.name")
    private String departmentName;
    
    // Collection
    @Projected(from = "orders")
    private List<OrderDTO> orders;
    
    // Computed field depending on multiple fields
    @Computed(dependsOn = {"firstName", "lastName"})
    private String fullName;
    
    // Computed field depending on a single field
    @Computed(dependsOn = {"birthDate"})
    private Integer age;
    
    // Getters and setters...
}
```

### 3. Define Computation Providers

Create classes containing computation methods for your `@Computed` fields:

```java
public class UserComputations {
    
    // Static method for fullName
    public static String getFullName(String firstName, String lastName) {
        return firstName + " " + lastName;
    }
    
    // Instance method for age (can be a Spring bean)
    public Integer getAge(LocalDate birthDate) {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}
```

**Naming convention**: Methods must follow the pattern `get[FieldName]` where `FieldName` is the DTO field name with the first letter capitalized.

### 4. Use Registries at Runtime

#### Access Entity Metadata

```java
import io.github.cyfko.filterql.jpa.metamodel.PersistenceRegistry;

// Check if an entity is registered
boolean isRegistered = PersistenceRegistry.isEntityRegistered(User.class);

// Get metadata for an entity
Map<String, PersistenceMetadata> metadata = PersistenceRegistry.getMetadataFor(User.class);

// Get metadata for a specific field
PersistenceMetadata fieldMeta = PersistenceRegistry.getFieldMetadata(User.class, "email");

// Get ID fields of an entity
List<String> idFields = PersistenceRegistry.getIdFields(User.class);
```

#### Access Projection Metadata

```java
import io.github.cyfko.filterql.jpa.metamodel.ProjectionRegistry;

// Get metadata for a projection
ProjectionMetadata projectionMeta = ProjectionRegistry.getMetadataFor(UserDTO.class);

// Check if a projection exists
boolean hasProjection = ProjectionRegistry.hasProjection(UserDTO.class);

// Get required entity fields for a projection
List<String> requiredFields = ProjectionRegistry.getRequiredEntityFields(UserDTO.class);

// Convert a DTO path to an entity path
String entityPath = ProjectionRegistry.toEntityPath("userEmail", UserDTO.class, false);
// Returns: "email"

String nestedPath = ProjectionRegistry.toEntityPath("city", UserDTO.class, false);
// Returns: "address.city"
```

## 💡 Use Cases

### Use Case 1: REST API with Projections

In a REST API, you can use the registries to dynamically build JPA queries based on fields requested by the client:

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @GetMapping
    public List<UserDTO> getUsers(@RequestParam(required = false) String fields) {
        // Determine required entity fields for the projection
        List<String> requiredFields = fields != null 
            ? parseFields(fields, UserDTO.class)
            : ProjectionRegistry.getRequiredEntityFields(UserDTO.class);
        
        // Build an optimized JPA query
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        // Select only necessary fields
        // ... query building logic ...
        
        return users.stream()
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }
    
    private List<String> parseFields(String fields, Class<?> dtoClass) {
        return Arrays.stream(fields.split(","))
            .map(field -> ProjectionRegistry.toEntityPath(field.trim(), dtoClass, true))
            .collect(Collectors.toList());
    }
}
```

### Use Case 2: Filter Schema Validation

For a dynamic filtering system, you can validate that fields used in filters exist in entities:

```java
public class FilterValidator {
    
    public void validateFilter(String entityName, String fieldPath) {
        Class<?> entityClass = resolveEntityClass(entityName);
        
        if (!PersistenceRegistry.isEntityRegistered(entityClass)) {
            throw new IllegalArgumentException("Entity not registered: " + entityName);
        }
        
        // Validate the field path
        String[] segments = fieldPath.split("\\.");
        Map<String, PersistenceMetadata> metadata = PersistenceRegistry.getMetadataFor(entityClass);
        
        for (String segment : segments) {
            PersistenceMetadata fieldMeta = metadata.get(segment);
            if (fieldMeta == null) {
                throw new IllegalArgumentException("Invalid field: " + segment);
            }
            // Navigate to next type if necessary...
        }
    }
}
```

### Use Case 3: API Documentation Generation

Automatically generate OpenAPI/Swagger documentation from metadata:

```java
public class ApiDocumentationGenerator {
    
    public OpenAPISchema generateSchema(Class<?> dtoClass) {
        ProjectionMetadata metadata = ProjectionRegistry.getMetadataFor(dtoClass);
        
        OpenAPISchema schema = new OpenAPISchema();
        
        // Add direct fields
        for (DirectMapping mapping : metadata.directMappings()) {
            schema.addProperty(mapping.dtoField(), resolveType(mapping.dtoFieldType()));
        }
        
        // Add computed fields
        for (ComputedField computed : metadata.computedFields()) {
            schema.addProperty(computed.dtoField(), resolveComputedType(computed));
        }
        
        return schema;
    }
}
```

### Use Case 4: Query Optimization with Field Selection

Build JPA queries that only load fields necessary for a given projection:

```java
public class OptimizedQueryBuilder {
    
    public <T> TypedQuery<T> buildProjectionQuery(Class<?> dtoClass, Class<T> entityClass) {
        List<String> requiredFields = ProjectionRegistry.getRequiredEntityFields(dtoClass);
        
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<T> root = query.from(entityClass);
        
        // Build selection based on required fields
        List<Selection<?>> selections = requiredFields.stream()
            .map(field -> buildSelection(root, field))
            .collect(Collectors.toList());
        
        query.select(cb.array(selections.toArray(new Selection[0])));
        
        return (TypedQuery<T>) em.createQuery(query);
    }
    
    private Selection<?> buildSelection(Root<?> root, String fieldPath) {
        String[] segments = fieldPath.split("\\.");
        Path<?> path = root;
        
        for (String segment : segments) {
            path = path.get(segment);
        }
        
        return path;
    }
}
```

## 🔧 Advanced Features

### Collection Support

The processor automatically detects collections and extracts their metadata:

```java
@Projection(entity = User.class)
public class UserDTO {
    
    @Projected(from = "orders")
    private List<OrderDTO> orders;  // Entity collection
    
    @Projected(from = "tags")
    private Set<String> tags;  // Element collection
}
```

### Computation Providers with Dependency Injection

You can use Spring beans for computation providers:

```java
@Projection(
    entity = User.class,
    providers = {
        @Provider(value = DateFormatter.class, bean = "isoDateFormatter")
    }
)
public class UserDTO {
    @Computed(dependsOn = {"createdAt"})
    private String formattedDate;
}

@Service("isoDateFormatter")
public class DateFormatter {
    public String getFormattedDate(LocalDateTime createdAt) {
        return createdAt.format(DateTimeFormatter.ISO_DATE);
    }
}
```

### Complex Nested Paths

Support for deep paths in entity hierarchy:

```java
@Projected(from = "department.manager.address.city")
private String managerCity;
```

## 🏗️ Architecture

The processor works in two phases:

1. **Phase 1 - Entity Processing**: Analyzes all `@Entity` and `@Embeddable` classes, extracts their metadata, and generates `PersistenceMetadataRegistryProviderImpl`
2. **Phase 2 - Projection Processing**: Analyzes all `@Projection` classes, validates mappings against entity metadata, and generates `ProjectionMetadataRegistryProviderImpl`

The generated registries are immutable and thread-safe, accessible via the utility classes `PersistenceRegistry` and `ProjectionRegistry`.

## 📝 Annotations

### `@Projection`

Class-level annotation that declares a DTO projection.

**Parameters:**
- `entity`: The source JPA entity class (required)
- `providers`: Array of computation providers (optional)

### `@Projected`

Field-level annotation to map a DTO field to an entity field.

**Parameters:**
- `from`: The path to the entity field (optional, uses DTO field name by default)

### `@Computed`

Field-level annotation to declare a computed field.

**Parameters:**
- `dependsOn`: Array of paths to entity fields required for computation

### `@Provider`

Annotation to declare a computation provider.

**Parameters:**
- `value`: The provider class (required)
- `bean`: The bean name for dependency injection (optional)

## ⚠️ Limitations

- Entity classes must be public
- Test classes are automatically ignored
- `@Transient` fields are excluded from analysis
- Computation providers must follow the `get[FieldName]` naming convention

## 🤝 Contributing

Contributions are welcome! Please open an issue to discuss major changes before submitting a pull request.

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## 👤 Author

**Frank KOSSI**

- Email: frank.kossi@kunrin.com
- Organization: [Kunrin SA](https://www.kunrin.com)

## 🔗 Links

- [GitHub Repository](https://github.com/cyfko/jpa-metamodel-processor)
- [Issue Tracker](https://github.com/cyfko/jpa-metamodel-processor/issues)
- [Maven Central](https://search.maven.org/artifact/io.github.cyfko/jpa-metamodel-processor)
