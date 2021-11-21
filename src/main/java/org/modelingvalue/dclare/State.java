//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2021 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.*;

import org.modelingvalue.collections.*;
import org.modelingvalue.collections.util.*;

@SuppressWarnings({"rawtypes", "unused"})
public class State implements Serializable {
    private static final long                                           serialVersionUID   = -3468784705870374732L;

    public static final DefaultMap<Setable, Object>                     EMPTY_SETABLES_MAP = DefaultMap.of(Getable::getDefault);
    public static final DefaultMap<Object, DefaultMap<Setable, Object>> EMPTY_OBJECTS_MAP  = DefaultMap.of(o -> EMPTY_SETABLES_MAP);
    public static final Predicate<Object>                               ALL_OBJECTS        = __ -> true;
    public static final Predicate<Setable>                              ALL_SETTABLES      = __ -> true;
    public static final BinaryOperator<String>                          CONCAT             = (a, b) -> a + b;
    public static final BinaryOperator<String>                          CONCAT_COMMA       = (a, b) -> a.isEmpty() || b.isEmpty() ? a + b : a + "," + b;
    private static final Comparator<Entry>                              COMPARATOR         = Comparator.comparing(a -> StringUtil.toString(a.getKey()));
    private static final Constant<Object, Object>                       INTERNAL           = Constant.of("$INTERNAL", v -> {
                                                                                               if (v instanceof DefaultMap) {
                                                                                                   ((DefaultMap<?, ?>) v).forEach(State::deduplicate);
                                                                                               } else if (v instanceof Map) {
                                                                                                   ((Map<?, ?>) v).forEach(State::deduplicate);
                                                                                               }
                                                                                               return v;
                                                                                           });

    private final DefaultMap<Object, DefaultMap<Setable, Object>>       map;
    private final UniverseTransaction                                   universeTransaction;

    State(UniverseTransaction universeTransaction, DefaultMap<Object, DefaultMap<Setable, Object>> map) {
        this.universeTransaction = universeTransaction;
        this.map = map;
    }

    protected State clone(UniverseTransaction universeTransaction) {
        return new State(universeTransaction, map);
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

    public <O, T> State set(O object, Setable<O, T> property, T value) {
        DefaultMap<Setable, Object> props = getProperties(object);
        DefaultMap<Setable, Object> set = setProperties(props, property, value);
        return set != props ? set(object, set) : this;
    }

    public <O, T> State set(O object, Setable<O, T> property, T value, T[] old) {
        return set(object, property, (pre, post) -> {
            old[0] = pre;
            return post;
        }, value);
    }

    @SuppressWarnings("unchecked")
    public <O> O canonical(O object) {
        Entry<Object, DefaultMap<Setable, Object>> entry = map.getEntry(object);
        return entry != null ? (O) entry.getKey() : object;
    }

    public <O, T, E> State set(O object, Setable<O, T> property, BiFunction<T, E, T> function, E element, T[] oldNew) {
        DefaultMap<Setable, Object> props = getProperties(object);
        oldNew[0] = get(props, property);
        oldNew[1] = function.apply(oldNew[0], element);
        return !Objects.equals(oldNew[0], oldNew[1]) ? set(object, setProperties(props, property, oldNew[1])) : this;
    }

    public <O, T, E> State set(O object, Setable<O, T> property, UnaryOperator<T> oper, T[] oldNew) {
        DefaultMap<Setable, Object> props = getProperties(object);
        oldNew[0] = get(props, property);
        oldNew[1] = oper.apply(oldNew[0]);
        return !Objects.equals(oldNew[0], oldNew[1]) ? set(object, setProperties(props, property, oldNew[1])) : this;
    }

    public <O, T, E> State set(O object, Setable<O, T> property, BiFunction<T, E, T> function, E element) {
        DefaultMap<Setable, Object> props = getProperties(object);
        T preVal = get(props, property);
        T postVal = function.apply(preVal, element);
        return !Objects.equals(preVal, postVal) ? set(object, setProperties(props, property, postVal)) : this;
    }

    public <O, T, E> State set(O object, Setable<O, T> property, UnaryOperator<T> function) {
        DefaultMap<Setable, Object> props = getProperties(object);
        T preVal = get(props, property);
        T postVal = function.apply(preVal);
        return !Objects.equals(preVal, postVal) ? set(object, setProperties(props, property, postVal)) : this;
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

    <O, T> State set(O object, DefaultMap<Setable, Object> post) {
        if (post.isEmpty()) {
            DefaultMap<Object, DefaultMap<Setable, Object>> niw = map.removeKey(object);
            return niw.isEmpty() ? universeTransaction.emptyState() : new State(universeTransaction, niw);
        } else {
            return new State(universeTransaction, map.put(object, post));
        }
    }

    @SuppressWarnings("unchecked")
    public State merge(StateMergeHandler changeHandler, State[] branches, int length) {
        DefaultMap<Object, DefaultMap<Setable, Object>>[] maps = new DefaultMap[length];
        for (int i = 0; i < length; i++) {
            maps[i] = branches[i].map;
        }
        DefaultMap<Object, DefaultMap<Setable, Object>> niw = map.merge((o, ps, pss, pl) -> {
            DefaultMap<Setable, Object> props = ps.merge((p, v, vs, vl) -> {
                Object r = v;
                if (v instanceof Mergeable) {
                    r = ((Mergeable) v).merge(vs, (int) vl);
                } else {
                    for (int i = 0; i < vl; i++) {
                        if (vs[i] != null && !vs[i].equals(v)) {
                            if (!Objects.equals(r, v)) {
                                if (changeHandler != null) {
                                    changeHandler.handleMergeConflict(o, p, v, vs);
                                } else {
                                    throw new NotMergeableException(o + "." + p + "= " + v + " -> " + StringUtil.toString(vs));
                                }
                            } else {
                                r = vs[i];
                            }
                        }
                    }
                }
                return r;
            }, pss, pl);
            if (changeHandler != null) {
                for (Entry<Setable, Object> p : props) {
                    if (p != ps.getEntry(p.getKey())) {
                        deduplicate(p);
                        changeHandler.handleChange(o, ps, p, pss);
                    }
                }
            }
            return props;
        }, maps, maps.length);
        return niw.isEmpty() ? universeTransaction.emptyState() : new State(universeTransaction, niw);

    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <K, V> boolean deduplicate(Entry<K, V> e) {
        return e.setValueIfEqual((V) INTERNAL.object(e.getValue()));
    }

    @Override
    public String toString() {
        return "State" + "[" + universeTransaction.getClass().getSimpleName() + getProperties(universeTransaction.universe()).toString() + "]";
    }

    public String asString() {
        return asString(ALL_OBJECTS, ALL_SETTABLES);
    }

    public String asString(Predicate<Object> objectFilter, Predicate<Setable> setableFilter) {
        return get(() -> {
            String stateRender = filter(objectFilter, setableFilter).sorted(COMPARATOR).reduce("", (s1, e1) -> {
                String keyRender = StringUtil.toString(e1.getKey());
                String valueRender = e1.getValue().sorted(COMPARATOR).reduce("", (s2, e2) -> {
                    String subKeyRender = StringUtil.toString(e2.getKey());
                    String subValueRender = e2.getValue() instanceof State ? "State{...}" : StringUtil.toString(e2.getValue());
                    return String.format("%s\n        %-60s = %s", s2, subKeyRender, subValueRender);
                }, CONCAT);
                return s1 + "\n" + "    " + keyRender + " {" + valueRender + "\n" + "    }";
            }, //
                    CONCAT);
            return "State{" + stateRender + "\n" + "}";
        });
    }

    public Map<Setable, Integer> count() {
        return get(() -> map.toValues().flatMap(m -> m).reduce(Map.of(), (m, e) -> {
            Integer cnt = m.get(e.getKey());
            return m.put(e.getKey(), cnt == null ? 1 : cnt + 1);
        }, (a, b) -> a.addAll(b, Integer::sum)));
    }

    public <R> R get(Supplier<R> supplier) {
        ReadOnlyTransaction tx = universeTransaction.runOnState.openTransaction(universeTransaction);
        try {
            return tx.get(supplier, this);
        } finally {
            universeTransaction.runOnState.closeTransaction(tx);
        }
    }

    public void run(Runnable action) {
        ReadOnlyTransaction tx = universeTransaction.runOnState.openTransaction(universeTransaction);
        try {
            tx.run(action, this);
        } finally {
            universeTransaction.runOnState.closeTransaction(tx);
        }
    }

    public <R> R derive(Supplier<R> supplier) {
        DerivationTransaction tx = universeTransaction.derivation.openTransaction(universeTransaction);
        try {
            return tx.derive(supplier, this, new ConstantState(universeTransaction::handleException));
        } finally {
            universeTransaction.derivation.closeTransaction(tx);
        }
    }

    public <T> Collection<T> getObjects(Class<T> filter) {
        return map.toKeys().filter(filter);
    }

    public Collection<?> getObjects() {
        return getObjects(Object.class);
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public Collection<Entry<Object, Collection<Entry<Setable, Object>>>> filter(Predicate<Object> objectFilter, Predicate<Setable> setableFilter) {
        return map.filter(e1 -> objectFilter.test(e1.getKey())).map(e1 -> Entry.of(e1.getKey(), e1.getValue().filter(e2 -> setableFilter.test(e2.getKey()))));
    }

    public Collection<Entry<Object, Map<Setable, Pair<Object, Object>>>> diff(State other) {
        return diff(other, ALL_OBJECTS, ALL_SETTABLES);
    }

    public Collection<Entry<Object, Map<Setable, Pair<Object, Object>>>> diff(State other, Predicate<Object> objectFilter, Predicate<Setable> setableFilter) {
        return map.diff(other.map).filter(d1 -> objectFilter.test(d1.getKey())).map(d2 -> {
            DefaultMap<Setable, Object> map2 = d2.getValue().a();
            Map<Setable, Pair<Object, Object>> diff = map2.diff(d2.getValue().b()).filter(d3 -> setableFilter.test(d3.getKey())).toMap(e -> e);
            return diff.isEmpty() ? null : Entry.of(d2.getKey(), diff);
        }).notNull();
    }

    public Collection<Entry<Object, Pair<DefaultMap<Setable, Object>, DefaultMap<Setable, Object>>>> diff(State other, Predicate<Object> objectFilter) {
        return map.diff(other.map).filter(d1 -> objectFilter.test(d1.getKey()));
    }

    public String diffString(State other) {
        return diffString(diff(other));
    }

    public String diffString(State other, Predicate<Object> objectFilter, Predicate<Setable> setableFilter) {
        return diffString(diff(other, objectFilter, setableFilter));
    }

    public String diffString(Collection<Entry<Object, Map<Setable, Pair<Object, Object>>>> diff) {
        return get(() -> diff.reduce("", (s1, e1) -> s1 + "\n  " + StringUtil.toString(e1.getKey()) + " {" + e1.getValue().reduce("", (s2, e2) -> s2 + "\n      " + //
                StringUtil.toString(e2.getKey()) + " =" + valueDiffString(e2.getValue().a(), e2.getValue().b()), CONCAT) + "}", CONCAT));
    }

    public String shortDiffString(Collection<Entry<Object, Map<Setable, Pair<Object, Object>>>> diff, Object self) {
        return get(() -> diff.reduce("", (s1, e1) -> (s1.isEmpty() ? "" : s1 + ",") + (self.equals(e1.getKey()) ? "" : StringUtil.toString(e1.getKey()) + "{") + //
                e1.getValue().reduce("", (s2, e2) -> (s2.isEmpty() ? "" : s2 + ",") + StringUtil.toString(e2.getKey()) + "=" + //
                        shortValueDiffString(e2.getValue().a(), e2.getValue().b()), CONCAT_COMMA) + (self.equals(e1.getKey()) ? "" : "}"), CONCAT_COMMA));
    }

    @SuppressWarnings("unchecked")
    private static String valueDiffString(Object a, Object b) {
        if (a instanceof Set && b instanceof Set) {
            return "\n          <+ " + ((Set) a).removeAll((Set) b) + "\n          +> " + ((Set) b).removeAll((Set) a);
        } else {
            return "\n          <- " + StringUtil.toString(a) + "\n          -> " + StringUtil.toString(b);
        }
    }

    @SuppressWarnings("unchecked")
    private static String shortValueDiffString(Object a, Object b) {
        if (a instanceof Set && b instanceof Set) {
            Set removed = ((Set) a).removeAll((Set) b);
            Set added = ((Set) b).removeAll((Set) a);
            return (removed.isEmpty() ? "" : "-" + removed) + (added.isEmpty() ? "" : "+" + added);
        } else {
            return StringUtil.toString(a) + "->" + StringUtil.toString(b);
        }
    }

    public void forEach(TriConsumer<Object, Setable, Object> consumer) {
        map.forEachOrdered(e0 -> e0.getValue().forEachOrdered(e1 -> consumer.accept(e0.getKey(), e1.getKey(), e1.getValue())));
    }

    @Override
    public int hashCode() {
        return universeTransaction.universe().hashCode() + map.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof State)) {
            return false;
        } else if (!universeTransaction.universe().equals(((State) obj).universeTransaction.universe())) {
            return false;
        } else {
            return Objects.equals(map, ((State) obj).map);
        }
    }

    public UniverseTransaction universeTransaction() {
        return universeTransaction;
    }

}
