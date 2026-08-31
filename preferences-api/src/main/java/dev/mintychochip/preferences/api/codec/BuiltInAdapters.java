package dev.mintychochip.preferences.api.codec;

import com.google.common.base.Preconditions;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/**
 * Factory methods for common {@link DialogInputAdapter} implementations.
 *
 * <p>Response parsers apply defense-in-depth validation and return {@code null} for absent or
 * out-of-range client values.
 */
public final class BuiltInAdapters {

    /** @return checkbox adapter for boolean preferences */
    public static DialogInputAdapter<Boolean> checkbox() {
        return new DialogInputAdapter<>() {
            @Override public DialogInput buildInput(String key, Component label, Boolean current) {
                Preconditions.checkNotNull(key, "key");
                Preconditions.checkNotNull(label, "label");
                Preconditions.checkNotNull(current, "current");
                return DialogInput.bool(key, label, current, "true", "false");
            }
            @Override public @Nullable Boolean parseResponse(DialogResponseView r, String key) {
                Preconditions.checkNotNull(r, "response");
                Preconditions.checkNotNull(key, "key");
                return r.getBoolean(key);
            }
        };
    }

    /**
     * Creates a numeric range slider adapter.
     *
     * @param min minimum accepted value, inclusive
     * @param max maximum accepted value, inclusive
     * @param step slider step
     * @param toFloat converts the typed value to the dialog float representation
     * @param fromFloat converts a validated dialog float back to the typed value
     * @param <N> number type
     * @return slider adapter
     * @throws NullPointerException if {@code toFloat} or {@code fromFloat} is {@code null}
     */
    public static <N extends Number> DialogInputAdapter<N> slider(
            float min, float max, float step,
            Function<N, Float> toFloat, Function<Float, N> fromFloat) {
        Preconditions.checkNotNull(toFloat, "toFloat");
        Preconditions.checkNotNull(fromFloat, "fromFloat");
        return new DialogInputAdapter<>() {
            @Override public DialogInput buildInput(String key, Component label, N current) {
                Preconditions.checkNotNull(key, "key");
                Preconditions.checkNotNull(label, "label");
                Preconditions.checkNotNull(current, "current");
                return DialogInput.numberRange(key, 200, label, "options.generic_value", min, max, toFloat.apply(current), step);
            }
            @Override public @Nullable N parseResponse(DialogResponseView r, String key) {
                Preconditions.checkNotNull(r, "response");
                Preconditions.checkNotNull(key, "key");
                Float f = r.getFloat(key);
                if (f == null) return null;
                // Defense in depth: do not trust client-supplied values outside the declared range.
                if (f < min || f > max) return null;
                return fromFloat.apply(f);
            }
        };
    }

    /**
     * Creates a single-option picker for an enum type.
     *
     * @param type enum class
     * @param display maps each constant to its dialog label
     * @param <E> enum type
     * @return option picker adapter
     * @throws NullPointerException if {@code type} or {@code display} is {@code null}
     */
    public static <E extends Enum<E>> DialogInputAdapter<E> optionPicker(
            Class<E> type, Function<E, Component> display) {
        Preconditions.checkNotNull(type, "type");
        Preconditions.checkNotNull(display, "display");
        return new DialogInputAdapter<>() {
            @Override public DialogInput buildInput(String key, Component label, E current) {
                Preconditions.checkNotNull(key, "key");
                Preconditions.checkNotNull(label, "label");
                Preconditions.checkNotNull(current, "current");
                List<SingleOptionDialogInput.OptionEntry> entries = Arrays.stream(type.getEnumConstants())
                    .map(e -> SingleOptionDialogInput.OptionEntry.create(e.name(), display.apply(e), e == current))
                    .toList();
                return DialogInput.singleOption(key, 200, entries, label, true);
            }
            @Override public @Nullable E parseResponse(DialogResponseView r, String key) {
                Preconditions.checkNotNull(r, "response");
                Preconditions.checkNotNull(key, "key");
                String id = r.getText(key);
                if (id == null) return null;
                try { return Enum.valueOf(type, id); }
                catch (IllegalArgumentException e) { return null; }
            }
        };
    }

    /**
     * Creates a bounded text input adapter.
     *
     * @param maxLength maximum accepted response length; must be {@code >= 0}
     * @return text adapter
     * @throws IllegalArgumentException if {@code maxLength} is negative
     */
    public static DialogInputAdapter<String> text(int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("maxLength must be >= 0");
        }
        return new DialogInputAdapter<>() {
            @Override public DialogInput buildInput(String key, Component label, String current) {
                Preconditions.checkNotNull(key, "key");
                Preconditions.checkNotNull(label, "label");
                Preconditions.checkNotNull(current, "current");
                return DialogInput.text(key, 200, label, true, current, maxLength, null);
            }
            @Override public @Nullable String parseResponse(DialogResponseView r, String key) {
                Preconditions.checkNotNull(r, "response");
                Preconditions.checkNotNull(key, "key");
                String text = r.getText(key);
                if (text == null) return null;
                // Defense in depth: reject over-long payloads even if the client ignores maxLength.
                if (text.length() > maxLength) return null;
                return text;
            }
        };
    }

    private BuiltInAdapters() {}
}
