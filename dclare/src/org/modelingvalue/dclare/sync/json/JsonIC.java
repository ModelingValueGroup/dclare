package org.modelingvalue.dclare.sync.json;

import java.util.AbstractMap.*;
import java.util.*;
import java.util.Map.Entry;

import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;

public class JsonIC extends Json {
    public static String toJson(Object o) {
        return new ToJsonIC(o).toJson();
    }

    public static Object fromJson(String s) {
        return new FromJsonIC(s).fromJson();
    }

    public static class ToJsonIC extends ToJson<Iterable<Object>, Map<Object, Object>> {
        public ToJsonIC(Object root) {
            super(root);
        }

        @Override
        protected boolean isArrayType(Object o) {
            return o instanceof Iterable;
        }

        @Override
        protected boolean isMapType(Object o) {
            return o instanceof java.util.Map || o instanceof Map;
        }

        @Override
        protected Iterator<Entry<Object, Object>> getMapIterator(Map<Object, Object> o) {
            return o.<Entry<Object, Object>> map(e1 -> new SimpleEntry<>(e1.getKey(), e1.getValue())).sorted(Comparator.comparing(e -> e.getKey().toString())).iterator();
        }

        @Override
        protected Iterator<Object> getArrayIterator(Iterable<Object> o) {
            return o.iterator();
        }
    }

    public static class FromJsonIC extends FromJson<List<Object>, Map<String, Object>> {
        public FromJsonIC(String input) {
            super(input);
        }

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
            return m.put(key, value);
        }

        @Override
        protected List<Object> makeArrayEntry(List<Object> l, Object o) {
            return l.add(o);
        }
    }
}
