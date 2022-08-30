package org.modelingvalue.dclare;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.modelingvalue.collections.util.ContextThread;

public class DclareTrace {

    private static final int      TAG_LENGTH        = 8;
    private static final int      TX_TYPE_LENGTH    = 2;
    private static final int      THREAD_NUM_LENGTH = 2;
    private static final int      ONE_INDENT_LENGTH = 2;
    private static final String[] INDENT_ARRAY      = IntStream.range(0, 50).mapToObj(DclareTrace::makeIndent).toArray(String[]::new);
    private static final String[] THREAD_NAME_ARRAY = IntStream.range(0, 99).mapToObj(i -> String.format("%02d", i)).toArray(String[]::new);
    private static final String   TRACE_BASE        = fill(TAG_LENGTH + 1 + THREAD_NUM_LENGTH + 1 + TX_TYPE_LENGTH, '_');

    //  produces a string like: "DCLARE__06_OB            "
    public static String getLineStart(String tag) {
        StringBuilder b = new StringBuilder(TRACE_BASE);
        superImpose(b, tag.length() <= TAG_LENGTH ? tag : tag.substring(0, TAG_LENGTH), 0);
        superImpose(b, getThreadNum(ContextThread.getNr()), TAG_LENGTH + 1);
        LeafTransaction current = LeafTransaction.getCurrent();
        if (current != null) {
            superImpose(b, current.getCurrentTypeForTrace(), TAG_LENGTH + 1 + THREAD_NUM_LENGTH + 1);
        }
        b.append(' ').append(getIndent(current != null ? current.depth() : 0));
        return b.toString();
    }

    private static String getIndent(int i) {
        if (0 <= i && i <= 2000) {
            return i < INDENT_ARRAY.length ? INDENT_ARRAY[i] : makeIndent(i);
        } else {
            return "...(indent=" + i + "???)...";
        }
    }

    private static String getThreadNum(int i) {
        return 0 <= i && i <= 99 ? THREAD_NAME_ARRAY[i] : "??";
    }

    private static String makeIndent(int i) {
        return fill(i * ONE_INDENT_LENGTH, ' ');
    }

    private static void superImpose(StringBuilder base, String over, int at) {
        base.replace(at, at + over.length(), over);
    }

    private static String fill(int i, char c) {
        char[] a = new char[i];
        Arrays.fill(a, c);
        return new String(a);
    }

}
