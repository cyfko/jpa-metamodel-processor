package io.github.cyfko.jpametamodel.api;

import java.util.*;

/**
 * Metadata about a DTO projection mapping to an entity.
 *
 * @param entityClass Full qualified name of the entity class
 * @param directMappings List of direct field mappings (DTO field → Entity field)
 * @param computedFields List of computed fields with their dependencies
 * @since 1.0.0
 */
public record ProjectionMetadata(
        Class<?> entityClass,
        DirectMapping[] directMappings,
        ComputedField[] computedFields,
        ComputationProvider[] computers
) {

    public ProjectionMetadata {
        Objects.requireNonNull(entityClass, "entityClass cannot be null");
        Objects.requireNonNull(directMappings, "directMappings cannot be null");
        Objects.requireNonNull(computedFields, "computedFields cannot be null");
        Objects.requireNonNull(computers, "computers cannot be null");
    }

    /**
     * Gets all entity fields required for this projection.
     * Includes both direct mappings and computed field dependencies.
     *
     * @return deduplicated list of entity field paths
     */
    public List<String> getAllRequiredEntityFields() {
        Set<String> fields = new LinkedHashSet<>();

        // Add direct mappings
        for (var dm: directMappings){
            fields.add(dm.entityField());
        }

        // Add computed dependencies
        for (var cf: computedFields){
            fields.addAll(List.of(cf.dependencies()));
        }

        return List.copyOf(fields);
    }

    /**
     * Gets the direct mapping for a DTO field, if it exists.
     *
     * @param dtoField the DTO field name
     * @param ignoreCase whether field name should be compared equals ignoring case
     * @return Optional containing the mapping, or empty if not found
     */
    public Optional<DirectMapping> getDirectMapping(String dtoField, boolean ignoreCase) {
        return Arrays.stream(directMappings)
                .filter(m -> ignoreCase ? m.dtoField().equalsIgnoreCase(dtoField) : m.dtoField().equals(dtoField))
                .findFirst();
    }

    /**
     * Gets the computed field metadata for a DTO field, if it exists.
     *
     * @param dtoField the DTO field name
     * @param ignoreCase whether field name should be compared equals ignoring case
     * @return Optional containing the computed field, or empty if not found
     */
    public Optional<ComputedField> getComputedField(String dtoField, boolean ignoreCase) {
        return Arrays.stream(computedFields)
                .filter(c -> ignoreCase ? c.dtoField().equalsIgnoreCase(dtoField) : c.dtoField().equals(dtoField))
                .findFirst();
    }

    /**
     * Checks if a DTO field is a computed field.
     *
     * @param dtoField the DTO field name
     * @param ignoreCase whether field name should be compared equals ignoring case
     * @return true if computed, false otherwise
     */
    public boolean isComputedField(String dtoField, boolean ignoreCase) {
        return Arrays.stream(computedFields)
                .anyMatch(c -> ignoreCase ? c.dtoField().equalsIgnoreCase(dtoField) : c.dtoField().equals(dtoField));
    }

    /**
     * Checks if a DTO field is a direct mapping.
     *
     * @param dtoField the DTO field name
     * @param ignoreCase whether field name should be compared equals ignoring case
     * @return true if direct mapping, false otherwise
     */
    public boolean isDirectMapping(String dtoField, boolean ignoreCase) {
        return Arrays.stream(directMappings)
                .anyMatch(m -> ignoreCase ? m.dtoField().equalsIgnoreCase(dtoField) : m.dtoField().equals(dtoField));
    }
}



