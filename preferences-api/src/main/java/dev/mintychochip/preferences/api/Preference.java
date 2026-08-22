package dev.mintychochip.preferences.api;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Typed handle for a registered preference.
 *
 * <p>Values are persisted through the preference's {@link dev.mintychochip.preferences.api.codec.StorageCodec}.
 * Mutating methods fire {@link dev.mintychochip.preferences.api.event.PreferenceChangeEvent} before
 * persistence; listeners may cancel the change.
 */
public interface Preference<T> {
    /** @return stable preference identifier */
    PreferenceKey key();

    /** @return whether this preference is per-player or global */
    PreferenceScope scope();

    /** @return declared Java value type */
    Class<T> type();

    /** @return label shown in preference dialogs */
    Component label();

    /** @return descriptive text shown beneath the label */
    Component description();

    /** @return value used when no stored value exists for the target scope */
    T defaultValue();

    /**
     * Returns the current value for {@code player}.
     *
     * @param player player whose stored value is read
     * @return current typed value
     * @throws NullPointerException if {@code player} is {@code null}
     */
    T get(Player player);

    /** @return current global value for this preference */
    T getGlobal();

    /**
     * Sets the value for {@code player}.
     *
     * @param player player whose value is updated
     * @param value new typed value
     * @throws NullPointerException if any argument is {@code null}
     */
    void set(Player player, T value);

    /**
     * Sets the global value programmatically.
     *
     * <p>Fired {@link dev.mintychochip.preferences.api.event.PreferenceChangeEvent} instances use a
     * {@code null} {@link dev.mintychochip.preferences.api.event.PreferenceChangeEvent#editor()}.
     *
     * @param value new typed value
     * @throws NullPointerException if {@code value} is {@code null}
     */
    void setGlobal(T value);

    /**
     * Sets the global value and attributes the change to {@code editor}.
     *
     * <p>Used when an admin saves from the preferences dialog; the event's
     * {@link dev.mintychochip.preferences.api.event.PreferenceChangeEvent#editor()} is the editor's UUID.
     *
     * @param editor player performing the edit
     * @param value new typed value
     * @throws NullPointerException if any argument is {@code null}
     */
    void setGlobal(Player editor, T value);

    /**
     * Clears the stored value for {@code player}, reverting to {@link #defaultValue()}.
     *
     * @param player player whose stored value is cleared
     * @throws NullPointerException if {@code player} is {@code null}
     */
    void reset(Player player);

    /** Clears the stored global value, reverting to {@link #defaultValue()}. */
    void resetGlobal();
}
