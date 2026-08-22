package dev.mintychochip.preferences.api.codec;

import java.util.Objects;

/**
 * Shared {@link StorageCodec} instances for common preference value types.
 *
 * <p>All codecs reject {@code null} inputs and throw {@link IllegalArgumentException} for invalid
 * stored strings.
 */
public final class BuiltInCodecs {

    /** Identity codec for plain strings. */
    public static final StorageCodec<String> STRING = new StorageCodec<>() {
        @Override public String parse(String stored) {
            return Objects.requireNonNull(stored, "stored");
        }
        @Override public String write(String value) {
            return Objects.requireNonNull(value, "value");
        }
    };

    /** Codec for {@code "true"} and {@code "false"} string literals only. */
    public static final StorageCodec<Boolean> BOOLEAN = new StorageCodec<>() {
        @Override public Boolean parse(String stored) {
            Objects.requireNonNull(stored, "stored");
            if ("true".equals(stored)) return Boolean.TRUE;
            if ("false".equals(stored)) return Boolean.FALSE;
            throw new IllegalArgumentException("not a boolean: " + stored);
        }
        @Override public String write(Boolean value) {
            return Objects.requireNonNull(value, "value").toString();
        }
    };

    /** Codec parsing decimal integer strings. */
    public static final StorageCodec<Integer> INTEGER = parsing(Integer::parseInt, String::valueOf);
    /** Codec parsing decimal long strings. */
    public static final StorageCodec<Long> LONG = parsing(Long::parseLong, String::valueOf);
    /** Codec parsing decimal float strings. */
    public static final StorageCodec<Float> FLOAT = parsing(Float::parseFloat, String::valueOf);
    /** Codec parsing decimal double strings. */
    public static final StorageCodec<Double> DOUBLE = parsing(Double::parseDouble, String::valueOf);

    /**
     * Creates a codec that persists {@link Enum#name()} values.
     *
     * @param type enum class
     * @param <E> enum type
     * @return storage codec for the enum
     * @throws NullPointerException if {@code type} is {@code null}
     */
    public static <E extends Enum<E>> StorageCodec<E> enumerated(Class<E> type) {
        Objects.requireNonNull(type, "type");
        return new StorageCodec<>() {
            @Override public E parse(String stored) {
                Objects.requireNonNull(stored, "stored");
                try { return Enum.valueOf(type, stored); }
                catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("no " + type.getSimpleName() + " constant: " + stored);
                }
            }
            @Override public String write(E value) {
                return Objects.requireNonNull(value, "value").name();
            }
        };
    }

    private static <T> StorageCodec<T> parsing(java.util.function.Function<String, T> parse,
                                               java.util.function.Function<T, String> write) {
        return new StorageCodec<>() {
            @Override public T parse(String stored) {
                Objects.requireNonNull(stored, "stored");
                try { return parse.apply(stored); }
                catch (RuntimeException e) { throw new IllegalArgumentException("invalid value: " + stored, e); }
            }
            @Override public String write(T value) {
                return write.apply(Objects.requireNonNull(value, "value"));
            }
        };
    }

    private BuiltInCodecs() {}
}
