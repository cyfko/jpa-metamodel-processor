package io.github.cyfko.jpametamodel.providers;

import io.github.cyfko.jpametamodel.api.PersistenceMetadata;
import io.github.cyfko.jpametamodel.processor.EntityProcessor;

import java.util.Map;

/**
 * Interface for accessing the registry of JPA entity metadata.
 * <p>
 * Implementations of this interface are typically generated at compile time
 * by the {@code EntityRegistryProcessor}. They expose metadata about all discovered
 * JPA entities and their fields.
 * </p>
 *
 * <p>
 * The registry maps each entity class to a map of field names and their corresponding metadata.
 * Field metadata includes information about:
 * <ul>
 *   <li>Identity fields (@Id, @EmbeddedId)</li>
 *   <li>Relationships (@OneToOne, @ManyToOne, @OneToMany, @ManyToMany)</li>
 *   <li>Collections (@ElementCollection) with element kind (Scalar, Embeddable, Entity)</li>
 *   <li>Collection types (List, Set, Map)</li>
 *   <li>Bidirectional relationships (mappedBy)</li>
 *   <li>Ordering clauses (@OrderBy)</li>
 *   <li>Embedded types (@Embedded)</li>
 *   <li>Mapped IDs (@MapsId)</li>
 * </ul>
 * </p>
 *
 * <p>
 * Example usage:
 * <pre>{@code
 * PersistenceMetadataRegistryProvider provider = // ... obtained from generated implementation
 * Map<Class<?>, Map<String, PersistenceMetadata>> registry = provider.getEntityMetadataRegistry();
 *
 * // Get metadata for User entity
 * Map<String, PersistenceMetadata> userFields = registry.get(User.class);
 *
 * // Get metadata for a specific field
 * PersistenceMetadata idField = userFields.get("id");
 * if (idField.isId()) {
 *     System.out.println("This is an identifier field");
 * }
 * }</pre>
 * </p>
 *
 * @see PersistenceMetadata
 * @see EntityProcessor
 */
public interface PersistenceRegistryProvider {

    /**
     * Returns the full metadata registry for all discovered JPA entities.
     * <p>
     * The returned map is unmodifiable and contains all entities processed
     * by the annotation processor at compile time. The outer map uses entity
     * classes as keys, and the inner maps use field names as keys.
     * </p>
     *
     * @return an unmodifiable map from entity class to field metadata map
     */
    Map<Class<?>, Map<String, PersistenceMetadata>> getEntityMetadataRegistry();

    /**
     * Returns the full metadata registry for all discovered JPA @Embeddable related to discovered JPA entities.
     * <p>
     * The returned map is unmodifiable and contains all {@link jakarta.persistence.Embeddable} processed
     * by the annotation processor at compile time. The outer map uses embeddable
     * classes as keys, and the inner maps use field names as keys.
     * </p>
     *
     * @return an unmodifiable map from {@link jakarta.persistence.Embeddable} class to field metadata map
     */
    Map<Class<?>, Map<String, PersistenceMetadata>> getEmbeddableMetadataRegistry();
}