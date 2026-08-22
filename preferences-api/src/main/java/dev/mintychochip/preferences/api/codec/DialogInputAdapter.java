package dev.mintychochip.preferences.api.codec;

import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/**
 * Bridges a preference's typed value and a Paper dialog input control.
 *
 * <p>Adapters are optional; preferences registered with {@link PreferenceCodec#storageOnly(StorageCodec)}
 * omit an adapter and are read-only in the GUI.
 *
 * @param <T> preference value type
 */
public interface DialogInputAdapter<T> {
    /**
     * Builds a dialog input pre-filled with {@code current}.
     *
     * @param inputKey stable key used when reading the response
     * @param label control label
     * @param current current typed value
     * @return dialog input definition
     * @throws NullPointerException if any argument is {@code null}
     */
    DialogInput buildInput(String inputKey, Component label, T current);

    /**
     * Reads the typed value from a submitted dialog response.
     *
     * @param response submitted dialog response
     * @param inputKey key passed to {@link #buildInput(String, Component, Object)}
     * @return parsed value, or {@code null} when the field is absent or invalid
     * @throws NullPointerException if {@code response} or {@code inputKey} is {@code null}
     */
    @Nullable T parseResponse(DialogResponseView response, String inputKey);
}
