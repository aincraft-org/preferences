package dev.jlo.preferences.internal;

import dev.jlo.preferences.api.Preference;
import dev.jlo.preferences.api.PreferenceBuilder;
import dev.jlo.preferences.api.PreferencesService;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/** Default {@link PreferencesService} implementation, registered by the plugin in onEnable. */
public final class PreferencesServiceImpl implements PreferencesService {

    private final PreferenceRegistry registry;
    private final @Nullable Consumer<String> beforeUnregisterNamespace;

    public PreferencesServiceImpl(PreferenceRegistry registry) {
        this(registry, null);
    }

    /**
     * @param beforeUnregisterNamespace invoked with the plugin namespace before registry removal
     *                                  (flush pending writes + close dialog sessions).
     */
    public PreferencesServiceImpl(PreferenceRegistry registry,
                                  @Nullable Consumer<String> beforeUnregisterNamespace) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.beforeUnregisterNamespace = beforeUnregisterNamespace; // intentionally nullable
    }

    @Override
    public <T> Preference<T> register(Plugin owner, Class<T> type, Consumer<PreferenceBuilder<T>> configure) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(configure, "configure");
        PreferenceBuilder<T> builder = new PreferenceBuilder<>(owner.getName().toLowerCase(Locale.ROOT), type);
        configure.accept(builder);
        builder.validate();
        RegisteredPreference<T> pref = new RegisteredPreference<>(
            builder.key(),
            builder.scope(),
            builder.label(),
            builder.description(),
            builder.codec(),
            builder.type(),
            builder.defaultValue(),
            builder.onChange());
        registry.register(pref);
        return pref;
    }

    @Override
    public Collection<? extends Preference<?>> all() {
        return registry.all();
    }

    @Override
    public void unregisterPlugin(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        String ns = plugin.getName().toLowerCase(Locale.ROOT);
        if (beforeUnregisterNamespace != null) {
            beforeUnregisterNamespace.accept(ns);
        }
        registry.unregisterNamespace(ns);
    }
}
