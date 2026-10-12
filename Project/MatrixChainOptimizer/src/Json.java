import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Tiny JSON writer so the project needs no external libraries. */
final class Json {
    private static final long JS_SAFE = 9007199254740991L; // 2^53 - 1

    private Json() {}

    static String write(Object o) {
        StringBuilder sb = new StringBuilder(4096);
        w(sb, o);
        return sb.toString();
    }

    private static void w(StringBuilder sb, Object o) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String) {
            quote(sb, (String) o);
        } else if (o instanceof BigInteger) {
            quote(sb, o.toString());
        } else if (o instanceof Boolean) {
            sb.append(o);
        } else if (o instanceof Double || o instanceof Float) {
            double d = ((Number) o).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) sb.append("null");
            else sb.append(String.format(Locale.ROOT, "%.2f", d));
        } else if (o instanceof Number) {
            long v = ((Number) o).longValue();
            // JavaScript cannot represent integers above 2^53 exactly, so send them as strings.
            if (Math.abs(v) > JS_SAFE) quote(sb, Long.toString(v));
            else sb.append(v);
        } else if (o instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                w(sb, e.getValue());
            }
            sb.append('}');
        } else if (o instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object x : (List<?>) o) {
                if (!first) sb.append(',');
                first = false;
                w(sb, x);
            }
            sb.append(']');
        } else if (o instanceof int[]) {
            int[] a = (int[]) o;
            sb.append('[');
            for (int i = 0; i < a.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(a[i]);
            }
            sb.append(']');
        } else if (o instanceof long[]) {
            long[] a = (long[]) o;
            sb.append('[');
            for (int i = 0; i < a.length; i++) {
                if (i > 0) sb.append(',');
                w(sb, a[i]);
            }
            sb.append(']');
        } else if (o instanceof Object[]) {
            Object[] a = (Object[]) o;
            sb.append('[');
            for (int i = 0; i < a.length; i++) {
                if (i > 0) sb.append(',');
                w(sb, a[i]);
            }
            sb.append(']');
        } else {
            quote(sb, o.toString());
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }
}
