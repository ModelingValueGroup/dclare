package org.modelingvalue.dclare.sync.json;

import java.util.*;
import java.util.function.*;

public abstract class FromJson<ARRAY_TYPE, MAP_TYPE> {
    private static final String  TRUE_STRING  = "true";
    private static final String  FALSE_STRING = "false";
    private static final String  NULL_STRING  = "null";
    private static final char    EOF_CHAR     = '\000';
    //
    private final        String  input;
    private final        int     inputLength;
    //
    private              int     i;
    private              char    current;
    private              boolean eof;

    public FromJson(String input) {
        this.input = Objects.requireNonNull(input);
        inputLength = input.length();
        next(0);
    }

    public Object fromJson() {
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

    protected abstract MAP_TYPE makeMap();

    protected abstract ARRAY_TYPE makeArray();

    protected abstract MAP_TYPE makeMapEntry(MAP_TYPE m, String key, Object value);

    protected abstract ARRAY_TYPE makeArrayEntry(ARRAY_TYPE l, Object o);

    protected MAP_TYPE closeMap(MAP_TYPE m) {
        return m;
    }

    protected ARRAY_TYPE closeArray(ARRAY_TYPE l) {
        return l;
    }

    @SuppressWarnings("unused")
    protected void detectedWhitespace(int offset, Supplier<String> stringSupplier) {
    }

    protected IllegalArgumentException error() {
        String pre   = input.substring(Math.max(0, i - 20), Math.min(i, input.length()));
        String where = "<" + (eof ? "PAST END" : input.charAt(i)) + ">";
        String post  = input.substring(Math.min(input.length(), i + 1), Math.min(input.length(), i + 20));
        return new IllegalArgumentException("json syntax error (at index " + i + ": " + pre + where + post + ")");
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    private void next() {
        next(1);
    }

    private void next(int skip) {
        i += skip;
        eof = inputLength <= i;
        current = eof ? EOF_CHAR : input.charAt(i);
    }

    private Object parseElement() {
        skipWS();
        Object o = parseValue();
        skipWS();
        return o;
    }

    private Object parseValue() {
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

    private MAP_TYPE parseMap() {
        MAP_TYPE m = makeMap();
        next();
        skipWS();
        if (current != '}') {
            A:
            while (true) {
                String key = parseString();
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
            }
        }
        next();
        return closeMap(m);
    }

    private ARRAY_TYPE parseArray() {
        ARRAY_TYPE l = makeArray();
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
            }
        }
        next();
        return closeArray(l);
    }

    private String parseString() {
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

    private Object parseNumber() {
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

    private Object parseTrue() {
        if (!input.startsWith(TRUE_STRING, i)) {
            throw error();
        }
        next(TRUE_STRING.length());
        return true;
    }

    private Object parseFalse() {
        if (!input.startsWith(FALSE_STRING, i)) {
            throw error();
        }
        next(FALSE_STRING.length());
        return false;
    }

    private Object parseNull() {
        if (!input.startsWith(NULL_STRING, i)) {
            throw error();
        }
        next(NULL_STRING.length());
        return null;
    }

    private void skipWS() {
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
