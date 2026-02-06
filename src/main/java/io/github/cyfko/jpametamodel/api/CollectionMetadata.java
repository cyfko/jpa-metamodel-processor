package io.github.cyfko.jpametamodel.api;

import java.util.Optional;

/**
 * Encapsulates metadata specific to collection fields.
 * <p>
 * This record can be extended with additional collection-specific attributes
 * such as fetch type, cascade options, ordering, etc.
 * </p>
 */
public record CollectionMetadata(
        CollectionKind kind,
        CollectionType collectionType,
        Optional<String> mappedBy,
        Optional<String> orderBy
) {
    /**
     * Creates a simple collection metadata with only the kind specified.
     */
    public static CollectionMetadata of(CollectionKind kind, CollectionType collectionType) {
        return new CollectionMetadata(kind, collectionType, Optional.empty(), Optional.empty());
    }

    /**
     * Returns a new instance with mappedBy set.
     */
    public CollectionMetadata withMappedBy(String mappedBy) {
        return new CollectionMetadata(kind, collectionType, Optional.of(mappedBy), orderBy);
    }

    /**
     * Returns a new instance with orderBy set.
     */
    public CollectionMetadata withOrderBy(String orderBy) {
        return new CollectionMetadata(kind, collectionType, mappedBy, Optional.of(orderBy));
    }
}
