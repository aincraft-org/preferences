package dev.mintychochip.preferences.common.internal.session;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Thread-safe single-slot dialog sessions per player.
 *
 * <p>Backed by a {@link ConcurrentHashMap}; {@link #open} atomically replaces any prior session
 * for the same player. Methods may be called from any thread.
 */
public final class DialogSessionManager {

    private final Map<UUID, DialogSession> sessions = new ConcurrentHashMap<>();

    /**
     * Replaces any existing session for the same player (single-slot model).
     *
     * @param session session to store
     */
    public void open(DialogSession session) {
        sessions.put(Objects.requireNonNull(session, "session").player(), session);
    }

    /**
     * Returns the player's current session.
     *
     * @param player player id
     * @return current session, or {@code null} if none
     */
    public @Nullable DialogSession current(UUID player) {
        return sessions.get(Objects.requireNonNull(player, "player"));
    }

    /**
     * Removes the player's session if present.
     *
     * @param player player id
     */
    public void close(UUID player) {
        sessions.remove(Objects.requireNonNull(player, "player"));
    }

    /**
     * Tests whether the player has an open session on the given screen.
     *
     * @param player player id
     * @param screen screen to match
     * @return {@code true} if the player has an open session on the given screen
     * @throws NullPointerException if {@code player} or {@code screen} is null
     */
    public boolean matches(UUID player, DialogSession.Screen screen) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(screen, "screen");
        DialogSession s = sessions.get(player);
        return s != null && s.screen() == screen;
    }

    /**
     * Closes sessions targeting any preference in the given namespace (plugin disable).
     *
     * <p>Removes sessions whose {@link DialogSession#editTarget()} is non-{@code null} and whose
     * {@link dev.mintychochip.preferences.api.PreferenceKey#namespace()} equals {@code namespace}
     * via exact {@link String#equals}. List-only sessions without an edit target are retained.
     *
     * @param namespace plugin namespace
     * @throws NullPointerException if {@code namespace} is null
     */
    public void closeForNamespace(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        sessions.values().removeIf(s -> s.editTarget() != null && s.editTarget().namespace().equals(namespace));
    }
}
