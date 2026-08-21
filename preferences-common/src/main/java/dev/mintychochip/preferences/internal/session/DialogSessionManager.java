package dev.mintychochip.preferences.internal.session;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/** Single-slot sessions: opening any dialog replaces the player's previous one. */
public final class DialogSessionManager {

    private final Map<UUID, DialogSession> sessions = new ConcurrentHashMap<>();

    public void open(DialogSession session) {
        sessions.put(Objects.requireNonNull(session, "session").player(), session);
    }

    public @Nullable DialogSession current(UUID player) {
        return sessions.get(Objects.requireNonNull(player, "player"));
    }

    public void close(UUID player) {
        sessions.remove(Objects.requireNonNull(player, "player"));
    }

    public boolean matches(UUID player, DialogSession.Screen screen) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(screen, "screen");
        DialogSession s = sessions.get(player);
        return s != null && s.screen() == screen;
    }

    /** Closes sessions targeting any preference in the given namespace (plugin disable). */
    public void closeForNamespace(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        sessions.values().removeIf(s -> s.editTarget() != null && s.editTarget().namespace().equals(namespace));
    }
}
