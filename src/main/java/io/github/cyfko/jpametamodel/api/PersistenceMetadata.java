package io.github.cyfko.jpametamodel.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents metadata about a single field within a JPA entity.
 * <p>
 * This model is generated at compile time and used to describe key characteristics
 * of entity fields, including whether they are identifiers, collections, or mapped relationships.
 * </p>
 *
 * <p>
 * Typical use cases include query generation, validation, and introspection of entity structures.
 * </p>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * // Scalar field (e.g., String, Integer, or @Embeddable): neither an identifier nor a collection
 * PersistenceMetadata scalarField = PersistenceMetadata.scalar(java.lang.String.class);
 * PersistenceMetadata embeddableField = PersistenceMetadata.scalar(com.example.Address.class);
 *
 * // Identifier field (@Id or @EmbeddedId)
 * PersistenceMetadata idField = PersistenceMetadata.id(java.lang.Long.class); // field annotated with @Id
 * PersistenceMetadata orderIdField = PersistenceMetadata.id(com.example.OrderId.class); // field annotated with @EmbeddedId
 *
 * // Collection field (e.g., @OneToMany, @ElementCollection)
 * CollectionMetadata collectionMeta = new CollectionMetadata(CollectionKind.ENTITY, CollectionType.LIST, Optional.of("orders"));
 * PersistenceMetadata collectionField = PersistenceMetadata.collection(collectionMeta, com.example.Order.class);
 *
 * // Field mapped via @MapsId
 * PersistenceMetadata mappedIdField = collectionField.withMappedId("userId");
 * }</pre>
 *
 * @param isId true if the field is an identifier (@Id or @EmbeddedId)
 * @param relatedType the {@link Class} type of the related field
 * @param mappedIdField the field name used for @MapsId mapping, if any
 * @param collection metadata for collection fields, if any
 * @since 1.0.0
 * @author Frank KOSSI
 */
public record PersistenceMetadata(
        boolean isId,
        Class<?> relatedType,
        Optional<String> mappedIdField,
        Optional<CollectionMetadata> collection
) {

    public PersistenceMetadata {
        Objects.requireNonNull(relatedType, "relatedType cannot be null");
        Objects.requireNonNull(mappedIdField, "mappedIdField cannot be null");
        Objects.requireNonNull(collection, "collection cannot be null");
    }

    // ==================== Factory Methods ====================

    /**
     * Creates metadata for a simple scalar field (primitive, basic type, or @Embeddable).
     * <p>
     * A scalar field is neither an identifier (@Id or @EmbeddedId) nor a collection.
     * It represents a basic type (e.g., String, Integer), a primitive, or an embeddable
     * (a field whose relatedType is annotated with @Embeddable).
     * </p>
     *
     * @param relatedType the type of the scalar field (e.g., "String", "Integer", "Address")
     * @return metadata for a scalar field
     */
    public static PersistenceMetadata scalar(Class<?> relatedType) {
        return new PersistenceMetadata(false, relatedType, Optional.empty(), Optional.empty());
    }

    /**
     * Creates metadata for an identifier field (@Id or @EmbeddedId).
     *
     * @param relatedType the type of the identifier field (e.g., "User", "Address")
     * @return metadata for an identifier field
     */
    public static PersistenceMetadata id(Class<?> relatedType) {
        return new PersistenceMetadata(true, relatedType, Optional.empty(), Optional.empty());
    }

    /**
     * Creates metadata for a collection field (@ElementCollection, @OneToMany, @ManyToMany).
     *
     * @param collectionMetadata metadata describing the collection
     * @param elementType the type of elements in the collection
     * @return metadata for a collection field
     */
    public static PersistenceMetadata collection(CollectionMetadata collectionMetadata, Class<?> elementType) {
        return new PersistenceMetadata(
                false,
                elementType,
                Optional.empty(),
                Optional.of(collectionMetadata)
        );
    }

    // ==================== Fluent Builders ====================

    /**
     * Returns a new instance with mappedIdField set.
     * Used for fields annotated with @MapsId.
     *
     * @param fieldName the name of the field used for @MapsId mapping
     * @return a new metadata instance with mappedIdField set
     */
    public PersistenceMetadata withMappedId(String fieldName) {
        return new PersistenceMetadata(isId, relatedType, Optional.of(fieldName), collection);
    }

    // ==================== Query Methods ====================

    /**
     * Checks if this field is a collection type.
     *
     * @return true if the field is a collection
     */
    public boolean isCollection() {
        return collection.isPresent();
    }

    /**
     * Checks if this field is mapped via @MapsId.
     *
     * @return true if the field is mapped via @MapsId
     */
    public boolean isMappedId() {
        return mappedIdField.isPresent();
    }

    /**
     * Gets the collection kind if this is a collection field.
     *
     * @return the collection kind, or empty if not a collection
     */
    public Optional<CollectionKind> collectionKind() {
        return collection.map(CollectionMetadata::kind);
    }

    /**
     * Checks if this is a scalar collection (@ElementCollection of basic types).
     *
     * @return true if the field is a scalar collection
     */
    public boolean isScalarCollection() {
        return collectionKind()
                .map(kind -> kind == CollectionKind.SCALAR)
                .orElse(false);
    }

    /**
     * Checks if this is an entity collection (@OneToMany, @ManyToMany).
     *
     * @return true if the field is an entity collection
     */
    public boolean isEntityCollection() {
        return collectionKind()
                .map(kind -> kind == CollectionKind.ENTITY)
                .orElse(false);
    }

    /**
     * Checks if this is an embeddable collection (@ElementCollection of @Embeddable).
     *
     * @return true if the field is an embeddable collection
     */
    public boolean isEmbeddableCollection() {
        return collectionKind()
                .map(kind -> kind == CollectionKind.EMBEDDABLE)
                .orElse(false);
    }

    /**
     * Checks if this is a bidirectional relationship (has mappedBy).
     *
     * @return true if the field is a bidirectional relationship
     */
    public boolean isBidirectional() {
        return collection
                .flatMap(CollectionMetadata::mappedBy)
                .isPresent();
    }

    /**
     * Checks if this collection is ordered (List type).
     *
     * @return true if the field is an ordered collection
     */
    public boolean isOrdered() {
        return collection
                .map(c -> c.collectionType() == CollectionType.LIST)
                .orElse(false);
    }

    /**
     * Gets the mappedBy value if this is a bidirectional relationship.
     *
     * @return the mappedBy value, or empty if not bidirectional
     */
    public Optional<String> getMappedBy() {
        return collection.flatMap(CollectionMetadata::mappedBy);
    }

    /**
     * Checks if this is a List of entities.
     *
     * @return true if the field is a List of entities
     */
    public boolean isEntityList() {
        return collection
                .map(c -> c.kind() == CollectionKind.ENTITY &&
                        c.collectionType() == CollectionType.LIST)
                .orElse(false);
    }

    /**
     * Checks if this is a Set of entities.
     *
     * @return true if the field is a Set of entities
     */
    public boolean isEntitySet() {
        return collection
                .map(c -> c.kind() == CollectionKind.ENTITY &&
                        c.collectionType() == CollectionType.SET)
                .orElse(false);
    }
}
