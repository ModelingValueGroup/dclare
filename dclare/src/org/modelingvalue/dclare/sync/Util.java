package org.modelingvalue.dclare.sync;

public class Util {
    /////////////////////////////////////////////////////////////////////////////////
    public static String encodeWithLength(String... ss) {
        return encodeWithLength(new StringBuilder(), ss).toString();
    }

    public static StringBuilder encodeWithLength(StringBuilder b, String... ss) {
        for (String s : ss) {
            if (b.length() != 0) {
                b.append(',');
            }
            b.append(s.length()).append(':').append(s);
        }
        return b;
    }

    public static String[] decodeFromLength(String s, int num) {
        String[] parts = new String[num];
        for (int i = 0; i < num && s != null; i++) {
            String[] split  = s.split("[^0-9]", 2);
            int      length = Integer.parseInt(split[0]);
            parts[i] = split[1].substring(0, length);
            s = i + 1 == num ? null : split[1].substring(length + 1);
        }
        return parts;
    }
}
