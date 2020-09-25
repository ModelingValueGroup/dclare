package org.modelingvalue.dclare.sync.json;

import java.util.AbstractMap.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.*;

import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;

public class JsonIC extends Json {
    public static String toJson(Object o) {
        return new ToJsonIC().toJson(o);
    }

    public static Object fromJson(String s) {
        return new FromJsonIC().fromJson(s);
    }

    public static class ToJsonIC extends ToJson {
        @Override
        protected boolean isMapType(Object o) {
            return super.isMapType(o) || o instanceof Map;
        }

        @Override
        protected Iterator<Entry<Object, Object>> getMapIterator(Object o) {
            if (o instanceof Map) {
                Stream<Entry<Object, Object>> entryStream = ((Map<Object, Object>) o).map(e1 -> new SimpleEntry<>(e1.getKey(), e1.getValue()));
                return entryStream.sorted(Comparator.comparing(e -> e.getKey().toString())).iterator();
            } else {
                return super.getMapIterator(o);
            }
        }
    }

    public static class FromJsonIC extends FromJsonBase<List<Object>, Map<String, Object>> {
        @Override
        protected Map<String, Object> makeMap() {
            return Map.of();
        }

        @Override
        protected List<Object> makeArray() {
            return List.of();
        }

        @Override
        protected Map<String, Object> makeMapEntry(Map<String, Object> m, String key, Object value) {
            return m == null ? null : m.put(key, value);
        }

        @Override
        protected List<Object> makeArrayEntry(List<Object> l, Object o) {
            return l == null ? null : l.add(o);
        }
    }
}
