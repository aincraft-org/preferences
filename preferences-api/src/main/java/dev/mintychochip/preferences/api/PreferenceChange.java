package dev.mintychochip.preferences.api;

import java.util.Objects;

/**
 * Payload delivered to per-preference {@link java.util.function.Consumer} change callbacks.
 *
 * <p>{@code oldValue} and {@code newValue} are stored-string forms produced by the preference's
 * {@link dev.mintychochip.preferences.api.codec.StorageCodec}, not typed Java values.
 *
 * @param key preference that changed
 * @param oldValue previous stored value
 * @param newValue new stored value
 */
public record PreferenceChange(PreferenceKey key, String oldValue, String newValue) {
    /**
     * Compact constructor rejecting null components.
     *
     * @throws NullPointerException if any component is {@code null}
     */
    public PreferenceChange {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(oldValue, "oldValue");
        Objects.requireNonNull(newValue, "newValue");
    }
}
