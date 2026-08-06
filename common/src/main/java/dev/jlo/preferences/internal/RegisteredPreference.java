package dev.jlo.preferences.internal;

import dev.jlo.preferences.api.Preference;
import dev.jlo.preferences.api.PreferenceChange;
import dev.jlo.preferences.api.PreferenceKey;
import dev.jlo.preferences.api.PreferenceScope;
import dev.jlo.preferences.api.codec.PreferenceCodec;
import dev.jlo.preferences.api.event.PreferenceChangeEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

public final class RegisteredPreference<T> implements Preference<T> {

    private final PreferenceKey key;
    private final PreferenceScope scope;
    private final Component label;
    private final Component description;
    private final PreferenceCodec<T> codec;
    private final Class<T> type;
    private final T defaultValue;
    private final @Nullable Consumer<PreferenceChange> onChange;
    private final Map<UUID, T> playerValues = new ConcurrentHashMap<>();
    private final Object globalLock = new Object();
    private volatile @Nullable T globalValue;

    /** Callbacks injected by the plugin wiring (storage + event bridge). */
    public @Nullable Function<String, String> storedValueLookup; // returns stored string or null
    public @Nullable Consumer<Applied> appliedHook; // persistence + dirty marking

    public record Applied(PreferenceKey key, PreferenceScope scope, @Nullable UUID player, String storedValue) {}

    public RegisteredPreference(PreferenceKey key, PreferenceScope scope, Component label,
                                Component description, PreferenceCodec<T> codec, Class<T> type,
                                T defaultValue, @Nullable Consumer<PreferenceChange> onChange) {
        this.key = key;
        this.scope = scope;
        this.label = label;
        this.description = description;
        this.codec = codec;
        this.type = type;
        this.defaultValue = defaultValue;
        this.onChange = onChange;
    }

    @Override
    public PreferenceKey key() {
        return key;
    }

    @Override
    public PreferenceScope scope() {
        return scope;
    }

    @Override
    public Class<T> type() {
        return type;
    }

    @Override
    public Component label() {
        return label;
    }

    @Override
    public Component description() {
        return description;
    }

    @Override
    public T defaultValue() {
        return defaultValue;
    }

    public PreferenceCodec<T> codec() {
        return codec;
    }

    @Override
    public T get(Player player) {
        checkScope(PreferenceScope.PLAYER);
        return playerValues.computeIfAbsent(player.getUniqueId(), uuid -> {
            String stored = storedValueLookup == null ? null
                : storedValueLookup.apply(key.namespace() + "\u0000" + uuid + "\u0000" + key.name());
            return parseOrDefault(stored);
        });
    }

    @Override
    public T getGlobal() {
        checkScope(PreferenceScope.GLOBAL);
        T value = globalValue;
        if (value == null) {
            synchronized (globalLock) {
                value = globalValue;
                if (value == null) {
                    String stored = storedValueLookup == null ? null
                        : storedValueLookup.apply(key.namespace() + "\u0000\ud83c\udf10\u0000" + key.name());
                    value = parseOrDefault(stored);
                    globalValue = value;
                }
            }
        }
        return value;
    }

    @Override
    public void set(Player player, T newValue) {
        checkScope(PreferenceScope.PLAYER);
        apply(player, player.getUniqueId(), newValue);
    }

    @Override
    public void setGlobal(T newValue) {
        checkScope(PreferenceScope.GLOBAL);
        apply(null, null, newValue);
    }

    @Override
    public void setGlobal(Player editor, T newValue) {
        checkScope(PreferenceScope.GLOBAL);
        apply(null, editor.getUniqueId(), newValue);
    }

    @Override
    public void reset(Player player) {
        set(player, defaultValue);
    }

    @Override
    public void resetGlobal() {
        setGlobal(defaultValue);
    }

    /**
     * @param scopePlayer player whose value is being set (null for global)
     * @param editor UUID attributed on {@link PreferenceChangeEvent} (may be set for global GUI edits)
     */
    private void apply(@Nullable Player scopePlayer, @Nullable UUID editor, T newValue) {
        if (!type.isInstance(newValue)) {
            throw new IllegalArgumentException("value is not " + type.getSimpleName());
        }
        // Force the lazy load of the current value first so a cold cache reports the true stored
        // value, not the default. get()/getGlobal() are the lazy-loading paths and fire no events.
        T current = (scopePlayer == null) ? getGlobal() : get(scopePlayer);
        String newStored = codec.storage().write(newValue);
        String oldStored = codec.storage().write(current);

        PreferenceChangeEvent event = new PreferenceChangeEvent(key, oldStored, newStored, editor);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        if (scopePlayer == null) {
            globalValue = newValue;
        } else {
            playerValues.put(scopePlayer.getUniqueId(), newValue);
        }

        if (appliedHook != null) {
            UUID storedPlayer = scopePlayer == null ? null : scopePlayer.getUniqueId();
            appliedHook.accept(new Applied(key, scope, storedPlayer, newStored));
        }
        if (onChange != null) {
            onChange.accept(new PreferenceChange(key, oldStored, newStored));
        }
    }

    private T parseOrDefault(@Nullable String stored) {
        if (stored == null) {
            return defaultValue;
        }
        try {
            return codec.storage().parse(stored);
        } catch (RuntimeException e) {
            Bukkit.getLogger().warning("Invalid stored value for " + key.asString() + ": '" + stored + "'; using default");
            return defaultValue;
        }
    }

    private void checkScope(PreferenceScope expected) {
        if (scope != expected) {
            throw new IllegalStateException(key.asString() + " is " + scope + "-scoped");
        }
    }

    /** Session/dialog support: evict cached value so next read reloads from store. */
    public void invalidatePlayer(UUID uuid) {
        playerValues.remove(uuid);
    }
}
