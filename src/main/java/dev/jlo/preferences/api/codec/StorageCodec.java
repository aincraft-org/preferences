package dev.jlo.preferences.api.codec;

/** Converts between a preference's typed value and its stored string form. */
public interface StorageCodec<T> {
    /** Parse a stored string. Throws IllegalArgumentException on invalid input. */
    T parse(String stored);
    /** Serialize to the stored string form. Must round-trip with parse(). */
    String write(T value);
}
