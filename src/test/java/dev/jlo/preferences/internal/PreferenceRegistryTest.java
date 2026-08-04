package dev.jlo.preferences.internal;

import static org.junit.jupiter.api.Assertions.*;

import dev.jlo.preferences.api.PreferenceKey;
import dev.jlo.preferences.api.PreferenceScope;
import dev.jlo.preferences.api.codec.BuiltInCodecs;
import dev.jlo.preferences.api.codec.PreferenceCodec;
import org.junit.jupiter.api.Test;

class PreferenceRegistryTest {

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
}
