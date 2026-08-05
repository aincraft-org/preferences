package dev.jlo.preferences.api;

import dev.jlo.preferences.api.codec.PreferenceCodec;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/**
 * Fluent registration configuration. Hooking plugins only use the fluent setters via
 * {@link PreferencesService#register}; the Preferences plugin reads the configured state
 * after {@link #validate()}.
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

    public PreferenceBuilder(String namespace, Class<T> type) {
        this.namespace = namespace;
        this.type = type;
    }

    public PreferenceBuilder<T> playerScoped(String name) {
        this.name = name;
        this.scope = PreferenceScope.PLAYER;
        return this;
    }

    public PreferenceBuilder<T> global(String name) {
        this.name = name;
        this.scope = PreferenceScope.GLOBAL;
        return this;
    }

    public PreferenceBuilder<T> label(Component label) {
        this.label = label;
        return this;
    }

    public PreferenceBuilder<T> description(Component description) {
        this.description = description;
        return this;
    }

    public PreferenceBuilder<T> codec(PreferenceCodec<T> codec) {
        this.codec = codec;
        return this;
    }

    public PreferenceBuilder<T> defaultValue(T defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public PreferenceBuilder<T> onChange(Consumer<PreferenceChange> onChange) {
        this.onChange = onChange;
        return this;
    }

    /** Ensures all required fields are set; throws if registration is incomplete. */
    public void validate() {
        if (name == null || scope == null || label == null || codec == null || defaultValue == null) {
            throw new IllegalStateException("name/scope/label/codec/defaultValue are all required");
        }
    }

    public PreferenceKey key() {
        return new PreferenceKey(namespace, name);
    }

    public PreferenceScope scope() {
        return scope;
    }

    public Component label() {
        return label;
    }

    public Component description() {
        return description;
    }

    public PreferenceCodec<T> codec() {
        return codec;
    }

    public Class<T> type() {
        return type;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public @Nullable Consumer<PreferenceChange> onChange() {
        return onChange;
    }
}
