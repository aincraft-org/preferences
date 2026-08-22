package dev.mintychochip.preferences.internal;

import dev.mintychochip.preferences.api.PreferenceKey;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Thread-safe registry of {@link RegisteredPreference} instances keyed by {@link PreferenceKey}.
 *
 * <p>Registration is unique per key: duplicate keys throw {@link IllegalStateException}.
 * {@link #onRegister(Consumer)} hooks receive every preference registered before or after the
 * hook is installed. Namespace removal drops all keys in that namespace but does not invoke
 * storage flush or dialog teardown — callers orchestrate that via {@link PreferencesServiceImpl}.
 */
public final class PreferenceRegistry {

    private final Map<PreferenceKey, RegisteredPreference<?>> prefs = new ConcurrentHashMap<>();
    private final List<Consumer<RegisteredPreference<?>>> onRegister = new CopyOnWriteArrayList<>();

    /**
     * Registers a preference, rejecting duplicate keys.
     *
     * <p>Invokes every {@link #onRegister(Consumer)} hook after the key is stored. Hooks run on
     * the caller's thread.
     *
     * @param pref preference to register
     * @throws NullPointerException if {@code pref} is null
     * @throws IllegalStateException if the key is already registered
     */
    public void register(RegisteredPreference<?> pref) {
        Objects.requireNonNull(pref, "pref");
        if (prefs.putIfAbsent(pref.key(), pref) != null) {
            throw new IllegalStateException("preference already registered: " + pref.key().asString());
        }
        onRegister.forEach(h -> h.accept(pref));
    }

    /**
     * Looks up a registered preference by key.
     *
     * @param key preference key
     * @return the registered preference, or {@code null} if absent
     * @throws NullPointerException if {@code key} is null
     */
    public @Nullable RegisteredPreference<?> byKey(PreferenceKey key) {
        Objects.requireNonNull(key, "key");
        return prefs.get(key);
    }

    /**
     * Returns an immutable snapshot of all registered preferences.
     *
     * @return immutable collection of registered preferences
     */
    public Collection<RegisteredPreference<?>> all() {
        return List.copyOf(prefs.values());
    }

    /**
     * Removes every preference whose key namespace matches.
     *
     * @param namespace plugin namespace (lowercase)
     * @throws NullPointerException if {@code namespace} is null
     */
    public void unregisterNamespace(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        prefs.keySet().removeIf(k -> k.namespace().equals(namespace));
    }

    /**
     * Registers a hook invoked for every {@link RegisteredPreference}, past and future.
     *
     * <p>The hook is called immediately for every preference already registered, then again for
     * each subsequent {@link #register(RegisteredPreference)}. Hooks are retained for the lifetime
     * of the registry.
     *
     * @param hook consumer invoked immediately for existing prefs and on each new registration
     * @throws NullPointerException if {@code hook} is null
     */
    public void onRegister(Consumer<RegisteredPreference<?>> hook) {
        Objects.requireNonNull(hook, "hook");
        onRegister.add(hook);
        all().forEach(hook);
    }
}
