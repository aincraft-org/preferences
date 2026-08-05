package dev.jlo.preferences.internal;

import dev.jlo.preferences.api.Preference;
import dev.jlo.preferences.api.PreferenceBuilder;
import dev.jlo.preferences.api.PreferencesService;
import java.util.Collection;
import java.util.Locale;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;

/** Default {@link PreferencesService} implementation, registered by the plugin in onEnable. */
public final class PreferencesServiceImpl implements PreferencesService {

    private final PreferenceRegistry registry;

    public PreferencesServiceImpl(PreferenceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public <T> Preference<T> register(Plugin owner, Class<T> type, Consumer<PreferenceBuilder<T>> configure) {
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
        registry.unregisterNamespace(plugin.getName().toLowerCase(Locale.ROOT));
    }
}
