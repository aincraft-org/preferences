package dev.jlo.preferences.api.codec;

import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/** Converts between a preference's typed value and a native dialog input. */
public interface DialogInputAdapter<T> {
    /** Build the dialog input control, pre-filled with the current value. */
    DialogInput buildInput(String inputKey, Component label, T current);
    /** Read the typed value back from a dialog response; null if absent/invalid. */
    @Nullable T parseResponse(DialogResponseView response, String inputKey);
}
