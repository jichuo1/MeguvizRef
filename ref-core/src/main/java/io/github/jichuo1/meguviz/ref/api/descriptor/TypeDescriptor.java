package io.github.jichuo1.meguviz.ref.api.descriptor;

import java.util.Objects;

/** A validated JVM type descriptor; loading never initializes the referenced class. */
public final class TypeDescriptor {
    private final String value;
    private TypeDescriptor(String value) { this.value = value; }
    public static TypeDescriptor of(String value) {
        Objects.requireNonNull(value, "descriptor");
        if (value.length() == 0 || value.length() > 4096) throw new IllegalArgumentException("descriptor length");
        int dimensions = 0;
        while (dimensions < value.length() && value.charAt(dimensions) == '[') dimensions++;
        if (dimensions > 255 || dimensions == value.length()) throw new IllegalArgumentException("array dimensions");
        String element = value.substring(dimensions);
        if (element.length() == 1 && "VZBCSIJFD".indexOf(element.charAt(0)) >= 0) {
            if (dimensions > 0 && element.equals("V")) throw new IllegalArgumentException("void array");
        } else if (element.startsWith("L") && element.endsWith(";") && element.length() > 2) {
            String name = element.substring(1, element.length() - 1);
            if (name.startsWith("/") || name.endsWith("/") || name.contains("//")) throw new IllegalArgumentException("reference name");
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (c == '.' || c == ';' || c == '[' || c == '(' || c == ')' || Character.isWhitespace(c)) throw new IllegalArgumentException("reference name");
            }
        } else throw new IllegalArgumentException("invalid descriptor: " + value);
        return new TypeDescriptor(value);
    }
    public static TypeDescriptor fromClass(Class<?> type) {
        if (type.isArray()) return of(type.getName().replace('.', '/'));
        if (!type.isPrimitive()) return of("L" + type.getName().replace('.', '/') + ";");
        if (type == void.class) return of("V");
        if (type == boolean.class) return of("Z");
        if (type == byte.class) return of("B");
        if (type == char.class) return of("C");
        if (type == short.class) return of("S");
        if (type == int.class) return of("I");
        if (type == long.class) return of("J");
        if (type == float.class) return of("F");
        return of("D");
    }
    public String value() { return value; }
    public boolean isVoid() { return value.equals("V"); }
    public Class<?> resolve(ClassLoader loader) throws ClassNotFoundException {
        switch (value) {
            case "V": return void.class;
            case "Z": return boolean.class;
            case "B": return byte.class;
            case "C": return char.class;
            case "S": return short.class;
            case "I": return int.class;
            case "J": return long.class;
            case "F": return float.class;
            case "D": return double.class;
            default:
                String name = value.charAt(0) == 'L' ? value.substring(1, value.length() - 1) : value;
                return Class.forName(name.replace('/', '.'), false, loader);
        }
    }
    @Override public boolean equals(Object other) { return other instanceof TypeDescriptor && value.equals(((TypeDescriptor) other).value); }
    @Override public int hashCode() { return value.hashCode(); }
    @Override public String toString() { return value; }
}
