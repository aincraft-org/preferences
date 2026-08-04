package dev.jlo.preferences.api.codec;

public final class BuiltInCodecs {

    public static final StorageCodec<String> STRING = new StorageCodec<>() {
        @Override public String parse(String stored) { return stored; }
        @Override public String write(String value) { return value; }
    };

    public static final StorageCodec<Boolean> BOOLEAN = new StorageCodec<>() {
        @Override public Boolean parse(String stored) {
            if ("true".equals(stored)) return Boolean.TRUE;
            if ("false".equals(stored)) return Boolean.FALSE;
            throw new IllegalArgumentException("not a boolean: " + stored);
        }
        @Override public String write(Boolean value) { return value.toString(); }
    };

    public static final StorageCodec<Integer> INTEGER = parsing(Integer::parseInt, String::valueOf);
    public static final StorageCodec<Long> LONG = parsing(Long::parseLong, String::valueOf);
    public static final StorageCodec<Float> FLOAT = parsing(Float::parseFloat, String::valueOf);
    public static final StorageCodec<Double> DOUBLE = parsing(Double::parseDouble, String::valueOf);

    public static <E extends Enum<E>> StorageCodec<E> enumerated(Class<E> type) {
        return new StorageCodec<>() {
            @Override public E parse(String stored) {
                if (stored == null) {
                    throw new IllegalArgumentException("no " + type.getSimpleName() + " constant: null");
                }
                try { return Enum.valueOf(type, stored); }
                catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("no " + type.getSimpleName() + " constant: " + stored);
                }
            }
            @Override public String write(E value) { return value.name(); }
        };
    }

    private static <T> StorageCodec<T> parsing(java.util.function.Function<String, T> parse,
                                               java.util.function.Function<T, String> write) {
        return new StorageCodec<>() {
            @Override public T parse(String stored) {
                try { return parse.apply(stored); }
                catch (RuntimeException e) { throw new IllegalArgumentException("invalid value: " + stored, e); }
            }
            @Override public String write(T value) { return write.apply(value); }
        };
    }

    private BuiltInCodecs() {}
}
