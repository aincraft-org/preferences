package dev.mintychochip.preferences;

import static org.junit.jupiter.api.Assertions.*;

import dev.mintychochip.preferences.api.PreferenceBuilder;
import dev.mintychochip.preferences.api.PreferenceChange;
import dev.mintychochip.preferences.api.PreferenceKey;
import dev.mintychochip.preferences.api.codec.BuiltInCodecs;
import dev.mintychochip.preferences.api.codec.PreferenceCodec;
import dev.mintychochip.preferences.api.event.PreferenceChangeEvent;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

/**
 * Drives real public API entry points with null to prove fail-fast preconditions,
 * and proves intentional-null contracts still accept null.
 */
class PreconditionBoundaryTest {

    @Test void preferenceChangeRejectsNullComponents() {
        PreferenceKey key = new PreferenceKey("demo", "flag");
        assertEquals("key", assertThrows(NullPointerException.class,
            () -> new PreferenceChange(null, "a", "b")).getMessage());
        assertEquals("oldValue", assertThrows(NullPointerException.class,
            () -> new PreferenceChange(key, null, "b")).getMessage());
        assertEquals("newValue", assertThrows(NullPointerException.class,
            () -> new PreferenceChange(key, "a", null)).getMessage());
    }

    @Test void preferenceChangeEventAllowsNullEditor() {
        PreferenceKey key = new PreferenceKey("demo", "flag");
        PreferenceChangeEvent event = new PreferenceChangeEvent(key, "old", "new", null);
        assertNull(event.editor());
        assertEquals("key", assertThrows(NullPointerException.class,
            () -> new PreferenceChangeEvent(null, "o", "n", null)).getMessage());
    }

    @Test void preferenceCodecRequiresStorageAllowsNullInput() {
        assertEquals("storage", assertThrows(NullPointerException.class,
            () -> new PreferenceCodec<>(null, null)).getMessage());
        PreferenceCodec<String> readOnly = PreferenceCodec.storageOnly(BuiltInCodecs.STRING);
        assertNull(readOnly.input(), "storageOnly must leave dialog adapter null");
        assertEquals("storage", assertThrows(NullPointerException.class,
            () -> PreferenceCodec.storageOnly(null)).getMessage());
    }

    @Test void preferenceBuilderRejectsNullFluentArgs() {
        assertEquals("namespace", assertThrows(NullPointerException.class,
            () -> new PreferenceBuilder<>(null, Boolean.class)).getMessage());
        assertEquals("type", assertThrows(NullPointerException.class,
            () -> new PreferenceBuilder<>("demo", null)).getMessage());

        PreferenceBuilder<Boolean> b = new PreferenceBuilder<>("demo", Boolean.class);
        assertEquals("name", assertThrows(NullPointerException.class,
            () -> b.playerScoped(null)).getMessage());
        assertEquals("label", assertThrows(NullPointerException.class,
            () -> b.label(null)).getMessage());
        assertEquals("codec", assertThrows(NullPointerException.class,
            () -> b.codec(null)).getMessage());
        assertEquals("defaultValue", assertThrows(NullPointerException.class,
            () -> b.defaultValue(null)).getMessage());
        assertEquals("onChange", assertThrows(NullPointerException.class,
            () -> b.onChange(null)).getMessage());
    }

    @Test void preferenceBuilderOnChangeOptionalWhenOmitted() {
        PreferenceBuilder<Boolean> b = new PreferenceBuilder<>("demo", Boolean.class)
            .playerScoped("flag")
            .label(Component.text("Flag"))
            .codec(PreferenceCodec.booleanBox())
            .defaultValue(true);
        b.validate();
        assertNull(b.onChange(), "onChange may remain null when never set");
    }

    @Test void enumeratedFactoryRejectsNullType() {
        assertEquals("type", assertThrows(NullPointerException.class,
            () -> PreferenceCodec.enumerated(null, e -> Component.text("x"))).getMessage());
    }
}
