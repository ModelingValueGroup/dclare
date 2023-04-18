//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2023 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

package org.modelingvalue.dclare;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.collections.util.TriConsumer;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("rawtypes")
public class StateMap implements Serializable {
    private static final long                        serialVersionUID   = -7537530409013376084L;
    public static final  DefaultMap<Setable, Object> EMPTY_SETABLES_MAP = DefaultMap.of(Getable::getDefault);
    public static final  StateMap                    EMPTY_STATE_MAP    = new StateMap(DefaultMap.of(o -> EMPTY_SETABLES_MAP));
    public static final  Predicate<Object>           ALL_OBJECTS        = __ -> true;
    public static final  Predicate<Setable>          ALL_SETTABLES      = __ -> true;
    private static final Constant<Object, Object>    INTERNAL           = Constant.of("$INTERNAL", v -> {
        if (v instanceof DefaultMap) {
            ((DefaultMap<?, ?>) v).forEach(StateMap::deduplicate);
        } else if (v instanceof Map) {
            ((Map<?, ?>) v).forEach(StateMap::deduplicate);
        }
        return v;
    });

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

    public int size() {
        return map.size();
    }

    public int hashCode() {
        return map.hashCode();
    }

    public <O> DefaultMap<Setable, Object> getProperties(O object) {
        return map.get(object);
    }

    @SuppressWarnings("unchecked")
    static <O, T> T get(DefaultMap<Setable, Object> props, Setable<O, T> property) {
        return (T) props.get(property);
    }

    @SuppressWarnings("unchecked")
    static <O, A, B> A getA(DefaultMap<Setable, Object> props, Setable<O, Pair<A, B>> property) {
        Pair<A, B> pair = (Pair<A, B>) props.get(property);
        return pair != null ? pair.a() : null;
    }

    @SuppressWarnings("unchecked")
    static <O, A, B> B getB(DefaultMap<Setable, Object> props, Setable<O, Pair<A, B>> property) {
        Pair<A, B> pair = (Pair<A, B>) props.get(property);
        return pair != null ? pair.b() : null;
    }

    protected static <O, T> DefaultMap<Setable, Object> setProperties(DefaultMap<Setable, Object> props, Setable<O, T> property, T newValue) {
        return Objects.equals(property.getDefault(), newValue) ? props.removeKey(property) : props.put(property.entry(newValue, props));
    }

    @SuppressWarnings("unchecked")
    public <O> O canonical(O object) {
        Entry<Object, DefaultMap<Setable, Object>> entry = map.getEntry(object);
        return entry != null ? (O) entry.getKey() : object;
    }

    public <O, T> T get(O object, Getable<O, T> property) {
        return get(getProperties(object), (Setable<O, T>) property);
    }

    public <O, A, B> A getA(O object, Getable<O, Pair<A, B>> property) {
        return getA(getProperties(object), (Setable<O, Pair<A, B>>) property);
    }

    public <O, A, B> B getB(O object, Getable<O, Pair<A, B>> property) {
        return getB(getProperties(object), (Setable<O, Pair<A, B>>) property);
    }

    @SuppressWarnings("unchecked")
    public <O, E, T> Collection<E> getCollection(O object, Getable<O, T> property) {
        T v = get(object, property);
        return v instanceof Collection ? (Collection<E>) v : v instanceof Iterable ? Collection.of((Iterable<E>) v) : v == null ? Set.of() : Set.of((E) v);
    }

    @SuppressWarnings("unchecked")
    public static <K, V> void deduplicate(Entry<K, V> e) {
        if (e.getValue() != null) {
            e.setValueIfEqual((V) INTERNAL.object(e.getValue()));
        }
    }

    @Override
    public String toString() {
        return map.sorted(Comparator.comparing(e2 -> e2.getKey().toString())).map(e1 -> {
            String val = e1.getValue().sorted(Comparator.comparing(e2 -> e2.getKey().toString())).map(e2 -> String.format("%-50s = %s", e2.getKey(), e2.getValue())).collect(Collectors.joining("\n    ", "    ", "\n"));
            return String.format("%-50s =\n%s", e1.getKey().toString(), val);
        }).collect(Collectors.joining("\n"));
    }

    @SuppressWarnings("unchecked")
    public <O, T> T get(O obj, Setable<O, T> settable) {
        return (T) map.get(obj).get(settable);
    }

    public <O, T> StateMap clear(O obj, Setable<O, T> settable) {
        return new StateMap(map.put(obj, map.get(obj).removeKey(settable)));
    }

    public Collection<?> getObjects() {
        return getObjects(Object.class);
    }

    public <T> Collection<T> getObjects(Class<T> filter) {
        return map.toKeys().filter(filter);
    }

    public Collection<Entry<Object, Collection<Entry<Setable, Object>>>> filter(Predicate<Object> objectFilter, Predicate<Setable> setableFilter) {
        return map.filter(e1 -> objectFilter.test(e1.getKey())).map(e1 -> Entry.of(e1.getKey(), e1.getValue().filter(e2 -> setableFilter.test(e2.getKey()))));
    }

    public Collection<Entry<Object, Map<Setable, Pair<Object, Object>>>> diff(StateMap other) {
        return diff(other, StateMap.ALL_OBJECTS, StateMap.ALL_SETTABLES);
    }

    public Collection<Entry<Object, Map<Setable, Pair<Object, Object>>>> diff(StateMap other, Predicate<Object> objectFilter, Predicate<Setable> setableFilter) {
        return map.diff(other.map()).filter(d1 -> objectFilter.test(d1.getKey())).map(d2 -> {
            DefaultMap<Setable, Object>        map2 = d2.getValue().a();
            Map<Setable, Pair<Object, Object>> diff = map2.diff(d2.getValue().b()).filter(d3 -> setableFilter.test(d3.getKey())).toMap(e -> e);
            return diff.isEmpty() ? null : Entry.of(d2.getKey(), diff);
        }).notNull();
    }

    public Collection<Entry<Object, Pair<DefaultMap<Setable, Object>, DefaultMap<Setable, Object>>>> diff(StateMap other, Predicate<Object> objectFilter) {
        return map.diff(other.map()).filter(d1 -> objectFilter.test(d1.getKey()));
    }

    public void forEach(TriConsumer<Object, Setable, Object> consumer) {
        map.forEachOrdered(e0 -> e0.getValue().forEachOrdered(e1 -> consumer.accept(e0.getKey(), e1.getKey(), e1.getValue())));
    }
}
