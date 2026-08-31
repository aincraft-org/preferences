package dev.mintychochip.preferences.api.codec;

import com.google.common.base.Preconditions;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/**
 * Bundles the required storage codec and an optional dialog adapter for a preference.
 *
 * @param <T> preference value type
 * @param storage codec used for persistence
 * @param input optional dialog adapter; {@code null} when the preference is storage-only
 */
public record PreferenceCodec<T>(StorageCodec<T> storage, @Nullable DialogInputAdapter<T> input) {
    /**
     * Compact constructor requiring a non-null storage codec.
     *
     * @throws NullPointerException if {@code storage} is {@code null}
     */
    public PreferenceCodec {
        Preconditions.checkNotNull(storage, "storage");
        // input is intentionally nullable (read-only-in-GUI / storageOnly)
    }

    /**
     * String preference with a bounded text dialog input.
     *
     * @param maxLength maximum accepted response length
     * @return codec using {@link BuiltInCodecs#STRING}
     */
    public static PreferenceCodec<String> string(int maxLength) {
        return new PreferenceCodec<>(BuiltInCodecs.STRING, BuiltInAdapters.text(maxLength));
    }

    /** @return boolean preference with a checkbox dialog input */
    public static PreferenceCodec<Boolean> booleanBox() {
        return new PreferenceCodec<>(BuiltInCodecs.BOOLEAN, BuiltInAdapters.checkbox());
    }

    /**
     * Integer preference edited with a numeric range slider.
     *
     * @param min minimum inclusive value
     * @param max maximum inclusive value
     * @param step slider step size
     * @return codec using {@link BuiltInCodecs#INTEGER}
     */
    public static PreferenceCodec<Integer> integerSlider(int min, int max, int step) {
        return new PreferenceCodec<>(BuiltInCodecs.INTEGER,
            BuiltInAdapters.slider(min, max, step, v -> v.floatValue(), f -> Math.round(f)));
    }

    /**
     * Long preference edited with a numeric range slider.
     *
     * @param min minimum inclusive value
     * @param max maximum inclusive value
     * @param step slider step size
     * @return codec using {@link BuiltInCodecs#LONG}
     */
    public static PreferenceCodec<Long> longSlider(long min, long max, long step) {
        return new PreferenceCodec<>(BuiltInCodecs.LONG,
            BuiltInAdapters.slider(min, max, step, v -> v.floatValue(), f -> (long) Math.round(f)));
    }

    /**
     * Float preference edited with a numeric range slider.
     *
     * @param min minimum inclusive value
     * @param max maximum inclusive value
     * @param step slider step size
     * @return codec using {@link BuiltInCodecs#FLOAT}
     */
    public static PreferenceCodec<Float> floatSlider(float min, float max, float step) {
        return new PreferenceCodec<>(BuiltInCodecs.FLOAT,
            BuiltInAdapters.slider(min, max, step, Function.identity(), Function.identity()));
    }

    /**
     * Double preference edited with a numeric range slider.
     *
     * @param min minimum inclusive value
     * @param max maximum inclusive value
     * @param step slider step size
     * @return codec using {@link BuiltInCodecs#DOUBLE}
     */
    public static PreferenceCodec<Double> doubleSlider(double min, double max, double step) {
        return new PreferenceCodec<>(BuiltInCodecs.DOUBLE,
            BuiltInAdapters.slider((float) min, (float) max, (float) step, v -> v.floatValue(), f -> (double) f));
    }

    /**
     * Enum preference edited with a single-option picker.
     *
     * @param type enum class
     * @param display maps each constant to its dialog label
     * @param <E> enum type
     * @return codec persisting {@link Enum#name()} values
     * @throws NullPointerException if {@code type} or {@code display} is {@code null}
     */
    public static <E extends Enum<E>> PreferenceCodec<E> enumerated(
            Class<E> type, Function<E, Component> display) {
        Preconditions.checkNotNull(type, "type");
        Preconditions.checkNotNull(display, "display");
        return new PreferenceCodec<>(BuiltInCodecs.enumerated(type), BuiltInAdapters.optionPicker(type, display));
    }

    /**
     * Persistable codec without a dialog adapter.
     *
     * <p>Preferences using this factory are read-only in the GUI.
     *
     * @param storage storage codec
     * @param <T> value type
     * @return codec with a {@code null} dialog adapter
     * @throws NullPointerException if {@code storage} is {@code null}
     */
    public static <T> PreferenceCodec<T> storageOnly(StorageCodec<T> storage) {
        return new PreferenceCodec<>(Preconditions.checkNotNull(storage, "storage"), null);
    }
}
