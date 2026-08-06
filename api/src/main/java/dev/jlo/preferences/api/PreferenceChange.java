package dev.jlo.preferences.api;

import java.util.Objects;

/** Payload for per-preference change callbacks. Values are stored-string form. */
public record PreferenceChange(PreferenceKey key, String oldValue, String newValue) {
    public PreferenceChange {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(oldValue, "oldValue");
        Objects.requireNonNull(newValue, "newValue");
    }
}
