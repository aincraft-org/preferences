package dev.jlo.preferences.api;

import java.util.Objects;

public record PreferenceKey(String namespace, String name) {
    public PreferenceKey {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        // No dots: dot is Bukkit YamlConfiguration's path separator and would corrupt storage keys.
        if (!namespace.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("bad namespace: " + namespace);
        }
        if (!name.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("bad preference name: " + name);
        }
    }

    public String asString() {
        return namespace + ":" + name;
    }
}
