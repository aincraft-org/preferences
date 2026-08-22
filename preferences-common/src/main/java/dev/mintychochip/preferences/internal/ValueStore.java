package dev.mintychochip.preferences.internal;

/**
 * Internal storage boundary for preference values.
 *
 * <p>The common module currently has one implementation, {@link YamlValueStore}; this
 * interface keeps callers independent of its on-disk representation.
 */
public interface ValueStore {}
