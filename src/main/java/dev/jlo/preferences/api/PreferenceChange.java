package dev.jlo.preferences.api;

/** Payload for per-preference change callbacks. Values are stored-string form. */
public record PreferenceChange(PreferenceKey key, String oldValue, String newValue) {}
