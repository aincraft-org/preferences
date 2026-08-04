package dev.jlo.preferences.api;

import dev.jlo.preferences.api.codec.PreferenceCodec;
import dev.jlo.preferences.internal.RegisteredPreference;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;

public final class PreferenceBuilder<T> {

    private final String namespace;
    private final Class<T> type;
    private String name;
    private PreferenceScope scope;
    private Component label;
    private Component description = Component.empty();
    private PreferenceCodec<T> codec;
    private T defaultValue;
    private Consumer<PreferenceChange> onChange;

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

    public Preference<T> build() {
        if (name == null || scope == null || label == null || codec == null || defaultValue == null) {
            throw new IllegalStateException("name/scope/label/codec/defaultValue are all required");
        }
        return new RegisteredPreference<>(new PreferenceKey(namespace, name), scope, label,
            description, codec, type, defaultValue, onChange);
    }
}
