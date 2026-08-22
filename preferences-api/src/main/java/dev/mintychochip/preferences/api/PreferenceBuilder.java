package dev.mintychochip.preferences.api;

import dev.mintychochip.preferences.api.codec.PreferenceCodec;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/**
 * Fluent registration configuration for a single preference.
 *
 * <p>Hooking plugins configure instances via {@link PreferencesService#register}; the Preferences
 * plugin reads the configured state after {@link #validate()}.
 *
 * @param <T> preference value type
 */
public final class PreferenceBuilder<T> {

    private final String namespace;
    private final Class<T> type;
    private String name;
    private PreferenceScope scope;
    private Component label;
    private Component description = Component.empty();
    private PreferenceCodec<T> codec;
    private T defaultValue;
    private @Nullable Consumer<PreferenceChange> onChange;

    /**
     * Creates a builder for the given plugin namespace and value type.
     *
     * @param namespace plugin namespace used in the derived {@link PreferenceKey}
     * @param type declared preference value type
     * @throws NullPointerException if {@code namespace} or {@code type} is {@code null}
     */
    public PreferenceBuilder(String namespace, Class<T> type) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.type = Objects.requireNonNull(type, "type");
    }

    /**
     * Configures a per-player preference with the given name.
     *
     * @param name preference name within the namespace
     * @return this builder
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public PreferenceBuilder<T> playerScoped(String name) {
        this.name = Objects.requireNonNull(name, "name");
        this.scope = PreferenceScope.PLAYER;
        return this;
    }

    /**
     * Configures a global preference with the given name.
     *
     * @param name preference name within the namespace
     * @return this builder
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public PreferenceBuilder<T> global(String name) {
        this.name = Objects.requireNonNull(name, "name");
        this.scope = PreferenceScope.GLOBAL;
        return this;
    }

    /**
     * Sets the dialog label shown to players.
     *
     * @param label non-empty display label
     * @return this builder
     * @throws NullPointerException if {@code label} is {@code null}
     */
    public PreferenceBuilder<T> label(Component label) {
        this.label = Objects.requireNonNull(label, "label");
        return this;
    }

    /**
     * Sets optional descriptive text shown beneath the label.
     *
     * @param description description component; use {@link Component#empty()} when omitted
     * @return this builder
     * @throws NullPointerException if {@code description} is {@code null}
     */
    public PreferenceBuilder<T> description(Component description) {
        this.description = Objects.requireNonNull(description, "description");
        return this;
    }

    /**
     * Sets the codec used for persistence and optional dialog editing.
     *
     * @param codec storage and dialog adapter bundle
     * @return this builder
     * @throws NullPointerException if {@code codec} is {@code null}
     */
    public PreferenceBuilder<T> codec(PreferenceCodec<T> codec) {
        this.codec = Objects.requireNonNull(codec, "codec");
        return this;
    }

    /**
     * Sets the value used when no stored value exists.
     *
     * @param defaultValue default preference value
     * @return this builder
     * @throws NullPointerException if {@code defaultValue} is {@code null}
     */
    public PreferenceBuilder<T> defaultValue(T defaultValue) {
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        return this;
    }

    /**
     * Registers a callback invoked after a successful change.
     *
     * <p>The callback receives stored-string values via {@link PreferenceChange}. Omitting this
     * setter leaves the callback unset ({@code null}).
     *
     * @param onChange change listener
     * @return this builder
     * @throws NullPointerException if {@code onChange} is {@code null}
     */
    public PreferenceBuilder<T> onChange(Consumer<PreferenceChange> onChange) {
        this.onChange = Objects.requireNonNull(onChange, "onChange");
        return this;
    }

    /**
     * Ensures all required fields are set.
     *
     * @throws IllegalStateException if name, scope, label, codec, or default value is unset
     */
    public void validate() {
        if (name == null || scope == null || label == null || codec == null || defaultValue == null) {
            throw new IllegalStateException("name/scope/label/codec/defaultValue are all required");
        }
    }

    /** @return derived preference key from namespace and configured name */
    public PreferenceKey key() {
        return new PreferenceKey(namespace, name);
    }

    /** @return configured scope, or {@code null} before {@link #playerScoped(String)} or {@link #global(String)} */
    public PreferenceScope scope() {
        return scope;
    }

    /** @return configured label, or {@code null} before {@link #label(Component)} */
    public Component label() {
        return label;
    }

    /** @return configured description; defaults to {@link Component#empty()} */
    public Component description() {
        return description;
    }

    /** @return configured codec, or {@code null} before {@link #codec(PreferenceCodec)} */
    public PreferenceCodec<T> codec() {
        return codec;
    }

    /** @return declared value type supplied at construction */
    public Class<T> type() {
        return type;
    }

    /** @return configured default value, or {@code null} before {@link #defaultValue(Object)} */
    public T defaultValue() {
        return defaultValue;
    }

    /**
     * @return optional change callback, or {@code null} when never configured
     */
    public @Nullable Consumer<PreferenceChange> onChange() {
        return onChange;
    }
}
