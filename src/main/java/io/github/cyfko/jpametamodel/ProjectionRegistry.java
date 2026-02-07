package io.github.cyfko.jpametamodel;

import io.github.cyfko.jpametamodel.api.CollectionMetadata;
import io.github.cyfko.jpametamodel.api.PersistenceMetadata;
import io.github.cyfko.jpametamodel.api.ComputationProvider;
import io.github.cyfko.jpametamodel.api.ComputedField;
import io.github.cyfko.jpametamodel.api.DirectMapping;
import io.github.cyfko.jpametamodel.api.ProjectionMetadata;
import io.github.cyfko.jpametamodel.providers.ProjectionRegistryProvider;

import java.util.*;

/**
 * Centralized utility class serving as a unified registry for handling projection metadata within the JPA context.
 * <p>
 * This class provides thread-safe access to the generated projection metadata registry implementation,
 * enabling retrieval and translation of DTO projection fields to their corresponding entity paths.
 * It also caches path mappings to optimize repeated queries.
 * </p>
 *
 * <p>This registry supports:</p>
 * <ul>
 *     <li>Lazy and thread-safe loading of the {@link ProjectionRegistryProvider} implementation via reflection.</li>
 *     <li>Retrieval of projection metadata for Data Transfer Object (DTO) classes.</li>
 *     <li>Determination of required entity fields for projections.</li>
 *     <li>Conversion of DTO projection field paths to fully qualified entity paths, respecting case sensitivity.</li>
 * </ul>
 *
 * <p>This utility class cannot be instantiated and exposes only static methods.</p>
 *
 * @author Frank KOSSI
 * @since 1.0.0
 */
public final class ProjectionRegistry {

    /**
     * Volatile instance of the projection metadata registry provider implementing the registry access.
     */
    private static volatile ProjectionRegistryProvider PROVIDER;

    /**
     * Cache storing precomputed mappings from DTO projection paths to entity paths per DTO class.
     */
    private static final Map<Class<?>, Map<String, String>> PROJECTION_TO_ENTITY_PATH_MAPPINGS_CACHE = new HashMap<>();

    /**
     * Private constructor to prevent instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always thrown to prevent instantiation
     */
    private ProjectionRegistry() {
        throw new UnsupportedOperationException("ProjectionRegistry is a utility class and cannot be instantiated");
    }

    /**
     * Returns the singleton instance of the projection metadata registry provider.
     * <p>
     * This method lazily loads the provider using double-checked locking to ensure thread-safe initialization.
     * </p>
     *
     * @return the {@link ProjectionRegistryProvider} instance providing projection metadata access
     * @throws IllegalStateException if the implementation class cannot be loaded or instantiated
     */
    public static ProjectionRegistryProvider getProvider() {
        // 1. Si on a déjà chargé le provider, on le retourne directement (Cache)
        if (PROVIDER != null) {
            return PROVIDER;
        }

        // 2. TENTATIVE A : ServiceLoader (Méthode standard Java)
        // Cela fonctionne si le module-info est configuré ou si META-INF est présent
        ServiceLoader<ProjectionRegistryProvider> loader = ServiceLoader.load(ProjectionRegistryProvider.class);
        Iterator<ProjectionRegistryProvider> iterator = loader.iterator();

        if (iterator.hasNext()) {
            PROVIDER = iterator.next();
            return PROVIDER;
        }

        // 3. TENTATIVE B : Fallback Manuel (Réflexion)
        // Si le ServiceLoader échoue (souvent le cas en tests ou sans modules),
        // on force le chargement de la classe par son nom exact.
        try {
            String generatedClassName = "io.github.cyfko.jpametamodel.providers.impl.ProjectionRegistryProviderImpl";

            Class<?> clazz = Class.forName(generatedClassName);
            PROVIDER = (ProjectionRegistryProvider) clazz.getDeclaredConstructor().newInstance();

            return PROVIDER;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot load metadata registry: ProjectionRegistryProviderImpl class not found. " +
                    "Ensure that the annotation processor has run and generated the registry class. " +
                    "Check that 'io.github.cyfko:jpa-metamodel-processor' is configured as an annotation processor.",
                    e
            );
        }
    }

    /**
     * Retrieves the {@link ProjectionMetadata} associated with the provided DTO projection class.
     * <p>
     * If explicit projection metadata does not exist for the given class, but the class is a registered JPA entity,
     * this method generates and returns an implicit {@link ProjectionMetadata} by treating all entity fields as direct projection fields.
     * This allows seamless support for ad-hoc projections on existing entity classes without requiring explicit DTO registration.
     * </p>
     *
     * @param dtoClass the DTO projection class or JPA entity class for which metadata is requested
     * @return the corresponding {@link ProjectionMetadata} if registered, or a synthesized instance reflecting
     * the entity structure if the class is a registered entity; returns {@code null} if no relevant metadata is available
     *
     * @example
     * <pre>
     * {@code
     * // Case 1: DTO class with registered projection metadata
     * ProjectionMetadata userMeta = ProjectionRegistry.getMetadataFor(UserDTO.class);
     * // Returns registered metadata for UserDTO, allowing mapping of custom DTO fields to entity fields
     *
     * // Case 2: JPA entity class (not a registered projection)
     * ProjectionMetadata entityMeta = ProjectionRegistry.getMetadataFor(UserEntity.class);
     * // Returns implicit metadata mapping all entity fields directly (e.g., "id"->"id", "name"->"name")
     *
     * // Example for an entity with collections:
     * // If UserEntity has List<Role> roles; the metadata will include a mapping for "roles" with the appropriate collection kind/type.
     * }
     * </pre>
     */
    public static ProjectionMetadata getMetadataFor(Class<?> dtoClass) {
        ProjectionMetadata projectionMetadata = getProvider().getProjectionMetadataRegistry().get(dtoClass);

        if (projectionMetadata == null && PersistenceRegistry.isEntityRegistered(dtoClass)) {
            projectionMetadata = getImplicitProjectionMetadataFromEntity(dtoClass);
        }

        return projectionMetadata;
    }

    /**
     * Produces an implicit {@link ProjectionMetadata} instance by transforming the entity's persistence metadata.
     * <p>
     * Each persistent property of the entity is mapped as a direct projection using the same field name for both DTO and entity.
     * Collection fields are annotated with their collection-specific metadata if present.
     * This method is used for JPA entity classes that do not have explicit projection metadata, enabling default, field-for-field projections.
     * </p>
     *
     * @param entityClass a class known to be a JPA entity
     * @return an implicit {@link ProjectionMetadata} instance mapping all entity fields as direct projection fields
     */
    private static ProjectionMetadata getImplicitProjectionMetadataFromEntity(Class<?> entityClass) {
        Map<String, PersistenceMetadata> persistenceMetadata = PersistenceRegistry.getMetadataFor(entityClass);

        DirectMapping[] directMappings = persistenceMetadata.entrySet()
                .stream()
                .map(e -> {
                    String fieldName = e.getKey();
                    PersistenceMetadata metadata = e.getValue();

                    DirectMapping.CollectionMetadata collectionMetadata = null;
                    if (metadata.collection().isPresent()) {
                        CollectionMetadata ec = metadata.collection().get();
                        collectionMetadata = new DirectMapping.CollectionMetadata(ec.kind(), ec.collectionType());
                    }

                    return new DirectMapping(fieldName,
                            fieldName,
                            metadata.relatedType(),
                            Optional.ofNullable(collectionMetadata)
                    );
                }).toArray(DirectMapping[]::new);

        return new ProjectionMetadata(entityClass, directMappings, new ComputedField[]{}, new ComputationProvider[]{});
    }

    /**
     * Checks whether a projection metadata entry exists for the specified DTO projection class.
     *
     * @param dtoClass the DTO projection class to check
     * @return {@code true} if metadata for the class exists, {@code false} otherwise
     */
    public static boolean hasProjection(Class<?> dtoClass) {
        return getProvider().getProjectionMetadataRegistry().containsKey(dtoClass);
    }

    /**
     * Returns a list of all required entity fields that the projection for the specified DTO class depends on.
     * <p>
     * This list includes all entity attributes needed for fully populating the DTO projection.
     * </p>
     *
     * @param dtoClass the DTO projection class to query
     * @return a list of required entity field names; empty if no metadata found
     */
    public static List<String> getRequiredEntityFields(Class<?> dtoClass) {
        ProjectionMetadata metadata = getMetadataFor(dtoClass);
        return metadata != null ? metadata.getAllRequiredEntityFields() : List.of();
    }

    /**
     * Converts a DTO projection field path to the corresponding entity field path.
     * <p>
     * This method resolves projection paths recursively, considering both computed
     * fields and direct entity field mappings defined in the projection metadata.
     * </p>
     * <p>
     * Caching is employed to optimize repeated lookups for the same DTO class and path.
     * </p>
     *
     * @param dtoPath    the projection field path in DTO format (e.g., {@code "address.city"} or {@code "fullName"})
     * @param dtoClass   the DTO projection class context to resolve the path
     * @param ignoreCase if {@code true}, DTO path matching is case-insensitive
     * @return the translated entity path string corresponding to the DTO projection path
     * @throws IllegalArgumentException if the projection path cannot be resolved to a valid entity path
     *
     * @example
     * <pre>
     * {@code
     * // Given the following DTO projection classes and mappings:
     * @Projection(User.class)
     * UserDTO {
     *     @Projected(from = "username")
     *     String name;
     *
     *     @Projected
     *     AddressDTO address;
     * }
     *
     * @Projection(
     *      entity = Address.class,
     *      computers = @Computer(GeographyUtils.class)
     * )
     * AddressDTO {
     *      @Projected(from = "cityName")
     *      String city;
     *
     *      @Projected(from = "streetName")
     *      String street;
     *
     *      @Computed(dependsOn = {"cityName", "streetName"})
     *      String geographicZone;
     * }
     *
     * // Assume the projection metadata maps:
     * // 'name' -> 'username'
     * // 'address.city' -> 'address.cityName'
     * // 'address.street' -> 'address.streetName'
     *
     * // Example usage:
     * String entityPath1 = ProjectionRegistry.toEntityPath("name", UserDTO.class, false);
     * // Returns "username"
     *
     * String entityPath2 = ProjectionRegistry.toEntityPath("address.city", UserDTO.class, false);
     * // Returns "address.cityName"
     *
     * String entityPath3 = ProjectionRegistry.toEntityPath("address.geographicZone", UserDTO.class, false);
     * // Returns "address.cityName,address.streetName"
     * }
     * </pre>
     */
    public static String toEntityPath(String dtoPath, Class<?> dtoClass, boolean ignoreCase) {
        synchronized (PROJECTION_TO_ENTITY_PATH_MAPPINGS_CACHE) {
            if (PROJECTION_TO_ENTITY_PATH_MAPPINGS_CACHE.containsKey(dtoClass)) {
                Map<String, String> mappingsCache = PROJECTION_TO_ENTITY_PATH_MAPPINGS_CACHE.get(dtoClass);
                if (ignoreCase) {
                    Optional<String> match = mappingsCache.keySet().stream()
                            .filter(k -> k.equalsIgnoreCase(dtoPath))
                            .findFirst();
                    if (match.isPresent()) {
                        return mappingsCache.get(match.get());
                    }
                } else if (mappingsCache.containsKey(dtoPath)) {
                    return mappingsCache.get(dtoPath);
                }
            }
        }

        final StringBuilder entityPath = new StringBuilder();
        toEntityPathRecursive(dtoPath, dtoClass, entityPath, ignoreCase);
        String entityPathString = entityPath.toString();

        synchronized (PROJECTION_TO_ENTITY_PATH_MAPPINGS_CACHE) {
            PROJECTION_TO_ENTITY_PATH_MAPPINGS_CACHE.putIfAbsent(dtoClass, new HashMap<>());
            PROJECTION_TO_ENTITY_PATH_MAPPINGS_CACHE.get(dtoClass).put(dtoPath, entityPathString);
        }

        return entityPathString;
    }

    /**
     * Recursively resolves the given DTO projection path segments into the equivalent entity path.
     * <p>
     * Supports recognition of computed fields and direct mappings. Throws {@link IllegalArgumentException} if an invalid field is encountered.
     * </p>
     *
     * @param dtoProjectionPath the projection path segment to resolve
     * @param dtoClass          the current DTO projection class context
     * @param entityPath        the builder accumulating the entity path
     * @param ignoreCase        whether to match DTO fields ignoring case
     * @throws IllegalArgumentException if the DTO path does not resolve to a valid field mapping
     */
    private static void toEntityPathRecursive(String dtoProjectionPath, Class<?> dtoClass, StringBuilder entityPath, boolean ignoreCase) {
        try {
            final ProjectionMetadata metadata = ProjectionRegistry.getMetadataFor(dtoClass);

            int dotIndex = dtoProjectionPath.indexOf('.');
            final String dtoField = dotIndex == -1 ? dtoProjectionPath : dtoProjectionPath.substring(0, dotIndex);

            // Check whether it is a computed field
            final Optional<ComputedField> computedField = metadata.getComputedField(dtoField, ignoreCase);
            if (computedField.isPresent()) {
                String[] dependencies = computedField.get().dependencies();
                String prefix = entityPath.toString();

                // use previous entityPath as a prefix for each dependency
                if (!prefix.isEmpty()) {
                    entityPath.append(".");
                }
                entityPath.append(dependencies[0]);

                for (int i = 1; i < dependencies.length; i++) {
                    entityPath.append(",");
                    if (!prefix.isEmpty()) {
                        entityPath.append(prefix).append(".");
                    }
                    entityPath.append(dependencies[i]);
                }

                return;
            }

            // Check whether it is a direct mapping field
            final Optional<DirectMapping> directMapping = metadata.getDirectMapping(dtoField, ignoreCase);
            if (directMapping.isEmpty()) {
                throw new IllegalArgumentException("Invalid field: " + dtoField);
            }

            // Record the entity field for the current mapping
            DirectMapping mapping = directMapping.get();
            Class<?> nextDtoClass = mapping.dtoFieldType();
            entityPath.append(mapping.entityField());

            // Recurse on remaining path (if any)
            if (dotIndex == -1) {
                return; // No remaining field to process, end recursion
            } else {
                entityPath.append(".");
            }

            toEntityPathRecursive(dtoProjectionPath.substring(dotIndex + 1), nextDtoClass, entityPath, ignoreCase);
        } catch (Exception e) {
            throw new IllegalArgumentException("\"" + dtoProjectionPath + "\" does not resolve to a valid projection field path.", e);
        }
    }

}
