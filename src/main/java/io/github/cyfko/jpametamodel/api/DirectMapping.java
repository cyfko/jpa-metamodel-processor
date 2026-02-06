package io.github.cyfko.jpametamodel.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a direct mapping between a Data Transfer Object (DTO) field and its corresponding entity field.
 * <p>
 * This mapping defines how a field exposed in a DTO projection correlates with a field path in the underlying entity model,
 * including nested paths (e.g., "address.city") to support complex entity structures.
 * </p>
 * <p>
 * The {@code dtoFieldType} specifies the {@link Class} type of the DTO field, which is used especially
 * for type-safe query construction and further projection metadata resolution.
 * </p>
 * <p>
 * If the DTO field represents a collection, additional metadata about the collection kind and type may be provided.
 * </p>
 *
 * <p><b>Example usage:</b></p>
 * <pre>
 * {@code
 * // Simple scalar mapping:
 * DirectMapping mapping = new DirectMapping(
 *     "name",
 *     "username",
 *     java.lang.String.class,
 *     Optional.empty()
 * );
 *
 * // Nested entity field mapping:
 * DirectMapping nestedMapping = new DirectMapping(
 *     "city",
 *     "address.cityName",
 *     java.lang.String.class,
 *     Optional.empty()
 * );
 *
 * // Collection field mapping with metadata:
 * CollectionMetadata collectionMetadata = DirectMapping.CollectionMetadata.of(CollectionKind.LIST, CollectionType.PERSISTENT);
 * DirectMapping collectionMapping = new DirectMapping(
 *     "tags",
 *     "tagEntities",
 *     java.lang.String.class,
 *     Optional.of(collectionMetadata)
 * );
 * }
 * </pre>
 *
 * @param dtoField      the name of the field in the DTO projection
 * @param entityField   the path to the corresponding field in the entity, supporting nested paths via dot notation
 * @param dtoFieldType  the {@link Class} type of the DTO field (or collection element type if the field is a collection).
 * @param collection    optional metadata describing collection properties of the DTO field, if applicable
 * @author Frank KOSSI
 * @since 1.0.0
 */
public record DirectMapping(String dtoField, String entityField, Class<?> dtoFieldType, Optional<CollectionMetadata> collection) {

    public DirectMapping {
        Objects.requireNonNull(dtoField, "dtoField cannot be null");
        Objects.requireNonNull(entityField, "entityField cannot be null");
        Objects.requireNonNull(dtoFieldType, "dtoFieldType cannot be null");
        Objects.requireNonNull(collection, "collection cannot be null");

        if (dtoField.isBlank()) {
            throw new IllegalArgumentException("dtoField cannot be blank");
        }
        if (entityField.isBlank()) {
            throw new IllegalArgumentException("entityField cannot be blank");
        }
        if (dtoFieldType == null) {
            throw new IllegalArgumentException("dtoFieldType cannot be blank");
        }
    }

    /**
     * Indicates whether the entity field path describes a nested property.
     *
     * @return {@code true} if the {@code entityField} contains one or more dot separators representing nesting,
     *         {@code false} if it is a simple field without nesting
     */
    public boolean isNested() {
        return entityField.contains(".");
    }

    /**
     * Returns the depth of nesting in the entity field path.
     * <p>
     * For example, an {@code entityField} of {@code "address.city"} has a nesting depth of 1,
     * while {@code "order.customer.address.city"} has a nesting depth of 3.
     * </p>
     *
     * @return the number of nesting levels; zero indicates a non-nested field
     */
    public int nestingDepth() {
        return entityField.split("\\.").length - 1;
    }

    /**
     * Returns the root (first segment) of the entity field path.
     * <p>
     * For instance, the root of {@code "address.city"} is {@code "address"}.
     * </p>
     *
     * @return the root entity field name
     */
    public String getRootField() {
        return entityField.split("\\.")[0];
    }

    /**
     * Encapsulates metadata related to collection DTO fields.
     * <p>
     * This metadata specifies characteristics of the collection such as its kind (e.g., SET, LIST)
     * and the underlying collection type implementation (e.g., PERSISTENT, TRANSIENT).
     * </p>
     * <p>
     * This nested record is designed to be extendable with further collection-specific attributes if needed.
     * </p>
     *
     * @param kind           the kind of collection (an enum defining collection semantics)
     * @param collectionType the specific collection type implementation
     * @author Frank KOSSI
     * @since 4.0.0
     */
    public record CollectionMetadata(
            CollectionKind kind,
            CollectionType collectionType
    ) {
        /**
         * Factory method to create a new instance of {@link CollectionMetadata} with specified kind and collection type.
         *
         * @param kind           the kind of collection
         * @param collectionType the type of the collection implementation
         * @return a new {@link CollectionMetadata} instance
         */
        public static CollectionMetadata of(CollectionKind kind, CollectionType collectionType) {
            return new CollectionMetadata(kind, collectionType);
        }

        /**
         * Returns a string representing the Java code to instantiate this {@link CollectionMetadata}.
         * <p>
         * Useful for code generation or logging purposes.
         * </p>
         *
         * @return a String containing the Java constructor invocation for this metadata instance
         */
        public String asInstance(){
            return "new DirectMapping.CollectionMetadata(CollectionKind." + kind.name() + ", CollectionType." + collectionType.name() + ")";
        }
    }
}
