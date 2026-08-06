package dev.jlo.preferences;

import static org.junit.jupiter.api.Assertions.*;

import dev.jlo.preferences.api.PreferenceKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PreferenceKeyTest {

    @Test void acceptsValidNames() {
        PreferenceKey key = new PreferenceKey("my-plugin", "draw_distance");
        assertEquals("my-plugin:draw_distance", key.asString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bad", "has space", "dot.name", "..", "a/b", "a\\b", "a:b", "a.b"})
    void rejectsBadNamespace(String ns) {
        assertThrows(IllegalArgumentException.class, () -> new PreferenceKey(ns, "ok"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bad", "has space", "dot.name", "..", "a/b", "a\\b", "a:b"})
    void rejectsBadName(String name) {
        assertThrows(IllegalArgumentException.class, () -> new PreferenceKey("demo", name));
    }
}
