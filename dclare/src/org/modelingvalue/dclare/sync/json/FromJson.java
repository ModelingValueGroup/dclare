package org.modelingvalue.dclare.sync.json;

import java.util.*;

public class FromJson extends FromJsonBase<Iterable<Object>, Map<String, Object>> {
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
