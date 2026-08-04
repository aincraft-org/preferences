package dev.jlo.preferences.internal;

import dev.jlo.preferences.api.PreferenceKey;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class PreferenceRegistry {

    private final Map<PreferenceKey, RegisteredPreference<?>> prefs = new ConcurrentHashMap<>();
    private final List<Consumer<RegisteredPreference<?>>> onRegister = new CopyOnWriteArrayList<>();

    public void register(RegisteredPreference<?> pref) {
        if (prefs.putIfAbsent(pref.key(), pref) != null) {
            throw new IllegalStateException("preference already registered: " + pref.key().asString());
        }
        onRegister.forEach(h -> h.accept(pref));
    }

    public @Nullable RegisteredPreference<?> byKey(PreferenceKey key) {
        return prefs.get(key);
    }

    public Collection<RegisteredPreference<?>> all() {
        return List.copyOf(prefs.values());
    }

    public void unregisterNamespace(String namespace) {
        prefs.keySet().removeIf(k -> k.namespace().equals(namespace));
    }

    /** Registers a hook invoked for every RegisteredPreference, past and future. */
    public void onRegister(Consumer<RegisteredPreference<?>> hook) {
        onRegister.add(hook);
        all().forEach(hook);
    }
}
