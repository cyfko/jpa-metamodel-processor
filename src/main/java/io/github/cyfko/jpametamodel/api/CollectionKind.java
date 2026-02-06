package io.github.cyfko.jpametamodel.api;

/**
 * Represents the kind of elements contained in a collection field.
 */
public enum CollectionKind {
    /**
     * Collection of basic types (String, Integer, etc.)
     */
    SCALAR,

    /**
     * Collection of @Embeddable types
     */
    EMBEDDABLE,

    /**
     * Collection of @Entity types
     */
    ENTITY,

    /**
     * Unknown or undetermined collection type
     */
    UNKNOWN
}
