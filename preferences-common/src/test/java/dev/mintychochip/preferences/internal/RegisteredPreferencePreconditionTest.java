package dev.mintychochip.preferences.internal;

import static org.junit.jupiter.api.Assertions.*;

import dev.mintychochip.preferences.api.PreferenceKey;
import dev.mintychochip.preferences.api.PreferenceScope;
import dev.mintychochip.preferences.api.codec.PreferenceCodec;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Verifies {@link RegisteredPreference} null preconditions and failure messages. */
class RegisteredPreferencePreconditionTest {

    private static RegisteredPreference<Boolean> playerPref() {
        return new RegisteredPreference<>(
            new PreferenceKey("demo", "flag"), PreferenceScope.PLAYER,
            Component.text("flag"), Component.empty(),
            PreferenceCodec.booleanBox(), Boolean.class, false, null);
    }

    private static RegisteredPreference<Boolean> globalPref() {
        return new RegisteredPreference<>(
            new PreferenceKey("demo", "announce"), PreferenceScope.GLOBAL,
            Component.text("announce"), Component.empty(),
            PreferenceCodec.booleanBox(), Boolean.class, false, null);
    }

    @Test void constructorAllowsNullOnChange() {
        RegisteredPreference<Boolean> pref = playerPref();
        assertNotNull(pref);
    }

    @Test void constructorRejectsNullKey() {
        assertEquals("key", assertThrows(NullPointerException.class, () ->
            new RegisteredPreference<>(null, PreferenceScope.PLAYER,
                Component.text("x"), Component.empty(),
                PreferenceCodec.booleanBox(), Boolean.class, false, null)).getMessage());
    }

    @Test void getRejectsNullPlayer() {
        RegisteredPreference<Boolean> pref = playerPref();
        NullPointerException e = assertThrows(NullPointerException.class, () -> pref.get(null));
        assertEquals("player", e.getMessage());
    }

    @Test void setRejectsNullPlayer() {
        RegisteredPreference<Boolean> pref = playerPref();
        NullPointerException e = assertThrows(NullPointerException.class, () -> pref.set(null, true));
        assertEquals("player", e.getMessage());
    }

    @Test void setRejectsNullValue() {
        RegisteredPreference<Boolean> pref = playerPref();
        Player player = Mockito.mock(Player.class);
        NullPointerException e = assertThrows(NullPointerException.class, () -> pref.set(player, null));
        assertEquals("value", e.getMessage());
    }

    @Test void setGlobalRejectsNullValue() {
        RegisteredPreference<Boolean> pref = globalPref();
        NullPointerException e = assertThrows(NullPointerException.class, () -> pref.setGlobal(null));
        assertEquals("value", e.getMessage());
    }

    @Test void setGlobalWithEditorRejectsNullEditor() {
        RegisteredPreference<Boolean> pref = globalPref();
        NullPointerException e = assertThrows(NullPointerException.class, () -> pref.setGlobal(null, true));
        assertEquals("editor", e.getMessage());
    }

    @Test void setGlobalWithEditorRejectsNullValue() {
        RegisteredPreference<Boolean> pref = globalPref();
        Player editor = Mockito.mock(Player.class);
        NullPointerException e = assertThrows(NullPointerException.class, () -> pref.setGlobal(editor, null));
        assertEquals("value", e.getMessage());
    }

    @Test void resetRejectsNullPlayer() {
        RegisteredPreference<Boolean> pref = playerPref();
        assertEquals("player", assertThrows(NullPointerException.class, () -> pref.reset(null)).getMessage());
    }

    @Test void invalidatePlayerRejectsNull() {
        RegisteredPreference<Boolean> pref = playerPref();
        assertEquals("uuid", assertThrows(NullPointerException.class,
            () -> pref.invalidatePlayer(null)).getMessage());
    }
}
