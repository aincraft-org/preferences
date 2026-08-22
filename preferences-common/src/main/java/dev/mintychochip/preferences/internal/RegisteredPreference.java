package dev.mintychochip.preferences.internal;

import dev.mintychochip.preferences.api.Preference;
import dev.mintychochip.preferences.api.PreferenceChange;
import dev.mintychochip.preferences.api.PreferenceKey;
import dev.mintychochip.preferences.api.PreferenceScope;
import dev.mintychochip.preferences.api.codec.PreferenceCodec;
import dev.mintychochip.preferences.api.event.PreferenceChangeEvent;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Runtime {@link Preference} implementation registered in {@link PreferenceRegistry}.
 *
 * <p>Lazy-loads stored values through {@link #storedValueLookup}, fires cancellable
 * {@link PreferenceChangeEvent}s before mutations, and persists via {@link #appliedHook}.
 */
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

    /**
     * Resolves a stored string for a composite lookup key
     * ({@code namespace\u0000target\u0000name}), or {@code null} if unset.
     *
     * <p>Wired by the plugin after registration. While {@code null}, lazy reads return
     * {@link #defaultValue()} and writes skip persistence.
     */
    public @Nullable Function<String, String> storedValueLookup; // returns stored string or null
    /**
     * Persists a successful, non-cancelled mutation and marks the namespace dirty for debounced
     * flush.
     *
     * <p>Wired by the plugin after registration. While {@code null}, in-memory caches update but
     * values are not written to storage.
     */
    public @Nullable Consumer<Applied> appliedHook; // persistence + dirty marking

    /**
     * Payload passed to {@link #appliedHook} after a successful, non-cancelled mutation.
     *
     * @param key preference that changed
     * @param scope scope of the stored row
     * @param player player UUID for player-scoped writes, or {@code null} for global
     * @param storedValue new persisted string form
     */
    public record Applied(PreferenceKey key, PreferenceScope scope, @Nullable UUID player, String storedValue) {}

    /**
     * Creates a registered preference with immutable metadata and an optional change callback.
     *
     * @param key stable preference identifier
     * @param scope player or global scope
     * @param label dialog label
     * @param description dialog description
     * @param codec storage and optional dialog codec bundle
     * @param type declared value type
     * @param defaultValue value used when no stored row exists
     * @param onChange optional per-preference callback; may be {@code null}
     * @throws NullPointerException if any required argument is {@code null}
     */
    public RegisteredPreference(PreferenceKey key, PreferenceScope scope, Component label,
                                Component description, PreferenceCodec<T> codec, Class<T> type,
                                T defaultValue, @Nullable Consumer<PreferenceChange> onChange) {
        this.key = Objects.requireNonNull(key, "key");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.label = Objects.requireNonNull(label, "label");
        this.description = Objects.requireNonNull(description, "description");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.type = Objects.requireNonNull(type, "type");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.onChange = onChange; // intentionally nullable
    }

    /** {@inheritDoc} */
    @Override
    public PreferenceKey key() {
        return key;
    }

    /** {@inheritDoc} */
    @Override
    public PreferenceScope scope() {
        return scope;
    }

    /** {@inheritDoc} */
    @Override
    public Class<T> type() {
        return type;
    }

    /** {@inheritDoc} */
    @Override
    public Component label() {
        return label;
    }

    /** {@inheritDoc} */
    @Override
    public Component description() {
        return description;
    }

    /** {@inheritDoc} */
    @Override
    public T defaultValue() {
        return defaultValue;
    }

    /** @return codec bundle used for persistence and optional dialog editing */
    public PreferenceCodec<T> codec() {
        return codec;
    }

    /**
     * Returns the current value for {@code player}.
     *
     * <p>Requires {@link PreferenceScope#PLAYER}. Lazily loads from {@link #storedValueLookup}
     * on first access per UUID, parsing via the preference {@link #codec()} and falling back to
     * {@link #defaultValue()} when unset or invalid.
     *
     * @param player player whose stored value is read
     * @return current typed value
     * @throws IllegalStateException if this preference is not player-scoped
     * @throws NullPointerException if {@code player} is {@code null}
     */
    @Override
    public T get(Player player) {
        checkScope(PreferenceScope.PLAYER);
        Objects.requireNonNull(player, "player");
        return playerValues.computeIfAbsent(player.getUniqueId(), uuid -> {
            String stored = storedValueLookup == null ? null
                : storedValueLookup.apply(key.namespace() + "\u0000" + uuid + "\u0000" + key.name());
            return parseOrDefault(stored);
        });
    }

    /**
     * Returns the current global value.
     *
     * <p>Requires {@link PreferenceScope#GLOBAL}. Uses double-checked locking to lazily load from
     * {@link #storedValueLookup}, parsing via the preference {@link #codec()} and falling back to
     * {@link #defaultValue()} when unset or invalid.
     *
     * @return current typed global value
     * @throws IllegalStateException if this preference is not global-scoped
     */
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

    /**
     * Sets the value for {@code player} after a cancellable change event.
     *
     * <p>Requires {@link PreferenceScope#PLAYER}. Serializes with the preference
     * {@link #codec()} before persistence.
     *
     * @param player player whose value is updated
     * @param newValue new typed value
     * @throws IllegalStateException if this preference is not player-scoped
     * @throws IllegalArgumentException if {@code newValue} is not an instance of {@link #type()}
     * @throws NullPointerException if any argument is {@code null}
     */
    @Override
    public void set(Player player, T newValue) {
        checkScope(PreferenceScope.PLAYER);
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(newValue, "value");
        apply(player, player.getUniqueId(), newValue);
    }

    /**
     * Sets the global value programmatically after a cancellable change event.
     *
     * <p>Requires {@link PreferenceScope#GLOBAL}. The fired event's editor is {@code null}.
     *
     * @param newValue new typed value
     * @throws IllegalStateException if this preference is not global-scoped
     * @throws IllegalArgumentException if {@code newValue} is not an instance of {@link #type()}
     * @throws NullPointerException if {@code newValue} is {@code null}
     */
    @Override
    public void setGlobal(T newValue) {
        checkScope(PreferenceScope.GLOBAL);
        Objects.requireNonNull(newValue, "value");
        apply(null, null, newValue); // editor intentionally null (programmatic)
    }

    /**
     * Sets the global value and attributes the change to {@code editor}.
     *
     * <p>Requires {@link PreferenceScope#GLOBAL}. Used when an admin saves from a dialog; the
     * event's editor is {@code editor}'s UUID.
     *
     * @param editor player performing the edit
     * @param newValue new typed value
     * @throws IllegalStateException if this preference is not global-scoped
     * @throws IllegalArgumentException if {@code newValue} is not an instance of {@link #type()}
     * @throws NullPointerException if any argument is {@code null}
     */
    @Override
    public void setGlobal(Player editor, T newValue) {
        checkScope(PreferenceScope.GLOBAL);
        Objects.requireNonNull(editor, "editor");
        Objects.requireNonNull(newValue, "value");
        apply(null, editor.getUniqueId(), newValue);
    }

    /**
     * Resets the player's stored value to {@link #defaultValue()}.
     *
     * @param player player whose value is reset
     * @throws NullPointerException if {@code player} is {@code null}
     */
    @Override
    public void reset(Player player) {
        Objects.requireNonNull(player, "player");
        set(player, defaultValue);
    }

    /**
     * Resets the global stored value to {@link #defaultValue()}.
     *
     * @throws IllegalStateException if this preference is not global-scoped
     */
    @Override
    public void resetGlobal() {
        setGlobal(defaultValue);
    }

    /**
     * @param scopePlayer player whose value is being set (null for global)
     * @param editor UUID attributed on {@link PreferenceChangeEvent} (may be set for global GUI edits)
     */
    private void apply(@Nullable Player scopePlayer, @Nullable UUID editor, T newValue) {
        // newValue already requireNonNull'd at public boundary
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

    /**
     * Session/dialog support: evict cached value so next read reloads from store.
     *
     * @param uuid player whose cached value is evicted
     * @throws NullPointerException if {@code uuid} is null
     */
    public void invalidatePlayer(UUID uuid) {
        playerValues.remove(Objects.requireNonNull(uuid, "uuid"));
    }
}
