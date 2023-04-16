package org.modelingvalue.dclare;

import org.modelingvalue.collections.DefaultMap;

import java.io.Serializable;
import java.util.Comparator;
import java.util.stream.Collectors;

@SuppressWarnings("rawtypes")
public class StateMap implements Serializable {
    public static final  DefaultMap<Setable, Object> EMPTY_SETABLES_MAP = DefaultMap.of(Getable::getDefault);
    public static final  StateMap                    EMPTY_STATE_MAP    = new StateMap(DefaultMap.of(o -> EMPTY_SETABLES_MAP));
    private static final long                        serialVersionUID   = -7537530409013376084L;

    private final DefaultMap<Object, DefaultMap<Setable, Object>> map;

    public StateMap(StateMap stateMap) {
        this(stateMap.map);
    }

    protected StateMap(DefaultMap<Object, DefaultMap<Setable, Object>> map) {
        this.map = map;
    }

    protected DefaultMap<Object, DefaultMap<Setable, Object>> map() {
        return map;
    }

    public StateMap getStateMap() {
        return new StateMap(map);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public String toString() {
        return map
                .sorted(Comparator.comparing(e2 -> e2.getKey().toString()))
                .map(e1 -> {
                    String val = e1.getValue()
                            .sorted(Comparator.comparing(e2 -> e2.getKey().toString()))
                            .map(e2 -> String.format("%-50s = %s", e2.getKey(), e2.getValue()))
                            .collect(Collectors.joining("\n    ", "    ", "\n"));
                    return String.format("%-50s =\n%s", e1.getKey().toString(), val);
                })
                .collect(Collectors.joining("\n"));
    }

    @SuppressWarnings("unchecked")
    public <O, T> T get(O obj, Setable<O, T> settable) {
        return (T) map.get(obj).get(settable);
    }

    public <O, T> StateMap clear(O obj, Setable<O, T> settable) {
        return new StateMap(map.put(obj, map.get(obj).removeKey(settable)));
    }
}
