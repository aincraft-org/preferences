package dev.mintychochip.preferences.api;

import java.util.Collection;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;

/**
 * Service entry point for registering and discovering preferences.
 *
 * <p>Implementations are loaded from Bukkit's service manager and are owned by the Preferences
 * plugin; hooking plugins must not construct an implementation.
 */
public interface PreferencesService {

    /**
     * Registers a preference owned by {@code owner}.
     *
     * <p>The {@code configure} callback must set name/scope, label, codec, and default value via
     * {@link PreferenceBuilder}; {@link PreferenceBuilder#validate()} is invoked before the
     * preference is published.
     *
     * @param owner plugin that owns the registration; used for lifecycle and unregister
     * @param type declared value type of the preference
     * @param configure fluent builder configuration; must not be {@code null}
     * @param <T> preference value type
     * @return handle for the registered preference
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalStateException if required builder fields are missing
     * @throws IllegalArgumentException if the derived {@link PreferenceKey} is invalid
     */
    <T> Preference<T> register(Plugin owner, Class<T> type, Consumer<PreferenceBuilder<T>> configure);

    /**
     * Returns every preference currently registered with the service.
     *
     * @return unmodifiable view of registered preferences; never {@code null}
     */
    Collection<? extends Preference<?>> all();

    /**
     * Removes all preferences registered by {@code plugin}.
     *
     * <p>Called automatically when a plugin disables; hooking plugins may invoke this explicitly
     * during teardown.
     *
     * @param plugin owning plugin whose preferences should be removed
     * @throws NullPointerException if {@code plugin} is {@code null}
     */
    void unregisterPlugin(Plugin plugin);
}
