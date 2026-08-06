package dev.jlo.preferences.api.event;

import dev.jlo.preferences.api.PreferenceKey;
import java.util.UUID;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.Nullable;

/** Fired before a preference value is persisted. Cancel to reject the change. */
public class PreferenceChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PreferenceKey key;
    private final String oldValue;
    private final String newValue;
    /** Editor player UUID; null for programmatic/console sets. Global dialog saves pass the admin. */
    private final @Nullable UUID editor;
    private boolean cancelled;

    public PreferenceChangeEvent(PreferenceKey key, String oldValue, String newValue, @Nullable UUID editor) {
        this.key = key;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.editor = editor;
    }

    public PreferenceKey key() {
        return key;
    }

    public String oldValue() {
        return oldValue;
    }

    public String newValue() {
        return newValue;
    }

    public @Nullable UUID editor() {
        return editor;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
