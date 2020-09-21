package org.modelingvalue.dclare.sync.json;

import java.util.*;
import java.util.Map.*;

public class Json {
    public static String toJson(Object o) {
        return new ToJsonDefault(o).toJson();
    }

    public static Object fromJson(String s) {
        return new FromJsonDefault(s).fromJson();
    }

    public static String formatNoWhitespace(String json) {
        return format(false, json, null);
    }

    public static String formatPretty(String json) {
        return formatPretty(json, "  ");
    }

    public static String formatPretty(String json, String indentString) {
        return format(true, formatNoWhitespace(json), indentString);
    }

    private static String format(boolean pretty, String json, String indentString) {
        StringBuilder b         = new StringBuilder();
        int           indent    = 0;
        boolean       inQuote   = false;
        char[]        charArray = json.toCharArray();
        for (int i = 0; i < charArray.length; i++) {
            char c = charArray[i];
            if (inQuote) {
                switch (c) {
                case '\\':
                    b.append(c);
                    c = charArray[++i];
                    b.append(c);
                    break;
                case '"':
                    b.append(c);
                    inQuote = false;
                    break;
                default:
                    b.append(c);
                }
            } else {
                switch (c) {
                case '"':
                    b.append(c);
                    inQuote = true;
                    break;
                case '{':
                case '[':
                    b.append(c);
                    if (pretty) {
                        appendIndent(indentString, ++indent, b);
                    }
                    break;
                case '}':
                case ']':
                    if (pretty) {
                        appendIndent(indentString, --indent, b);
                    }
                    b.append(c);
                    break;
                case ',':
                    b.append(c);
                    if (pretty) {
                        appendIndent(indentString, indent, b);
                    }
                    break;
                case ':':
                    b.append(": ");
                    break;
                case ' ':
                case '\r':
                case '\n':
                case '\t':
                    break;
                default:
                    b.append(c);
                }
            }
        }
        return b.toString();
    }

    private static void appendIndent(String indentString, int indentLevel, StringBuilder b) {
        b.append("\n").append(indentString.repeat(Math.max(0, indentLevel)));
    }

    private static class ToJsonDefault extends ToJson<Iterable<Object>, Map<Object, Object>> {
        public ToJsonDefault(Object root) {
            super(root);
        }

        @Override
        protected boolean isArrayType(Object o) {
            return o instanceof Iterable;
        }

        @Override
        protected boolean isMapType(Object o) {
            return o instanceof Map;
        }

        @Override
        protected Iterator<Entry<Object, Object>> getMapIterator(Map<Object, Object> o) {
            return o.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString())).iterator();
        }

        @Override
        protected Iterator<Object> getArrayIterator(Iterable<Object> o) {
            return o.iterator();
        }
    }

    private static class FromJsonDefault extends FromJson<Iterable<Object>, Map<String, Object>> {
        public FromJsonDefault(String input) {
            super(input);
        }

        @Override
        protected HashMap<String, Object> makeMap() {
            return new HashMap<>();
        }

        @Override
        protected Iterable<Object> makeArray() {
            return new ArrayList<>();
        }

        @Override
        protected Map<String, Object> makeMapEntry(Map<String, Object> m, String key, Object value) {
            m.put(key, value);
            return m;
        }

        @Override
        protected Iterable<Object> makeArrayEntry(Iterable<Object> l, Object o) {
            ((List<Object>) l).add(o);
            return l;
        }
    }
}

//0900 doorverbinden debiteurenbeheer
