package io.github.cyfko.tests;

import io.github.cyfko.jpametamodel.api.ComputationProvider;
import io.github.cyfko.jpametamodel.api.ComputedField;
import io.github.cyfko.jpametamodel.api.DirectMapping;
import io.github.cyfko.jpametamodel.api.ProjectionMetadata;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProjectionMetadataTest {

    @Test
    void testConstructorValidation() {
        assertThrows(NullPointerException.class, () ->
            new ProjectionMetadata(null, new DirectMapping[]{}, new ComputedField[]{}, new ComputationProvider[]{})
        );
    }

    @Test
    void testGetAllRequiredEntityFields() {
        ProjectionMetadata metadata = new ProjectionMetadata(
            Object.class,
            new DirectMapping[]{
                    new DirectMapping("email", "email", String.class, Optional.empty()),
                    new DirectMapping("city", "address.city", String.class, Optional.empty())
            },
            new ComputedField[]{
                    new ComputedField("fullName", new String[]{"firstName", "lastName"}),
                    new ComputedField("age", new String[]{"birthDate"})
            },
            new ComputationProvider[]{}
        );

        List<String> required = metadata.getAllRequiredEntityFields();

        assertEquals(5, required.size());
        assertTrue(required.contains("email"));
        assertTrue(required.contains("address.city"));
        assertTrue(required.contains("firstName"));
        assertTrue(required.contains("lastName"));
        assertTrue(required.contains("birthDate"));
    }

    @Test
    void testGetAllRequiredEntityFieldsDeduplicates() {
        ProjectionMetadata metadata = new ProjectionMetadata(
            Object.class,
            new DirectMapping[]{
                    new DirectMapping("name", "firstName", String.class, Optional.empty())
            },
            new ComputedField[]{
                    new ComputedField("fullName", new String[]{"firstName", "lastName"})
            },
            new ComputationProvider[]{}
        );

        List<String> required = metadata.getAllRequiredEntityFields();

        // firstName should appear only once
        assertEquals(2, required.size());
        assertTrue(required.contains("firstName"));
        assertTrue(required.contains("lastName"));
    }

    @Test
    void testGetDirectMapping() {
        ProjectionMetadata metadata = new ProjectionMetadata(
            Object.class,
            new DirectMapping[]{
                    new DirectMapping("userEmail", "email", String.class, Optional.empty())
            },
            new ComputedField[]{},
            new ComputationProvider[]{}
        );

        assertTrue(metadata.getDirectMapping("userEmail", false).isPresent());
        assertEquals("email", metadata.getDirectMapping("userEmail", false).get().entityField());
        assertFalse(metadata.getDirectMapping("nonExistent", false).isPresent());
    }

    @Test
    void testGetComputedField() {
        ProjectionMetadata metadata = new ProjectionMetadata(
            Object.class,
            new DirectMapping[]{},
            new ComputedField[]{
                    new ComputedField("fullName", new String[]{"firstName", "lastName"})
            },
            new ComputationProvider[]{}
        );

        assertTrue(metadata.getComputedField("fullName",false).isPresent());
        assertEquals(2, metadata.getComputedField("fullName",false).get().dependencyCount());
        assertFalse(metadata.getComputedField("nonExistent",false).isPresent());
    }

    @Test
    void testIsComputedField() {
        ProjectionMetadata metadata = new ProjectionMetadata(
            Object.class,
            new DirectMapping[]{
                    new DirectMapping("email", "email", String.class, Optional.empty())
            },
            new ComputedField[]{
                    new ComputedField("fullName", new String[]{"firstName", "lastName"})
            },
            new ComputationProvider[]{}
        );

        assertTrue(metadata.isComputedField("fullName",false));
        assertFalse(metadata.isComputedField("email",false));
    }

    @Test
    void testIsDirectMapping() {
        ProjectionMetadata metadata = new ProjectionMetadata(
            Object.class,
            new DirectMapping[]{
                    new DirectMapping("email", "email", String.class, Optional.empty())
            },
            new ComputedField[]{
                    new ComputedField("fullName", new String[]{"firstName", "lastName"})
            },
            new ComputationProvider[]{}
        );

        assertTrue(metadata.isDirectMapping("email",false));
        assertFalse(metadata.isDirectMapping("fullName",false));
    }
}