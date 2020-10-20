//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2019 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
//                                                                                                                     ~
// Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in      ~
// compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0  ~
// Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on ~
// an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the  ~
// specific language governing permissions and limitations under the License.                                          ~
//                                                                                                                     ~
// Maintainers:                                                                                                        ~
//     Wim Bast, Tom Brus, Ronald Krijgsheld                                                                           ~
// Contributors:                                                                                                       ~
//     Arjan Kok, Carel Bast                                                                                           ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.dclare.sync.json;

import java.util.*;
import java.util.Map.*;

public class ToJson {
    private static final String NULL_STRING = "null";

    private final StringBuilder b = new StringBuilder();
    private       int           level;
    private       int           index;

    public String toJson(Object root) {
        b.setLength(0);
        level = 0;
        index = 0;
        jsonFromAny(root);
        return b.toString();
    }

    public int getLevel() {
        return level;
    }

    public int getIndex() {
        return index;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    protected Object filter(Object o) {
        return o;
    }

    protected boolean isMapType(Object o) {
        return o instanceof Map;
    }

    protected Iterator<Entry<Object, Object>> getMapIterator(Object o) {
        //noinspection unchecked
        return ((Map<Object, Object>) o).entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString())).iterator();
    }

    protected boolean isArrayType(Object o) {
        return o instanceof Iterable;
    }

    protected Iterator<Object> getArrayIterator(Object o) {
        //noinspection unchecked
        return ((Iterable<Object>) o).iterator();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    protected void jsonFromAny(Object o) {
        o = filter(o);
        if (o == null) {
            b.append(NULL_STRING);

        } else if (isMapType(o)) {
            jsonFromMap(o);
        } else if (isArrayType(o)) {
            jsonFromArray(o);

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
            jsonFromUnknown(o);
        }
    }

    protected void jsonFromUnknown(Object o) {
        throw new IllegalArgumentException("can not render object (" + o + ") of class " + o.getClass().getName());
    }

    protected void jsonFromMap(Object o) {
        b.append('{');
        Iterator<Entry<Object, Object>> it  = getMapIterator(o);
        String                          sep = "";
        level++;
        int savedIndex = index;
        index = 0;
        while (it.hasNext()) {
            Entry<Object, Object> e = it.next();
            b.append(sep);
            sep = ",";           
            jsonFromString(stringFromKey(e.getKey()));
            b.append(":");
            jsonFromAny(e.getValue());
            index++;
        }
        index = savedIndex;
        level--;
        b.append('}');
    }

    protected String stringFromKey(Object key) {
        return Objects.requireNonNull(key, "can not make json: a map contains a null key").toString();
    }

    protected void jsonFromArray(Object o) {
        b.append('[');
        Iterator<Object> it  = getArrayIterator(o);
        String           sep = "";
        level++;
        int savedIndex = index;
        index = 0;
        while (it.hasNext()) {
            b.append(sep);
            sep = ",";
            jsonFromAny(it.next());
            index++;
        }
        index = savedIndex;
        level--;
        b.append(']');
    }

    protected void jsonFromByteArray(byte[] o) {
        b.append('[');
        String sep = "";
        level++;
        int savedIndex = index;
        index = 0;
        for (byte oo : o) {
            b.append(sep);
            sep = ",";
            b.append(oo);
            index++;
        }
        index = savedIndex;
        level--;
        b.append(']');
    }

    protected void jsonFromBooleanArray(boolean[] o) {
        b.append('[');
        String sep = "";
        level++;
        int savedIndex = index;
        index = 0;
        for (boolean oo : o) {
            b.append(sep);
            sep = ",";
            b.append(oo);
            index++;
        }
        index = savedIndex;
        level--;
        b.append(']');
    }

    protected void jsonFromObjectArray(Object[] o) {
        b.append('[');
        String sep = "";
        level++;
        int savedIndex = index;
        index = 0;
        for (Object oo : o) {
            b.append(sep);
            sep = ",";
            jsonFromAny(oo);
            index++;
        }
        index = savedIndex;
        level--;
        b.append(']');
    }

    protected void jsonFromDoubleArray(double[] o) {
        b.append('[');
        String sep = "";
        level++;
        int savedIndex = index;
        index = 0;
        for (double oo : o) {
            b.append(sep);
            sep = ",";
            b.append(oo);
            index++;
        }
        index = savedIndex;
        level--;
        b.append(']');
    }

    protected void jsonFromFloatArray(float[] o) {
        b.append('[');
        String sep = "";
        level++;
        int savedIndex = index;
        index = 0;
        for (float oo : o) {
            b.append(sep);
            sep = ",";
            b.append(oo);
            index++;
        }
        index = savedIndex;
        level--;
        b.append(']');
    }

    protected void jsonFromCharArray(char[] o) {
        b.append('[');
        String sep = "";
        level++;
        int savedIndex = index;
        index = 0;
        for (char oo : o) {
            b.append(sep);
            sep = ",";
            jsonFromCharacter(oo);
            index++;
        }
        index = savedIndex;
        level--;
        b.append(']');
    }

    protected void jsonFromLongArray(long[] o) {
        b.append('[');
        String sep = "";
        level++;
        int savedIndex = index;
        index = 0;
        for (long oo : o) {
            b.append(sep);
            sep = ",";
            b.append(oo);
            index++;
        }
        index = savedIndex;
        level--;
        b.append(']');
    }

    protected void jsonFromShortArray(short[] o) {
        b.append('[');
        String sep = "";
        level++;
        int savedIndex = index;
        index = 0;
        for (short oo : o) {
            b.append(sep);
            sep = ",";
            b.append(oo);
            index++;
        }
        index = savedIndex;
        level--;
        b.append(']');
    }

    protected void jsonFromIntArray(int[] o) {
        b.append('[');
        String sep = "";
        level++;
        int savedIndex = index;
        index = 0;
        for (int oo : o) {
            b.append(sep);
            sep = ",";
            b.append(oo);
            index++;
        }
        index = savedIndex;
        level--;
        b.append(']');
    }

    protected void jsonFromStringArray(String[] o) {
        b.append('[');
        String sep = "";
        level++;
        int savedIndex = index;
        index = 0;
        for (String oo : o) {
            b.append(sep);
            sep = ",";
            jsonFromString(oo);
            index++;
        }
        index = savedIndex;
        level--;
        b.append(']');
    }

    protected void jsonFromCharacter(char o) {
        b.append('"');
        appendStringCharacter(o);
        b.append('"');
    }

    protected void jsonFromString(String o) {
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
