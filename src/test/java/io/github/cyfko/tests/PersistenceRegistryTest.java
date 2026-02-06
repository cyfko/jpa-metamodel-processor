package io.github.cyfko.tests;

import io.github.cyfko.jpametamodel.PersistenceRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MetadataRegistry utility methods.
 * Tests that require a generated registry are in EntityRegistryProcessorTest.
 */
class PersistenceRegistryTest {

    @Test
    void cannotInstantiateUtilityClass() throws Exception {
        java.lang.reflect.Constructor<?> constructor = PersistenceRegistry.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        try {
            constructor.newInstance();
            fail("Should have thrown UnsupportedOperationException");
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Unwrap the actual exception
            Throwable cause = e.getCause();
            assertTrue(cause instanceof UnsupportedOperationException);
            assertTrue(cause.getMessage().contains("utility class"));
        }
    }

    @Test
    void getMetadataForNullThrowsNullPointerException() {
        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                PersistenceRegistry.getMetadataFor(null)
        );
        assertEquals("Entity class cannot be null", exception.getMessage());
    }

    @Test
    void isEntityRegisteredNullThrowsNullPointerException() {
        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                PersistenceRegistry.isEntityRegistered(null)
        );
        assertEquals("Entity class cannot be null", exception.getMessage());
    }

    @Test
    void getFieldMetadataNullEntityThrowsNullPointerException() {
        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                PersistenceRegistry.getFieldMetadata(null, "fieldName")
        );
        assertEquals("Entity class cannot be null", exception.getMessage());
    }

    @Test
    void getFieldMetadataNullFieldNameThrowsNullPointerException() {
        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                PersistenceRegistry.getFieldMetadata(String.class, null)
        );
        assertEquals("Field name cannot be null", exception.getMessage());
    }

    @Test
    void metadataRegistryClassIsFinal() {
        assertTrue(java.lang.reflect.Modifier.isFinal(PersistenceRegistry.class.getModifiers()));
    }

    @Test
    void allMethodsAreStatic() {
        java.lang.reflect.Method[] methods = PersistenceRegistry.class.getDeclaredMethods();

        for (java.lang.reflect.Method method : methods) {
            // Skip synthetic methods (like lambda methods)
            if (!method.isSynthetic()) {
                assertTrue(
                        java.lang.reflect.Modifier.isStatic(method.getModifiers()),
                        "Method " + method.getName() + " should be static"
                );
            }
        }
    }
}