package org.modelingvalue.dclare.delta;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.collections.util.QuadConsumer;
import org.modelingvalue.dclare.Setable;
import org.modelingvalue.dclare.State;

@SuppressWarnings("rawtypes")
public class Delta {
    private final Map<Object, Map<Setable, Pair<Object, Object>>> changes;

    public Delta(Collection<Entry<Object, Map<Setable, Pair<Object, Object>>>> diff) {
        changes = diff.toMap(e -> e);
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    public void apply() {
        forEach((prop, obj, oldValue, newValue) -> {
            //noinspection unchecked
            ((Setable<Object, Object>) prop).set(obj, newValue);
        });
    }

    public void forEach(QuadConsumer<Setable, Object, Object, Object> f) {
        changes.forEach(e ->
                e.getValue().forEach(sv ->
                        f.accept(sv.getKey(), e.getKey(), sv.getValue().a(), sv.getValue().b())));
    }

    @Override
    public String toString() {
        return State.deltaString(changes);
    }
}
