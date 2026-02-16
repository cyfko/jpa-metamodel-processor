package io.github.cyfko.jpametamodel.processor;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.Locale;

/**
 * Utility class for common string transformations used in the FilterQL Spring Boot starter.
 * <p>
 * Provides methods for class name extraction and case conversions (PascalCase, camelCase, kebab-case)
 * to facilitate consistent naming conventions across generated code, metadata, and API endpoints.
 * </p>
 *
 * <h2>Usage Context</h2>
 * <ul>
 *   <li>Code generation (annotation processors, metadata registries)</li>
 *   <li>API schema exposure (naming of fields and entities)</li>
 *   <li>Internal normalization for property references</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * All methods are stateless and thread-safe.
 * </p>
 *
 * @author cyfko
 * @since 1.0
 */
public class StringUtils {
    /**
     * Extracts the simple class name from a fully qualified class name.
     * <p>
     * For example, {@code "com.example.MyEntity"} yields {@code "MyEntity"}.
     * </p>
     *
     * @param fullClassName fully qualified class name (e.g., {@code "com.example.MyEntity"})
     * @return simple class name (e.g., {@code "MyEntity"})
     * @throws NullPointerException if {@code fullClassName} is {@code null}
     */
    public static String getSimpleClassName(String fullClassName) {
        return fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
    }

    /**
     * Converts a string to camelCase.
     * <p>
     * Examples:
     * <ul>
     * <li>"Name" → "name"</li>
     * <li>"FirstName" → "firstName"</li>
     * <li>"URL" → "url"</li>
     * <li>"URLPath" → "urlPath"</li>
     * </ul>
     * </p>
     */
    public static String toCamelCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }

        // Cas spécial : acronyme en début (URL, ID, etc.)
        // URLPath → urlPath, URL → url
        int firstLowerIndex = -1;
        for (int i = 0; i < str.length(); i++) {
            if (Character.isLowerCase(str.charAt(i))) {
                firstLowerIndex = i;
                break;
            }
        }

        if (firstLowerIndex == -1) {
            // Tout en majuscules (ex: "URL")
            return str.toLowerCase();
        }

        if (firstLowerIndex == 1) {
            // Cas normal : Name → name
            return Character.toLowerCase(str.charAt(0)) + str.substring(1);
        }

        // Acronyme suivi de camelCase : URLPath → urlPath
        return str.substring(0, firstLowerIndex - 1).toLowerCase() + str.substring(firstLowerIndex - 1);
    }

    /**
     * Converts a camelCase string to kebab-case.
     * <p>
     * For example, {@code "myEntityName"} yields {@code "my-entity-name"}.
     * </p>
     *
     * @param camelCaseString input string in camelCase
     * @return kebab-case equivalent, or {@code null} if input is {@code null}
     * @see #toCamelCase(String)
     */
    public static String camelToKebabCase(String camelCaseString) {
        if (camelCaseString == null || camelCaseString.isEmpty()) {
            return camelCaseString;
        }
        // Use a regex to find uppercase letters that are not at the beginning of the string,
        // and replace them with a hyphen followed by the lowercase version of the letter.
        // The regex ([a-z0-9])([A-Z]) captures a lowercase letter/digit followed by an uppercase letter.
        // $1 refers to the first captured group (the lowercase letter/digit),
        // $2 refers to the second captured group (the uppercase letter).
        return camelCaseString
                .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Capitalizes the first character of the given string, leaving the remainder
     * unchanged.
     * <p>
     * This method is null-safe and returns the input as-is when the string is
     * {@code null}
     * or empty. It is typically used to build JavaBean-style accessor names from
     * field
     * identifiers, for example when resolving {@code getXxx} methods via
     * reflection.
     * </p>
     *
     * @param str the input string, possibly {@code null} or empty
     * @return the capitalized string, or the original value if {@code null} or
     *         empty
     */
    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * Retourne le nom qualifié de l'élément pour les messages d'erreur.
     */
    public static String getQualifiedName(ExecutableElement ee) {
        TypeElement enclosingClass = (TypeElement) ee.getEnclosingElement();
        return enclosingClass.getQualifiedName().toString() + "." + ee.getSimpleName() + "()";
    }

    public static boolean hasJavaNamingConvention(String name) {
        return name.startsWith("get") || name.startsWith("is");
    }

    /**
     * Extrait le nom de propriété à partir d'une méthode getter en suivant les conventions JavaBeans.
     *
     * Conventions supportées :
     * - getXxx() → "xxx" (types non-boolean)
     * - isXxx() → "xxx" (types boolean uniquement)
     * - hasXxx() → "xxx" (types boolean uniquement)
     *
     * @param ee L'élément exécutable (méthode) à analyser
     * @return Le nom de propriété en camelCase
     * @throws IllegalStateException si la méthode ne respecte pas les conventions
     */
    public static String toJavaNamingAwareFieldName(ExecutableElement ee) {
        String methodName = ee.getSimpleName().toString();
        TypeKind kind = ee.getReturnType().getKind();
        boolean isBoolean = kind == TypeKind.BOOLEAN || "java.lang.Boolean".equals(ee.getReturnType().toString());

        // Cas 1 : getXxx() - tous types sauf boolean
        if (methodName.startsWith("get")) {
            if (methodName.length() <= 3) {
                throw new IllegalStateException(
                        "Invalid getter name: '" + methodName + "()' in " + getQualifiedName(ee) + ". " +
                                "Getter must be in format 'getXxx()' where 'Xxx' is at least one character."
                );
            }

            if (isBoolean) {
                throw new IllegalStateException(
                        "Invalid getter for boolean: '" + methodName + "()' in " + getQualifiedName(ee) + ". " +
                                "Boolean getters must use 'is' or 'has' prefix, not 'get'. " +
                                "Expected: 'is" + methodName.substring(3) + "()' or 'has" + methodName.substring(3) + "()'."
                );
            }

            String propertyName = methodName.substring(3);
            validatePropertyNameFormat(propertyName, methodName, ee);
            return toCamelCase(propertyName);
        }

        // Cas 2 : isXxx() - boolean uniquement
        if (methodName.startsWith("is")) {
            if (methodName.length() <= 2) {
                throw new IllegalStateException(
                        "Invalid getter name: '" + methodName + "()' in " + getQualifiedName(ee) + ". " +
                                "Getter must be in format 'isXxx()' where 'Xxx' is at least one character."
                );
            }

            if (!isBoolean) {
                throw new IllegalStateException(
                        "Invalid 'is' prefix for non-boolean: '" + methodName + "()' returns " +
                                ee.getReturnType() + " in " + getQualifiedName(ee) + ". " +
                                "'is' prefix is reserved for boolean types. Use 'get" + methodName.substring(2) + "()' instead."
                );
            }

            String propertyName = methodName.substring(2);
            validatePropertyNameFormat(propertyName, methodName, ee);
            return toCamelCase(propertyName);
        }

        // Cas 3 : hasXxx() - boolean uniquement
        if (methodName.startsWith("has")) {
            if (methodName.length() <= 3) {
                throw new IllegalStateException(
                        "Invalid getter name: '" + methodName + "()' in " + getQualifiedName(ee) + ". " +
                                "Getter must be in format 'hasXxx()' where 'Xxx' is at least one character."
                );
            }

            if (!isBoolean) {
                throw new IllegalStateException(
                        "Invalid 'has' prefix for non-boolean: '" + methodName + "()' returns " +
                                ee.getReturnType() + " in " + getQualifiedName(ee) + ". " +
                                "'has' prefix is reserved for boolean types. Use 'get" + methodName.substring(3) + "()' instead."
                );
            }

            String propertyName = methodName.substring(3);
            validatePropertyNameFormat(propertyName, methodName, ee);
            return toCamelCase(propertyName);
        }

        // Cas 4 : Aucune convention respectée
        throw buildDetailedNamingException(methodName, isBoolean, ee);
    }

    /**
     * Valide que le nom de propriété extrait suit les conventions (première lettre majuscule).
     */
    private static void validatePropertyNameFormat(String propertyName, String methodName, ExecutableElement ee) {
        if (propertyName.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid getter name: '" + methodName + "()' in " + getQualifiedName(ee) + ". " +
                            "Property name cannot be empty after removing prefix."
            );
        }

        char firstChar = propertyName.charAt(0);
        if (!Character.isUpperCase(firstChar)) {
            throw new IllegalStateException(
                    "Invalid getter name: '" + methodName + "()' in " + getQualifiedName(ee) + ". " +
                            "First character after prefix must be uppercase (JavaBeans convention). " +
                            "Expected: '" + methodName.substring(0, methodName.length() - propertyName.length()) +
                            Character.toUpperCase(firstChar) + propertyName.substring(1) + "()'."
            );
        }

        // Vérifier qu'il n'y a pas de caractères spéciaux interdits
        if (!propertyName.matches("^[A-Z][a-zA-Z0-9]*$")) {
            throw new IllegalStateException(
                    "Invalid property name: '" + propertyName + "' derived from '" + methodName + "()' in " +
                            getQualifiedName(ee) + ". " +
                            "Property names must contain only alphanumeric characters and start with an uppercase letter after prefix."
            );
        }
    }

    /**
     * Construit une exception détaillée pour les méthodes ne respectant aucune convention.
     */
    private static IllegalStateException buildDetailedNamingException(
            String methodName,
            boolean isBoolean,
            ExecutableElement ee) {

        StringBuilder message = new StringBuilder();
        message.append("Method '").append(methodName).append("()' in ")
                .append(getQualifiedName(ee))
                .append(" doesn't follow JavaBeans naming convention.\n\n");

        if (isBoolean) {
            message.append("For boolean properties, use one of:\n");
            message.append("  ✓ is").append(capitalize(methodName)).append("()\n");
            message.append("  ✓ has").append(capitalize(methodName)).append("()\n\n");
            message.append("Examples:\n");
            message.append("  • isActive() for property 'active'\n");
            message.append("  • hasChildren() for property 'children'\n");
        } else {
            message.append("For non-boolean properties, use:\n");
            message.append("  ✓ get").append(capitalize(methodName)).append("()\n\n");
            message.append("Examples:\n");
            message.append("  • getId() for property 'id'\n");
            message.append("  • getName() for property 'name'\n");
            message.append("  • getCreatedAt() for property 'createdAt'\n");
        }

        message.append("\nCurrent return type: ").append(ee.getReturnType());
        message.append("\nSee: https://filterql.io/docs/naming-convention");

        return new IllegalStateException(message.toString());
    }
}