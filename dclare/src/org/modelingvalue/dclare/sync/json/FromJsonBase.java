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
import java.util.function.*;

public class FromJsonBase<ARRAY_TYPE, MAP_TYPE> {
    private static final String  TRUE_STRING  = "true";
    private static final String  FALSE_STRING = "false";
    private static final String  NULL_STRING  = "null";
    private static final char    EOF_CHAR     = '\000';
    //
    private              String  input;
    private              int     inputLength;
    //
    private              int     i;
    private              char    current;
    private              boolean eof;
    private              int     level;
    private              int     index;

    public Object fromJson(String input) {
        this.input = Objects.requireNonNull(input);
        inputLength = input.length();
        i = 0;
        level = 0;
        index = 0;
        next(0);
        begin();
        Object root = parseElement();
        if (!eof) {
            throw error();
        }
        return end(root);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    protected void begin() {
    }

    protected Object end(Object root) {
        return root;
    }

    protected MAP_TYPE makeMap() {
        return null;
    }

    protected ARRAY_TYPE makeArray() {
        return null;
    }

    protected String makeMapKey(String key) {
        return key;
    }

    protected MAP_TYPE makeMapEntry(MAP_TYPE m, String key, Object value) {
        return m;
    }

    protected ARRAY_TYPE makeArrayEntry(ARRAY_TYPE l, Object o) {
        return l;
    }

    protected Object closeMap(MAP_TYPE m) {
        return m;
    }

    protected Object closeArray(ARRAY_TYPE l) {
        return l;
    }

    @SuppressWarnings("unused")
    protected void detectedWhitespace(int offset, Supplier<String> stringSupplier) {
    }

    @SuppressWarnings("unused")
    protected int getLevel() {
        return level;
    }

    @SuppressWarnings("unused")
    protected int getIndex() {
        return index;
    }

    protected IllegalArgumentException error() {
        String pre   = input.substring(Math.max(0, i - 20), Math.min(i, input.length()));
        String where = "<" + (eof ? "PAST END" : input.charAt(i)) + ">";
        String post  = input.substring(Math.min(input.length(), i + 1), Math.min(input.length(), i + 20));
        return new IllegalArgumentException("json syntax error (at index " + i + ": " + pre + where + post + ")");
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    protected void next() {
        next(1);
    }

    protected void next(int skip) {
        i += skip;
        eof = inputLength <= i;
        current = eof ? EOF_CHAR : input.charAt(i);
    }

    protected Object parseElement() {
        skipWS();
        Object o = parseValue();
        skipWS();
        return o;
    }

    protected Object parseValue() {
        if (eof) {
            throw error();
        }
        switch (current) {
        case '{':
            return parseMap();
        case '[':
            return parseArray();
        case '"':
            return parseString();
        case '+':
        case '-':
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
            return parseNumber();
        case 't'/*true*/:
            return parseTrue();
        case 'f'/*false*/:
            return parseFalse();
        case 'n'/*null*/:
            return parseNull();
        }
        throw error();
    }

    protected Object parseMap() {
        MAP_TYPE m = makeMap();
        level++;
        int savedIndex = index;
        index = 0;
        next();
        skipWS();
        if (current != '}') {
            A:
            while (true) {
                String key = makeMapKey(parseString());
                skipWS();
                if (current != ':') {
                    throw error();
                }
                next();
                skipWS();
                m = makeMapEntry(m, key, parseValue());
                skipWS();
                switch (current) {
                case ',':
                    next();
                    skipWS();
                    break;
                case '}':
                    break A;
                default:
                    throw error();
                }
                index++;
            }
        }
        next();
        index = savedIndex;
        level--;
        return closeMap(m);
    }

    protected Object parseArray() {
        ARRAY_TYPE l = makeArray();
        level++;
        int savedIndex = index;
        index = 0;
        next();
        skipWS();
        if (current != ']') {
            A:
            while (true) {
                l = makeArrayEntry(l, parseValue());
                skipWS();
                switch (current) {
                case ',':
                    next();
                    skipWS();
                    break;
                case ']':
                    break A;
                default:
                    throw error();
                }
                index++;
            }
        }
        next();
        index = savedIndex;
        level--;
        return closeArray(l);
    }

    protected String parseString() {
        StringBuilder b = new StringBuilder();
        next();
        while (true) {
            if (eof) {
                throw error();
            } else if (current == '\\') {
                next();
                switch (current) {
                case '"':
                case '\\':
                case '/':
                    b.append(current);
                    break;
                case 'b':
                    b.append('\b');
                    break;
                case 'f':
                    b.append('\f');
                    break;
                case 'n':
                    b.append('\n');
                    break;
                case 'r':
                    b.append('\r');
                    break;
                case 't':
                    b.append('\t');
                    break;
                case 'u':
                    next();
                    if (inputLength <= i + 4) {
                        throw error();
                    }
                    int hex = Integer.parseInt(input, i, i + 4, 16);
                    b.append((char) hex);
                    next(3);
                    break;
                default:
                    throw error();
                }
                next();
            } else if (current == '"') {
                next();
                return b.toString();
            } else {
                b.append(current);
                next();
            }
        }
    }

    protected Object parseNumber() {
        int     start    = i;
        boolean isDouble = false;
        A:
        while (true) {
            switch (current) {
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
            case '+':
            case '-':
                next();
                break;
            case '.':
            case 'e':
            case 'E':
                isDouble = true;
                next();
                break;
            default:
                break A;
            }
        }
        Character c0 = input.charAt(start);
        Character c1 = i <= start + 1 ? null : input.charAt(start + 1);
        if (c0 == '-') {
            c0 = c1;
            c1 = i <= start + 2 ? null : input.charAt(start + 2);
        }
        if (c0 == null
                || !('0' <= c0 && c0 <= '9')
                || (c0 == '0' && c1 != null && c1 != '.') && c1 != 'e' && c1 != 'E') {
            i = start;
            throw error();
        }
        if (isDouble) {
            //noinspection RedundantCast ## IntelliJ bug flags this as redunant: https://youtrack.jetbrains.com/issue/IDEA-251055
            return (Object) Double.parseDouble(input.substring(start, i));
        } else {
            //noinspection RedundantCast ## IntelliJ bug flags this as redunant: https://youtrack.jetbrains.com/issue/IDEA-251055
            return (Object) Long.parseLong(input, start, i, 10);
        }
    }

    protected Object parseTrue() {
        if (!input.startsWith(TRUE_STRING, i)) {
            throw error();
        }
        next(TRUE_STRING.length());
        return true;
    }

    protected Object parseFalse() {
        if (!input.startsWith(FALSE_STRING, i)) {
            throw error();
        }
        next(FALSE_STRING.length());
        return false;
    }

    protected Object parseNull() {
        if (!input.startsWith(NULL_STRING, i)) {
            throw error();
        }
        next(NULL_STRING.length());
        return null;
    }

    protected void skipWS() {
        int start = i;
        while (true) {
            switch (current) {
            case ' ':
            case '\n':
            case '\r':
            case '\t':
                next();
                break;
            default:
                if (start != i) {
                    detectedWhitespace(start, () -> input.substring(i, i - start));
                }
                return;
            }
        }
    }
}
