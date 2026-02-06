package io.github.cyfko.tests;

import io.github.cyfko.jpametamodel.api.DirectMapping;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DirectMappingTest {

    @Test
    void testConstructorValidation() {
        assertThrows(NullPointerException.class, () ->
            new DirectMapping(null, "entityField", null, Optional.empty())
        );

        assertThrows(NullPointerException.class, () ->
            new DirectMapping("dtoField", null, String.class, Optional.empty())
        );

        assertThrows(IllegalArgumentException.class, () ->
            new DirectMapping("", "entityField", String.class, Optional.empty())
        );

        assertThrows(IllegalArgumentException.class, () ->
            new DirectMapping("dtoField", "  ",  String.class, Optional.empty())
        );

        assertDoesNotThrow(() ->
                new DirectMapping("xx", "entityField", String.class, Optional.empty())
        );

        assertThrows(NullPointerException.class, () ->
                new DirectMapping("xx", "entityField", String.class, null)
        );
    }

    @Test
    void testSimpleMapping() {
        DirectMapping mapping = new DirectMapping("userEmail", "email", String.class, Optional.empty());

        assertEquals("userEmail", mapping.dtoField());
        assertEquals("email", mapping.entityField());
        assertFalse(mapping.isNested());
        assertEquals(0, mapping.nestingDepth());
        assertEquals("email", mapping.getRootField());
    }

    @Test
    void testNestedMapping() {
        DirectMapping mapping = new DirectMapping("city", "address.city", String.class, Optional.empty());

        assertTrue(mapping.isNested());
        assertEquals(1, mapping.nestingDepth());
        assertEquals("address", mapping.getRootField());
    }

    @Test
    void testDeeplyNestedMapping() {
        DirectMapping mapping = new DirectMapping("street", "user.address.street", String.class, Optional.empty());

        assertTrue(mapping.isNested());
        assertEquals(2, mapping.nestingDepth());
        assertEquals("user", mapping.getRootField());
    }
}