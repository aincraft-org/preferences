package dev.mintychochip.preferences.api.codec;

/**
 * Converts between a preference's typed value and its persisted string form.
 *
 * <p>Implementations must round-trip values written by {@link #write(Object)} through
 * {@link #parse(String)}. Invalid stored strings should be rejected with
 * {@link IllegalArgumentException}.
 *
 * @param <T> preference value type
 */
public interface StorageCodec<T> {
    /**
     * Parses a stored string into a typed value.
     *
     * @param stored persisted value; never {@code null}
     * @return parsed value
     * @throws NullPointerException if {@code stored} is {@code null}
     * @throws IllegalArgumentException if {@code stored} is not a valid encoding
     */
    T parse(String stored);

    /**
     * Serializes a typed value to its stored string form.
     *
     * @param value value to persist; never {@code null}
     * @return stored representation
     * @throws NullPointerException if {@code value} is {@code null}
     */
    String write(T value);
}
