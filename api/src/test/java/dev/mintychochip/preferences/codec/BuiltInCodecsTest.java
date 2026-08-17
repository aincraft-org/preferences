package dev.mintychochip.preferences.codec;

import static org.junit.jupiter.api.Assertions.*;

import dev.mintychochip.preferences.api.codec.BuiltInCodecs;
import dev.mintychochip.preferences.api.codec.StorageCodec;
import org.junit.jupiter.api.Test;

class BuiltInCodecsTest {

    @Test void booleanRoundTrip() {
        StorageCodec<Boolean> c = BuiltInCodecs.BOOLEAN;
        assertEquals(Boolean.TRUE, c.parse("true"));
        assertEquals(Boolean.FALSE, c.parse("false"));
        assertEquals("true", c.write(true));
    }

    @Test void booleanRejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> BuiltInCodecs.BOOLEAN.parse("yes"));
    }

    @Test void integerRoundTrip() {
        assertEquals(Integer.valueOf(42), BuiltInCodecs.INTEGER.parse("42"));
        assertEquals("-7", BuiltInCodecs.INTEGER.write(-7));
        assertThrows(IllegalArgumentException.class, () -> BuiltInCodecs.INTEGER.parse("1.5"));
    }

    @Test void longRoundTrip() {
        assertEquals(Long.MAX_VALUE, BuiltInCodecs.LONG.parse(String.valueOf(Long.MAX_VALUE)));
    }

    @Test void floatRoundTrip() {
        assertEquals(Float.valueOf(1.5f), BuiltInCodecs.FLOAT.parse("1.5"));
        assertEquals("2.25", BuiltInCodecs.FLOAT.write(2.25f));
    }

    @Test void doubleRoundTrip() {
        assertEquals(Double.valueOf(0.125), BuiltInCodecs.DOUBLE.parse("0.125"));
    }

    @Test void stringRoundTrip() {
        assertEquals("hello world", BuiltInCodecs.STRING.parse("hello world"));
        assertEquals("", BuiltInCodecs.STRING.write(""));
    }

    enum Mode { FAST, SLOW }

    @Test void enumRoundTrip() {
        StorageCodec<Mode> c = BuiltInCodecs.enumerated(Mode.class);
        assertEquals(Mode.SLOW, c.parse("SLOW"));
        assertEquals("FAST", c.write(Mode.FAST));
        assertThrows(IllegalArgumentException.class, () -> c.parse("medium"));
    }

    @Test void enumRejectsNullStored() {
        NullPointerException e = assertThrows(NullPointerException.class,
            () -> BuiltInCodecs.enumerated(Mode.class).parse(null));
        assertEquals("stored", e.getMessage());
    }

    @Test void writeRejectsNullValue() {
        NullPointerException e = assertThrows(NullPointerException.class,
            () -> BuiltInCodecs.STRING.write(null));
        assertEquals("value", e.getMessage());
    }
}
