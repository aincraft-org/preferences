package dev.jlo.preferences.api.codec;

import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

public final class BuiltInAdapters {

    public static DialogInputAdapter<Boolean> checkbox() {
        return new DialogInputAdapter<>() {
            @Override public DialogInput buildInput(String key, Component label, Boolean current) {
                java.util.Objects.requireNonNull(key, "key");
                java.util.Objects.requireNonNull(label, "label");
                java.util.Objects.requireNonNull(current, "current");
                return DialogInput.bool(key, label, current, "true", "false");
            }
            @Override public @Nullable Boolean parseResponse(DialogResponseView r, String key) {
                java.util.Objects.requireNonNull(r, "response");
                java.util.Objects.requireNonNull(key, "key");
                return r.getBoolean(key);
            }
        };
    }

    public static <N extends Number> DialogInputAdapter<N> slider(
            float min, float max, float step,
            Function<N, Float> toFloat, Function<Float, N> fromFloat) {
        java.util.Objects.requireNonNull(toFloat, "toFloat");
        java.util.Objects.requireNonNull(fromFloat, "fromFloat");
        return new DialogInputAdapter<>() {
            @Override public DialogInput buildInput(String key, Component label, N current) {
                java.util.Objects.requireNonNull(key, "key");
                java.util.Objects.requireNonNull(label, "label");
                java.util.Objects.requireNonNull(current, "current");
                return DialogInput.numberRange(key, 200, label, "options.generic_value", min, max, toFloat.apply(current), step);
            }
            @Override public @Nullable N parseResponse(DialogResponseView r, String key) {
                java.util.Objects.requireNonNull(r, "response");
                java.util.Objects.requireNonNull(key, "key");
                Float f = r.getFloat(key);
                if (f == null) return null;
                // Defense in depth: do not trust client-supplied values outside the declared range.
                if (f < min || f > max) return null;
                return fromFloat.apply(f);
            }
        };
    }

    public static <E extends Enum<E>> DialogInputAdapter<E> optionPicker(
            Class<E> type, Function<E, Component> display) {
        java.util.Objects.requireNonNull(type, "type");
        java.util.Objects.requireNonNull(display, "display");
        return new DialogInputAdapter<>() {
            @Override public DialogInput buildInput(String key, Component label, E current) {
                java.util.Objects.requireNonNull(key, "key");
                java.util.Objects.requireNonNull(label, "label");
                java.util.Objects.requireNonNull(current, "current");
                List<SingleOptionDialogInput.OptionEntry> entries = Arrays.stream(type.getEnumConstants())
                    .map(e -> SingleOptionDialogInput.OptionEntry.create(e.name(), display.apply(e), e == current))
                    .toList();
                return DialogInput.singleOption(key, 200, entries, label, true);
            }
            @Override public @Nullable E parseResponse(DialogResponseView r, String key) {
                java.util.Objects.requireNonNull(r, "response");
                java.util.Objects.requireNonNull(key, "key");
                String id = r.getText(key);
                if (id == null) return null;
                try { return Enum.valueOf(type, id); }
                catch (IllegalArgumentException e) { return null; }
            }
        };
    }

    public static DialogInputAdapter<String> text(int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("maxLength must be >= 0");
        }
        return new DialogInputAdapter<>() {
            @Override public DialogInput buildInput(String key, Component label, String current) {
                java.util.Objects.requireNonNull(key, "key");
                java.util.Objects.requireNonNull(label, "label");
                java.util.Objects.requireNonNull(current, "current");
                return DialogInput.text(key, 200, label, true, current, maxLength, null);
            }
            @Override public @Nullable String parseResponse(DialogResponseView r, String key) {
                java.util.Objects.requireNonNull(r, "response");
                java.util.Objects.requireNonNull(key, "key");
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
