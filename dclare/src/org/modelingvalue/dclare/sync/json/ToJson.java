package org.modelingvalue.dclare.sync.json;

import java.util.*;
import java.util.Map.*;

public abstract class ToJson<ARRAY_TYPE, MAP_TYPE> {
    private static final String NULL_STRING = "null";

    private final StringBuilder b = new StringBuilder();

    public ToJson(Object root) {
        jsonFromAny(root);
    }

    public String toJson() {
        return b.toString();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    protected abstract boolean isArrayType(Object o);

    protected abstract boolean isMapType(Object o);

    protected abstract Iterator<Object> getArrayIterator(ARRAY_TYPE o);

    protected abstract Iterator<Entry<Object, Object>> getMapIterator(MAP_TYPE o);

    protected Object replace(Object o) {
        throw new IllegalArgumentException("can not render object of class " + o.getClass().getName());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    private void jsonFromAny(Object o) {
        if (o == null) {
            b.append(NULL_STRING);

        } else if (isMapType(o)) {
            //noinspection unchecked
            jsonFromMap((MAP_TYPE) o);
        } else if (isArrayType(o)) {
            //noinspection unchecked
            jsonFromArray((ARRAY_TYPE) o);

        } else if (o instanceof String) {
            jsonFromString((String) o);
        } else if (o instanceof Byte) {
            b.append((byte) o);
        } else if (o instanceof Short) {
            b.append((short) o);
        } else if (o instanceof Integer) {
            b.append((int) o);
        } else if (o instanceof Long) {
            b.append((long) o);
        } else if (o instanceof Character) {
            jsonFromCharacter((Character) o);
        } else if (o instanceof Float) {
            b.append((float) o);
        } else if (o instanceof Double) {
            b.append((double) o);
        } else if (o instanceof Boolean) {
            b.append((boolean) o);

        } else if (o instanceof String[]) {
            jsonFromStringArray((String[]) o);
        } else if (o instanceof byte[]) {
            jsonFromByteArray((byte[]) o);
        } else if (o instanceof short[]) {
            jsonFromShortArray((short[]) o);
        } else if (o instanceof int[]) {
            jsonFromIntArray((int[]) o);
        } else if (o instanceof long[]) {
            jsonFromLongArray((long[]) o);
        } else if (o instanceof char[]) {
            jsonFromCharArray((char[]) o);
        } else if (o instanceof float[]) {
            jsonFromFloatArray((float[]) o);
        } else if (o instanceof double[]) {
            jsonFromDoubleArray((double[]) o);
        } else if (o instanceof boolean[]) {
            jsonFromBooleanArray((boolean[]) o);
        } else if (o instanceof Object[]) {
            jsonFromObjectArray((Object[]) o);
        } else {
            jsonFromAny(replace(o));
        }
    }

    private void jsonFromMap(MAP_TYPE o) {
        b.append('{');
        Iterator<Entry<Object, Object>> it  = getMapIterator(o);
        String                          sep = "";
        while (it.hasNext()) {
            Entry<Object, Object> e = it.next();
            b.append(sep);
            sep = ",";
            jsonFromString(Objects.requireNonNull(e.getKey(), "can not make json: a map contains a null key").toString());
            b.append(":");
            jsonFromAny(e.getValue());
        }
        b.append('}');
    }

    private void jsonFromArray(ARRAY_TYPE o) {
        b.append('[');
        Iterator<Object> it  = getArrayIterator(o);
        String           sep = "";
        while (it.hasNext()) {
            b.append(sep);
            sep = ",";
            jsonFromAny(it.next());
        }
        b.append(']');
    }

    private void jsonFromByteArray(byte[] o) {
        b.append('[');
        String sep = "";
        for (byte oo : o) {
            b.append(sep);
            sep = ",";
            b.append(oo);
        }
        b.append(']');
    }

    private void jsonFromBooleanArray(boolean[] o) {
        b.append('[');
        String sep = "";
        for (boolean oo : o) {
            b.append(sep);
            sep = ",";
            b.append(oo);
        }
        b.append(']');
    }

    private void jsonFromObjectArray(Object[] o) {
        b.append('[');
        String sep = "";
        for (Object oo : o) {
            b.append(sep);
            sep = ",";
            jsonFromAny(oo);
        }
        b.append(']');
    }

    private void jsonFromDoubleArray(double[] o) {
        b.append('[');
        String sep = "";
        for (double oo : o) {
            b.append(sep);
            sep = ",";
            b.append(oo);
        }
        b.append(']');
    }

    private void jsonFromFloatArray(float[] o) {
        b.append('[');
        String sep = "";
        for (float oo : o) {
            b.append(sep);
            sep = ",";
            b.append(oo);
        }
        b.append(']');
    }

    private void jsonFromCharArray(char[] o) {
        b.append('[');
        String sep = "";
        for (char oo : o) {
            b.append(sep);
            sep = ",";
            jsonFromCharacter(oo);
        }
        b.append(']');
    }

    private void jsonFromLongArray(long[] o) {
        b.append('[');
        String sep = "";
        for (long oo : o) {
            b.append(sep);
            sep = ",";
            b.append(oo);
        }
        b.append(']');
    }

    private void jsonFromShortArray(short[] o) {
        b.append('[');
        String sep = "";
        for (short oo : o) {
            b.append(sep);
            sep = ",";
            b.append(oo);
        }
        b.append(']');
    }

    private void jsonFromIntArray(int[] o) {
        b.append('[');
        String sep = "";
        for (int oo : o) {
            b.append(sep);
            sep = ",";
            b.append(oo);
        }
        b.append(']');
    }

    private void jsonFromStringArray(String[] o) {
        b.append('[');
        String sep = "";
        for (String oo : o) {
            b.append(sep);
            sep = ",";
            jsonFromString(oo);
        }
        b.append(']');
    }

    private void jsonFromCharacter(char o) {
        b.append('"');
        appendStringCharacter(o);
        b.append('"');
    }

    private void jsonFromString(String o) {
        b.append('"');
        final int length = o.length();
        for (int i = 0; i < length; i++) {
            appendStringCharacter(o.charAt(i));
        }
        b.append('"');
    }

    private void appendStringCharacter(char ch) {
        switch (ch) {
        case '"':
            b.append("\\\"");
            break;
        case '/':
            b.append("\\/");
            break;
        case '\\':
            b.append("\\\\");
            break;
        case '\b':
            b.append("\\b");
            break;
        case '\f':
            b.append("\\f");
            break;
        case '\n':
            b.append("\\n");
            break;
        case '\r':
            b.append("\\r");
            break;
        case '\t':
            b.append("\\t");
            break;
        default:
            // see: https://www.unicode.org
            if (ch <= '\u001F' || ('\u007F' <= ch && ch <= '\u009F') || ('\u2000' <= ch && ch <= '\u20FF')) {
                String ss = Integer.toHexString(ch);
                b.append("\\u");
                b.append("0".repeat(4 - ss.length()));
                b.append(ss.toUpperCase());
            } else {
                b.append(ch);
            }
        }
    }
}
