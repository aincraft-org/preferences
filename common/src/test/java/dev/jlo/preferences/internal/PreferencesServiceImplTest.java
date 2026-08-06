package dev.jlo.preferences.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.jlo.preferences.api.PreferenceKey;
import dev.jlo.preferences.api.PreferenceScope;
import dev.jlo.preferences.api.codec.PreferenceCodec;
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
}
