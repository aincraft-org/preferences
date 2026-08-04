package dev.jlo.preferences.api.codec;

import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/** Bundles the storage codec (required) and dialog adapter (optional). */
public record PreferenceCodec<T>(StorageCodec<T> storage, @Nullable DialogInputAdapter<T> input) {

    public static PreferenceCodec<String> string(int maxLength) {
        return new PreferenceCodec<>(BuiltInCodecs.STRING, BuiltInAdapters.text(maxLength));
    }

    public static PreferenceCodec<Boolean> booleanBox() {
        return new PreferenceCodec<>(BuiltInCodecs.BOOLEAN, BuiltInAdapters.checkbox());
    }

    public static PreferenceCodec<Integer> integerSlider(int min, int max, int step) {
        return new PreferenceCodec<>(BuiltInCodecs.INTEGER,
            BuiltInAdapters.slider(min, max, step, v -> v.floatValue(), f -> Math.round(f)));
    }

    public static PreferenceCodec<Long> longSlider(long min, long max, long step) {
        return new PreferenceCodec<>(BuiltInCodecs.LONG,
            BuiltInAdapters.slider(min, max, step, v -> v.floatValue(), f -> (long) Math.round(f)));
    }

    public static PreferenceCodec<Float> floatSlider(float min, float max, float step) {
        return new PreferenceCodec<>(BuiltInCodecs.FLOAT,
            BuiltInAdapters.slider(min, max, step, Function.identity(), Function.identity()));
    }

    public static PreferenceCodec<Double> doubleSlider(double min, double max, double step) {
        return new PreferenceCodec<>(BuiltInCodecs.DOUBLE,
            BuiltInAdapters.slider((float) min, (float) max, (float) step, v -> v.floatValue(), f -> (double) f));
    }

    public static <E extends Enum<E>> PreferenceCodec<E> enumerated(
            Class<E> type, Function<E, Component> display) {
        return new PreferenceCodec<>(BuiltInCodecs.enumerated(type), BuiltInAdapters.optionPicker(type, display));
    }

    /** Persistable but not dialog-editable (read-only in GUI). */
    public static <T> PreferenceCodec<T> storageOnly(StorageCodec<T> storage) {
        return new PreferenceCodec<>(storage, null);
    }
}
