package dev.jlo.preferences.internal.session;

import static org.junit.jupiter.api.Assertions.*;

import dev.jlo.preferences.api.PreferenceKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DialogSessionManagerTest {

    @Test void openAndQuery() {
        DialogSessionManager mgr = new DialogSessionManager();
        UUID p = UUID.randomUUID();
        mgr.open(new DialogSession(p, DialogSession.Kind.PLAYER_LIST, 0, null));
        assertNotNull(mgr.current(p));
        assertTrue(mgr.matches(p, DialogSession.Kind.PLAYER_LIST));
    }

    @Test void openingReplacesPreviousSession() {
        DialogSessionManager mgr = new DialogSessionManager();
        UUID p = UUID.randomUUID();
        mgr.open(new DialogSession(p, DialogSession.Kind.PLAYER_LIST, 2, null));
        PreferenceKey key = new PreferenceKey("demo", "flag");
        mgr.open(new DialogSession(p, DialogSession.Kind.EDIT, 0, key));
        assertEquals(DialogSession.Kind.EDIT, mgr.current(p).kind());
        assertEquals(key, mgr.current(p).target());
    }

    @Test void unknownPlayerHasNoSession() {
        DialogSessionManager mgr = new DialogSessionManager();
        assertNull(mgr.current(UUID.randomUUID()));
        assertFalse(mgr.matches(UUID.randomUUID(), DialogSession.Kind.EDIT));
    }

    @Test void closeRemoves() {
        DialogSessionManager mgr = new DialogSessionManager();
        UUID p = UUID.randomUUID();
        mgr.open(new DialogSession(p, DialogSession.Kind.PLAYER_LIST, 0, null));
        mgr.close(p);
        assertNull(mgr.current(p));
        assertFalse(mgr.matches(p, DialogSession.Kind.PLAYER_LIST));
    }

    @Test void nullArgumentsRejected() {
        DialogSessionManager mgr = new DialogSessionManager();
        NullPointerException e = assertThrows(NullPointerException.class, () -> mgr.open(null));
        assertEquals("session", e.getMessage());
        e = assertThrows(NullPointerException.class, () -> mgr.current(null));
        assertEquals("player", e.getMessage());
        e = assertThrows(NullPointerException.class, () -> mgr.close(null));
        assertEquals("player", e.getMessage());
        e = assertThrows(NullPointerException.class, () -> mgr.matches(null, DialogSession.Kind.EDIT));
        assertEquals("player", e.getMessage());
        e = assertThrows(NullPointerException.class, () -> mgr.matches(UUID.randomUUID(), null));
        assertEquals("kind", e.getMessage());
    }
}
