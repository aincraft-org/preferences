package dev.jlo.preferences.internal.session;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/** Single-slot sessions: opening any dialog replaces the player's previous one. */
public final class DialogSessionManager {

    private final Map<UUID, DialogSession> sessions = new ConcurrentHashMap<>();

    public void open(DialogSession session) { sessions.put(session.player(), session); }

    public @Nullable DialogSession current(UUID player) { return sessions.get(player); }

    public void close(UUID player) { sessions.remove(player); }

    public boolean matches(UUID player, DialogSession.Kind kind) {
        DialogSession s = sessions.get(player);
        return s != null && s.kind() == kind;
    }
}
