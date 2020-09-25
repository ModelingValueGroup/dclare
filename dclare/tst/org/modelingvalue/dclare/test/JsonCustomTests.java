package org.modelingvalue.dclare.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.stream.*;

import org.junit.jupiter.api.*;
import org.modelingvalue.dclare.sync.json.*;

public class JsonCustomTests {
    @RepeatedTest(1)
    public void counters() {
        assertEquals("1,0,0,0,0,0,0", new CountingTesterFromJson().fromJson("[]"));
        assertEquals("3,2,3,3,4,0,0", new CountingTesterFromJson().fromJson("[[],[],{\"a\":{\"b\":\"aa\"},\"c\":21}]"));

        String testData = readData("test.json");
        assertEquals(592 + 22, testData.replaceAll("[^\\[]", "").length());
        assertEquals(519 + 29, testData.replaceAll("[^{]", "").length());
        assertEquals(592 + 22, Json.toJson(Json.fromJson(testData)).replaceAll("[^\\[]", "").length());
        assertEquals(519 + 29, Json.toJson(Json.fromJson(testData)).replaceAll("[^{]", "").length());
        assertEquals("592,519,1588,1331,1735,22,29", new CountingTesterFromJson().fromJson(testData));
    }

    public String readData(String name) {
        try (BufferedReader is = new BufferedReader(new InputStreamReader(JsonCustomTests.class.getResourceAsStream(name)))) {
            return is.lines().collect(Collectors.joining());
        } catch (IOException e) {
            fail();
            return null;
        }
    }

    private static class CountingTesterFromJson extends FromJsonBase<Void, Void> {
        private int numArrays;
        private int numMaps;
        private int numArrayEntries;
        private int numMapEntries;
        private int numStrings;
        private int numBracketsString;
        private int numCurliesString;

        @Override
        protected Void makeMap() {
            numMaps++;
            return null;
        }

        @Override
        protected Void makeArray() {
            numArrays++;
            return null;
        }

        @Override
        protected Void makeMapEntry(Void m, String key, Object value) {
            numMapEntries++;
            countString(key);
            countString(value);
            return null;
        }

        @Override
        protected Void makeArrayEntry(Void l, Object o) {
            numArrayEntries++;
            countString(o);
            return null;
        }

        private void countString(Object v) {
            if (v instanceof String) {
                numStrings++;
                String s = (String) v;
                numBracketsString += s.replaceAll("[^\\[]", "").length();
                numCurliesString += s.replaceAll("[^{]", "").length();
            }
        }

        @Override
        protected Object end(Object root) {
            return numArrays + "," + numMaps + "," + numArrayEntries + "," + numMapEntries + "," + numStrings + "," + numBracketsString + "," + numCurliesString;
        }
    }
}
