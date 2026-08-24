package dev.mintychochip.preferences.common.internal;

import static org.junit.jupiter.api.Assertions.*;

import dev.mintychochip.preferences.api.PreferenceKey;
import dev.mintychochip.preferences.api.PreferenceScope;
import dev.mintychochip.preferences.api.codec.BuiltInCodecs;
import dev.mintychochip.preferences.api.codec.PreferenceCodec;
import org.junit.jupiter.api.Test;
/** Verifies preference registration, lookup, removal, and duplicate rejection. */
class PreferenceRegistryTest {
    /** Builds a minimal player-scoped boolean preference for registry tests. */
    private RegisteredPreference<Boolean> boolPref(String ns, String name) {
        return new RegisteredPreference<>(
            new PreferenceKey(ns, name), PreferenceScope.PLAYER,
            net.kyori.adventure.text.Component.text(name),
            net.kyori.adventure.text.Component.text("desc"),
            PreferenceCodec.booleanBox(), Boolean.class, false, null);
    }

    @Test void registerAndLookup() {
        PreferenceRegistry registry = new PreferenceRegistry();
        RegisteredPreference<Boolean> pref = boolPref("demo", "flag");
        registry.register(pref);
        assertSame(pref, registry.byKey(pref.key()));
        assertEquals(1, registry.all().size());
    }

    @Test void duplicateKeyRejected() {
        PreferenceRegistry registry = new PreferenceRegistry();
        registry.register(boolPref("demo", "flag"));
        assertThrows(IllegalStateException.class, () -> registry.register(boolPref("demo", "flag")));
    }

    @Test void unregisterPluginRemovesOnlyItsPrefs() {
        PreferenceRegistry registry = new PreferenceRegistry();
        registry.register(boolPref("demo", "a"));
        registry.register(boolPref("other", "b"));
        registry.unregisterNamespace("demo");
        assertNull(registry.byKey(new PreferenceKey("demo", "a")));
        assertNotNull(registry.byKey(new PreferenceKey("other", "b")));
    }

    @Test void registerRejectsNullPref() {
        PreferenceRegistry registry = new PreferenceRegistry();
        assertEquals("pref", assertThrows(NullPointerException.class,
            () -> registry.register(null)).getMessage());
    }

    @Test void byKeyRejectsNull() {
        PreferenceRegistry registry = new PreferenceRegistry();
        assertEquals("key", assertThrows(NullPointerException.class,
            () -> registry.byKey(null)).getMessage());
    }

    @Test void unregisterNamespaceRejectsNull() {
        PreferenceRegistry registry = new PreferenceRegistry();
        assertEquals("namespace", assertThrows(NullPointerException.class,
            () -> registry.unregisterNamespace(null)).getMessage());
    }
}
