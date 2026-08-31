package dev.mintychochip.preferences.api;

import com.google.common.base.Preconditions;

/**
 * Stable identifier for a registered preference, composed of a plugin namespace and preference name.
 *
 * <p>Both components must match {@code [a-z0-9_-]+}. Dots are forbidden because Bukkit
 * {@code YamlConfiguration} uses {@code .} as a path separator and would corrupt storage keys.
 *
 * @param namespace owning plugin namespace, for example {@code "my-plugin"}
 * @param name preference name within the namespace, for example {@code "draw_distance"}
 */
public record PreferenceKey(String namespace, String name) {
    /**
     * Compact constructor validating non-null components and allowed character sets.
     *
     * @throws NullPointerException if {@code namespace} or {@code name} is {@code null}
     * @throws IllegalArgumentException if either component contains disallowed characters
     */
    public PreferenceKey {
        Preconditions.checkNotNull(namespace, "namespace");
        Preconditions.checkNotNull(name, "name");
        // No dots: dot is Bukkit YamlConfiguration's path separator and would corrupt storage keys.
        if (!namespace.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("bad namespace: " + namespace);
        }
        if (!name.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("bad preference name: " + name);
        }
    }

    /**
     * Returns the canonical string form {@code namespace:name}.
     *
     * @return colon-separated key suitable for maps and storage indexes
     */
    public String asString() {
        return namespace + ":" + name;
    }
}
