package io.github.cyfko.jpametamodel.providers;

import io.github.cyfko.jpametamodel.api.ProjectionMetadata;
import java.util.Map;

/**
 * Provider interface for projection metadata registry.
 * Implementations are generated at compile-time by the annotation processor.
 */
public interface ProjectionRegistryProvider {
    /**
     * Returns the projection metadata registry.
     * @return immutable map of DTO class to ProjectionMetadata
     */
    Map<Class<?>, ProjectionMetadata> getProjectionMetadataRegistry();
}
