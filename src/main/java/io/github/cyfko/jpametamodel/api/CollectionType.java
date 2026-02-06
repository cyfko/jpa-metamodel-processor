package io.github.cyfko.jpametamodel.api;

/**
 * Type of collection container used in JPA entities.
 */
public enum CollectionType {
    /** java.util.List */
    LIST,
    
    /** java.util.Set */
    SET,
    
    /** java.util.Map */
    MAP,
    
    /** java.util.Collection (generic) */
    COLLECTION,
    
    /** Unknown or unsupported collection type */
    UNKNOWN
}