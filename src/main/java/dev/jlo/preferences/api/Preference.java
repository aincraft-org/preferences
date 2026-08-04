package dev.jlo.preferences.api;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Typed handle for a registered preference. */
public interface Preference<T> {
    PreferenceKey key();

    PreferenceScope scope();

    Class<T> type();

    Component label();

    Component description();

    T defaultValue();

    T get(Player player);

    T getGlobal();

    void set(Player player, T value);

    void setGlobal(T value);

    void reset(Player player);

    void resetGlobal();
}
