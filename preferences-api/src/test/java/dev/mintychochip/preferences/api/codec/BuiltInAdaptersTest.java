package dev.mintychochip.preferences.api.codec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.mintychochip.preferences.api.codec.BuiltInAdapters;
import dev.mintychochip.preferences.api.codec.DialogInputAdapter;
import io.papermc.paper.dialog.DialogResponseView;
import org.junit.jupiter.api.Test;

/** Verifies {@link BuiltInAdapters} response validation and length/range guards. */
class BuiltInAdaptersTest {

    @Test void textAcceptsWithinMaxLength() {
        DialogInputAdapter<String> adapter = BuiltInAdapters.text(4);
        DialogResponseView view = mock(DialogResponseView.class);
        when(view.getText("value")).thenReturn("abcd");
        assertEquals("abcd", adapter.parseResponse(view, "value"));
    }

    @Test void textRejectsOverMaxLength() {
        DialogInputAdapter<String> adapter = BuiltInAdapters.text(3);
        DialogResponseView view = mock(DialogResponseView.class);
        when(view.getText("value")).thenReturn("abcd");
        assertNull(adapter.parseResponse(view, "value"));
    }

    @Test void textRejectsNull() {
        DialogInputAdapter<String> adapter = BuiltInAdapters.text(8);
        DialogResponseView view = mock(DialogResponseView.class);
        when(view.getText("value")).thenReturn(null);
        assertNull(adapter.parseResponse(view, "value"));
    }

    @Test void sliderAcceptsInRange() {
        DialogInputAdapter<Integer> adapter = BuiltInAdapters.slider(
            0f, 100f, 1f, Integer::floatValue, f -> Math.round(f));
        DialogResponseView view = mock(DialogResponseView.class);
        when(view.getFloat("value")).thenReturn(50f);
        assertEquals(50, adapter.parseResponse(view, "value"));
    }

    @Test void sliderRejectsBelowMin() {
        DialogInputAdapter<Integer> adapter = BuiltInAdapters.slider(
            10f, 100f, 1f, Integer::floatValue, f -> Math.round(f));
        DialogResponseView view = mock(DialogResponseView.class);
        when(view.getFloat("value")).thenReturn(5f);
        assertNull(adapter.parseResponse(view, "value"));
    }

    @Test void sliderRejectsAboveMax() {
        DialogInputAdapter<Integer> adapter = BuiltInAdapters.slider(
            0f, 10f, 1f, Integer::floatValue, f -> Math.round(f));
        DialogResponseView view = mock(DialogResponseView.class);
        when(view.getFloat("value")).thenReturn(11f);
        assertNull(adapter.parseResponse(view, "value"));
    }

    @Test void textNegativeMaxLengthRejected() {
        assertThrows(IllegalArgumentException.class, () -> BuiltInAdapters.text(-1));
    }
}
