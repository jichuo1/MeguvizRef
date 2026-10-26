package io.github.jichuo1.meguviz.ref.diagnostics;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small, bounded JSON codec for the diagnostics value schema; never instantiates arbitrary objects. */
final class BoundedJson {
    static final int MAX_BYTES = 65536;
    private final String input;
    private int position;
    private int nodes;
    private BoundedJson(String input) { this.input = input; }

    static Object decode(byte[] bytes) {
        if (bytes.length > MAX_BYTES) throw invalid("record too large");
        final String input;
        try {
            input = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException badEncoding) { throw invalid("invalid UTF-8"); }
        BoundedJson parser = new BoundedJson(input);
        Object result = parser.value(0);
        parser.whitespace();
        if (parser.position != input.length()) throw invalid("trailing data");
        return result;
    }

    static byte[] encode(Object value) {
        StringBuilder text = new StringBuilder();
        append(text, value, 0);
        byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw invalid("record too large");
        return bytes;
    }

    private Object value(int depth) {
        if (depth > 16 || ++nodes > 4096) throw invalid("depth or node limit");
        whitespace();
        if (position == input.length()) throw invalid("truncated value");
        char c = input.charAt(position);
        if (c == '"') return string();
        if (c == '{') {
            position++;
            Map<String, Object> result = new LinkedHashMap<>();
            whitespace();
            if (take('}')) return result;
            do {
                whitespace();
                String key = string();
                whitespace();
                require(':');
                if (result.containsKey(key)) throw invalid("duplicate key");
                result.put(key, value(depth + 1));
                whitespace();
                if (take('}')) return result;
                require(',');
            } while (true);
        }
        if (c == '[') {
            position++;
            List<Object> result = new ArrayList<>();
            whitespace();
            if (take(']')) return result;
            do {
                result.add(value(depth + 1));
                whitespace();
                if (take(']')) return result;
                require(',');
            } while (true);
        }
        if (input.startsWith("true", position)) { position += 4; return true; }
        if (input.startsWith("false", position)) { position += 5; return false; }
        if (input.startsWith("null", position)) { position += 4; return null; }
        int start = position;
        take('-');
        if (take('0')) {
            if (position < input.length() && Character.isDigit(input.charAt(position))) throw invalid("leading zero");
        } else {
            int digits = position;
            while (position < input.length() && input.charAt(position) >= '0' && input.charAt(position) <= '9') position++;
            if (position == digits) throw invalid("expected integer");
        }
        try { return Long.valueOf(input.substring(start, position)); }
        catch (NumberFormatException failure) { throw invalid("integer out of range"); }
    }

    private String string() {
        require('"');
        StringBuilder result = new StringBuilder();
        while (position < input.length()) {
            char c = input.charAt(position++);
            if (c == '"') return result.toString();
            if (c < 32) throw invalid("unescaped control character");
            if (c == '\\') {
                if (position == input.length()) throw invalid("truncated escape");
                c = input.charAt(position++);
                switch (c) {
                    case '"': case '\\': case '/': result.append(c); break;
                    case 'b': result.append('\b'); break;
                    case 'f': result.append('\f'); break;
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    case 'u':
                        if (input.length() - position < 4) throw invalid("truncated unicode escape");
                        int code = 0;
                        for (int i = 0; i < 4; i++) {
                            int digit = Character.digit(input.charAt(position++), 16);
                            if (digit < 0) throw invalid("invalid unicode escape");
                            code = code * 16 + digit;
                        }
                        result.append((char) code); break;
                    default: throw invalid("invalid escape");
                }
            } else result.append(c);
            if (result.length() > 8192) throw invalid("string too large");
        }
        throw invalid("unterminated string");
    }

    private static void append(StringBuilder out, Object value, int depth) {
        if (depth > 16 || out.length() > MAX_BYTES) throw invalid("output limit");
        if (value == null || value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            out.append(value);
        } else if (value instanceof String) {
            String text = (String) value;
            if (text.length() > 8192) throw invalid("string too large");
            out.append('"');
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '"' || c == '\\') out.append('\\').append(c);
                else if (c < 32 || Character.isSurrogate(c)) {
                    out.append("\\u");
                    String hex = Integer.toHexString(c);
                    for (int pad = hex.length(); pad < 4; pad++) out.append('0');
                    out.append(hex);
                } else out.append(c);
            }
            out.append('"');
        } else if (value instanceof Map) {
            out.append('{');
            boolean separator = false;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) throw invalid("key must be string");
                if (separator) out.append(',');
                append(out, entry.getKey(), depth + 1);
                out.append(':');
                append(out, entry.getValue(), depth + 1);
                separator = true;
            }
            out.append('}');
        } else if (value instanceof Iterable) {
            out.append('[');
            boolean separator = false;
            for (Object item : (Iterable<?>) value) {
                if (separator) out.append(',');
                append(out, item, depth + 1);
                separator = true;
            }
            out.append(']');
        } else throw invalid("unsupported value type");
    }
    private void whitespace() {
        while (position < input.length()) {
            char c = input.charAt(position);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') break;
            position++;
        }
    }
    private boolean take(char expected) {
        if (position < input.length() && input.charAt(position) == expected) { position++; return true; }
        return false;
    }
    private void require(char expected) { if (!take(expected)) throw invalid("expected " + expected); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
