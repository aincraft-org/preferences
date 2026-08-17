package dev.mintychochip.preferences.api;

import java.util.Collection;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;

/**
 * Loaded via Bukkit.getServicesManager().load(PreferencesService.class); implemented by this
 * plugin, never constructed by hooking plugins.
 */
public interface PreferencesService {

    <T> Preference<T> register(Plugin owner, Class<T> type, Consumer<PreferenceBuilder<T>> configure);

    Collection<? extends Preference<?>> all();

    void unregisterPlugin(Plugin plugin);
}
