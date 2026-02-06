package io.github.cyfko.tests;

import io.github.cyfko.jpametamodel.api.ComputedField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComputedFieldTest {

    @Test
    void testConstructorValidation() {
        assertThrows(NullPointerException.class, () -> new ComputedField(null, new String[] { "dep1" }));

        assertThrows(NullPointerException.class, () -> new ComputedField("field", null));

        assertThrows(IllegalArgumentException.class, () -> new ComputedField("", new String[] { "dep1" }));

        assertThrows(IllegalArgumentException.class, () -> new ComputedField("field", new String[] {}));

        assertThrows(IllegalArgumentException.class,
                () -> new ComputedField("field", new String[] { "machin" }, (String) null));

        assertThrows(IllegalArgumentException.class,
                () -> new ComputedField("field", new String[] { "truc" }, (Class<?>) null));

        // Empty reducers with null methodReference is valid
        assertDoesNotThrow(
                () -> new ComputedField("field", new String[] { "truc" }, new ComputedField.ReducerMapping[0], null));

        assertDoesNotThrow(() -> new ComputedField("field", new String[] { "truc" }, Object.class, null));

        assertDoesNotThrow(() -> new ComputedField("field", new String[] { "x" }, null, "smth"));

        assertDoesNotThrow(() -> new ComputedField("field", new String[] { "something" },
                new ComputedField.ReducerMapping[0], null));
    }

    @Test
    void testDependsOn() {
        ComputedField computed = new ComputedField("fullName", new String[] { "firstName", "lastName" });

        assertTrue(computed.dependsOn("firstName"));
        assertTrue(computed.dependsOn("lastName"));
        assertFalse(computed.dependsOn("email"));
    }

    @Test
    void testDependencyCount() {
        ComputedField computed1 = new ComputedField("field1", new String[] { "dep1" });
        ComputedField computed2 = new ComputedField("field2", new String[] { "dep1", "dep2", "dep3" });

        assertEquals(1, computed1.dependencyCount());
        assertEquals(3, computed2.dependencyCount());
    }

    @Test
    void testHasNestedDependencies() {
        ComputedField simple = new ComputedField("fullName", new String[] { "firstName", "lastName" });
        ComputedField nested = new ComputedField("addressInfo", new String[] { "address.city", "address.zipCode" });
        ComputedField mixed = new ComputedField("mixed", new String[] { "name", "address.city" });

        assertFalse(simple.hasNestedDependencies());
        assertTrue(nested.hasNestedDependencies());
        assertTrue(mixed.hasNestedDependencies());
    }
}