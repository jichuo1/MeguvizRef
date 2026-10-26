package io.github.jichuo1.meguviz.ref.api.query;

/** Argument compatibility only: this is not Java compiler overload resolution. */
public enum CompatibilityPolicy {
    LEGACY_BIL, JAVA_INVOCATION;
    public boolean accepts(Class<?> parameter, Class<?> argument) {
        if (argument == null) return !parameter.isPrimitive();
        if (!parameter.isPrimitive()) return parameter.isAssignableFrom(argument);
        Class<?> boxed = boxed(parameter);
        if (boxed == argument) return true;
        if (this == LEGACY_BIL || parameter == boolean.class || argument == Boolean.class) return false;
        if (argument == Byte.class) return parameter == short.class || parameter == int.class || parameter == long.class || parameter == float.class || parameter == double.class;
        if (argument == Short.class || argument == Character.class) return parameter == int.class || parameter == long.class || parameter == float.class || parameter == double.class;
        if (argument == Integer.class) return parameter == long.class || parameter == float.class || parameter == double.class;
        if (argument == Long.class) return parameter == float.class || parameter == double.class;
        return argument == Float.class && parameter == double.class;
    }
    public static Class<?> boxed(Class<?> type) {
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == void.class) return Void.class;
        return type;
    }
}
