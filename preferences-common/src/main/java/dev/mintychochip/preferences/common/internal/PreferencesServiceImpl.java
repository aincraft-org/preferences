package dev.mintychochip.preferences.common.internal;

import dev.mintychochip.preferences.api.Preference;
import dev.mintychochip.preferences.api.PreferenceBuilder;
import dev.mintychochip.preferences.api.PreferencesService;
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

    /**
     * Creates a service with no namespace teardown hook.
     *
     * @param registry backing preference registry
     */
    public PreferencesServiceImpl(PreferenceRegistry registry) {
        this(registry, null);
    }

    /**
     * Creates a service with an optional namespace teardown hook.
     *
     * @param registry backing preference registry
     * @param beforeUnregisterNamespace invoked with the plugin namespace before registry removal
     *                                  (flush pending writes + close dialog sessions); may be {@code null}
     */
    public PreferencesServiceImpl(PreferenceRegistry registry,
                                  @Nullable Consumer<String> beforeUnregisterNamespace) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.beforeUnregisterNamespace = beforeUnregisterNamespace; // intentionally nullable
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public Collection<? extends Preference<?>> all() {
        return registry.all();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Invokes the optional teardown hook (flush + dialog close) before registry removal.
     */
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
