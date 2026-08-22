package dev.mintychochip.preferences.api.event;

import dev.mintychochip.preferences.api.PreferenceKey;
import java.util.UUID;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.Nullable;

/**
 * Bukkit event fired synchronously before a preference value is persisted.
 *
 * <p>{@link #oldValue()} and {@link #newValue()} are stored-string forms. Cancel the event to
 * reject the change; cancelled changes are not written and per-preference callbacks are not invoked.
 */
public class PreferenceChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PreferenceKey key;
    private final String oldValue;
    private final String newValue;
    /** Editor player UUID; null for programmatic/console sets. Global dialog saves pass the admin. */
    private final @Nullable UUID editor;
    private boolean cancelled;

    /**
     * Creates a change event.
     *
     * @param key preference being changed
     * @param oldValue previous stored value
     * @param newValue proposed stored value
     * @param editor UUID of the editing player, or {@code null} for programmatic changes
     * @throws NullPointerException if {@code key}, {@code oldValue}, or {@code newValue} is {@code null}
     */
    public PreferenceChangeEvent(PreferenceKey key, String oldValue, String newValue, @Nullable UUID editor) {
        this.key = java.util.Objects.requireNonNull(key, "key");
        this.oldValue = java.util.Objects.requireNonNull(oldValue, "oldValue");
        this.newValue = java.util.Objects.requireNonNull(newValue, "newValue");
        this.editor = editor; // intentionally nullable (programmatic/console)
    }

    /** @return preference identifier */
    public PreferenceKey key() {
        return key;
    }

    /** @return previous stored value */
    public String oldValue() {
        return oldValue;
    }

    /** @return proposed stored value */
    public String newValue() {
        return newValue;
    }

    /**
     * @return UUID of the player performing the edit, or {@code null} for programmatic or console changes
     */
    public @Nullable UUID editor() {
        return editor;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /** {@inheritDoc} */
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /** {@inheritDoc} */
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /** @return shared handler list for all instances */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
