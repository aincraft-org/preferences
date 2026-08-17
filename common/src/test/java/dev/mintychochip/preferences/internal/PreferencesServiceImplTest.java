package dev.mintychochip.preferences.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.mintychochip.preferences.api.PreferenceKey;
import dev.mintychochip.preferences.api.PreferenceScope;
import dev.mintychochip.preferences.api.codec.PreferenceCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class PreferencesServiceImplTest {

    @Test void unregisterPluginInvokesTeardownBeforeRegistryRemoval() {
        PreferenceRegistry registry = new PreferenceRegistry();
        RegisteredPreference<Boolean> pref = new RegisteredPreference<>(
            new PreferenceKey("demo", "flag"), PreferenceScope.PLAYER,
            Component.text("flag"), Component.empty(),
            PreferenceCodec.booleanBox(), Boolean.class, false, null);
        registry.register(pref);

        List<String> order = new ArrayList<>();
        AtomicBoolean stillRegisteredDuringTeardown = new AtomicBoolean();
        PreferencesServiceImpl service = new PreferencesServiceImpl(registry, ns -> {
            order.add("teardown:" + ns);
            stillRegisteredDuringTeardown.set(registry.byKey(pref.key()) != null);
        });

        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("Demo");

        service.unregisterPlugin(plugin);

        assertEquals(List.of("teardown:demo"), order);
        assertTrue(stillRegisteredDuringTeardown.get(), "teardown must run before registry removal");
        assertNull(registry.byKey(pref.key()));
    }

    @Test void registerRejectsNullOwnerTypeConfigure() {
        PreferencesServiceImpl service = new PreferencesServiceImpl(new PreferenceRegistry());
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("Demo");

        assertEquals("owner", assertThrows(NullPointerException.class,
            () -> service.register(null, Boolean.class, b -> {})).getMessage());
        assertEquals("type", assertThrows(NullPointerException.class,
            () -> service.register(plugin, null, b -> {})).getMessage());
        assertEquals("configure", assertThrows(NullPointerException.class,
            () -> service.register(plugin, Boolean.class, null)).getMessage());
    }

    @Test void unregisterPluginRejectsNull() {
        PreferencesServiceImpl service = new PreferencesServiceImpl(new PreferenceRegistry());
        assertEquals("plugin", assertThrows(NullPointerException.class,
            () -> service.unregisterPlugin(null)).getMessage());
    }

    @Test void constructorAllowsNullTeardownHook() {
        PreferencesServiceImpl service = new PreferencesServiceImpl(new PreferenceRegistry(), null);
        assertNotNull(service.all());
    }

    @Test void constructorRejectsNullRegistry() {
        assertEquals("registry", assertThrows(NullPointerException.class,
            () -> new PreferencesServiceImpl(null)).getMessage());
    }
}
