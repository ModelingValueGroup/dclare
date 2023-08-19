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

import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Mergeable;
import org.modelingvalue.collections.util.NotMergeableException;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.collections.util.StringUtil;
import org.modelingvalue.dclare.Priority.Queued;

@SuppressWarnings({"rawtypes", "unused"})
public class State extends StateMap implements IState, Serializable {
    public static final BinaryOperator<String> CONCAT           = (a, b) -> a + b;
    public static final BinaryOperator<String> CONCAT_COMMA     = (a, b) -> a.isEmpty() || b.isEmpty() ? a + b : a + "," + b;
    private static final Comparator<Entry>     COMPARATOR       = Comparator.comparing(a -> StringUtil.toString(a.getKey()));

    private static final long                  serialVersionUID = -3468784705870374732L;

    private final UniverseTransaction          universeTransaction;
    private final Queued<Action<?>>[]          actions;
    private final Queued<Mutable>[]            children;

    @SuppressWarnings("unchecked")
    protected State(UniverseTransaction universeTransaction, StateMap stateMap) {
        super(stateMap);
        this.universeTransaction = universeTransaction;
        actions = new Queued[Priority.ALL.length];
        children = new Queued[Priority.ALL.length];
        for (int i = 0; i < Priority.ALL.length; i++) {
            actions[i] = new Priority.Queued(i, true);
            children[i] = new Priority.Queued(i, false);
        }
    }

    private State(UniverseTransaction universeTransaction, DefaultMap<Object, DefaultMap<Setable, Object>> map, Queued<Action<?>>[] actions, Queued<Mutable>[] children) {
        super(map);
        this.universeTransaction = universeTransaction;
        this.actions = actions;
        this.children = children;
    }

    private State newState(DefaultMap<Object, DefaultMap<Setable, Object>> newMap) {
        return newMap.isEmpty() ? universeTransaction.emptyState() : new State(universeTransaction, newMap, actions, children);
    }

    @Override
    public State state() {
        return this;
    }

    @Override
    public Priority priority(Queued queued) {
        if (queued.actions()) {
            for (int i = 0; i < actions.length; i++) {
                if (actions[i].equals(queued)) {
                    return Priority.ALL[i];
                }
            }
        } else {
            for (int i = 0; i < children.length; i++) {
                if (children[i].equals(queued)) {
                    return Priority.ALL[i];
                }
            }
        }
        return null;
    }

    @Override
    public Queued<Action<?>> actions(Priority prio) {
        return actions[prio.ordinal()];
    }

    @Override
    public Queued<Mutable> children(Priority prio) {
        return children[prio.ordinal()];
    }

    @SuppressWarnings("unchecked")
    public State exchange(Priority prio1, Priority prio2) {
        Queued<Action<?>> a1 = this.actions[prio1.ordinal()];
        Queued<Mutable> c1 = this.children[prio1.ordinal()];
        Queued<Action<?>> a2 = this.actions[prio2.ordinal()];
        Queued<Mutable> c2 = this.children[prio2.ordinal()];
        Queued<Action<?>>[] actions = this.actions.clone();
        Queued<Mutable>[] children = this.children.clone();
        actions[prio1.ordinal()] = a2;
        children[prio1.ordinal()] = c2;
        actions[prio2.ordinal()] = a1;
        children[prio2.ordinal()] = c1;
        return new State(universeTransaction, map(), actions, children);
    }

    public <O, T> State set(O object, Setable<O, T> property, T value) {
        DefaultMap<Setable, Object> props = getProperties(object);
        DefaultMap<Setable, Object> set = setProperties(object, props, property, value);
        return set != props ? set(object, set) : this;
    }

    public <O, T extends A, A> State set(O object, Setable<O, T> property, T value, A[] old) {
        return set(object, property, (pre, post) -> {
            old[0] = pre;
            return post;
        }, value);
    }

    public <O, T, E> State set(O object, Setable<O, T> property, BiFunction<T, E, T> function, E element, T[] oldNew) {
        DefaultMap<Setable, Object> props = getProperties(object);
        oldNew[0] = get(props, property);
        oldNew[1] = function.apply(oldNew[0], element);
        return !Objects.equals(oldNew[0], oldNew[1]) ? set(object, setProperties(object, props, property, oldNew[1])) : this;
    }

    public <O, T, E> State set(O object, Setable<O, T> property, UnaryOperator<T> oper, T[] oldNew) {
        DefaultMap<Setable, Object> props = getProperties(object);
        oldNew[0] = get(props, property);
        oldNew[1] = oper.apply(oldNew[0]);
        return !Objects.equals(oldNew[0], oldNew[1]) ? set(object, setProperties(object, props, property, oldNew[1])) : this;
    }

    public <O, T, E> State set(O object, Setable<O, T> property, BiFunction<T, E, T> function, E element) {
        DefaultMap<Setable, Object> props = getProperties(object);
        T preVal = get(props, property);
        T postVal = function.apply(preVal, element);
        return !Objects.equals(preVal, postVal) ? set(object, setProperties(object, props, property, postVal)) : this;
    }

    public <O, T, E> State set(O object, Setable<O, T> property, UnaryOperator<T> function) {
        DefaultMap<Setable, Object> props = getProperties(object);
        T preVal = get(props, property);
        T postVal = function.apply(preVal);
        return !Objects.equals(preVal, postVal) ? set(object, setProperties(object, props, property, postVal)) : this;
    }

    <O, T> State set(O object, DefaultMap<Setable, Object> post) {
        return newState(post.isEmpty() ? map().removeKey(object) : map().put(object, post));
    }

    @SuppressWarnings("unchecked")
    public State merge(StateMergeHandler changeHandler, State[] branches, int length) {
        DefaultMap<Object, DefaultMap<Setable, Object>>[] maps = new DefaultMap[length];
        for (int i = 0; i < length; i++) {
            maps[i] = branches[i].map();
        }
        return newState(map().merge((o, ps, pss, pl) -> {
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
                        changeHandler.handleChange(o, p.getKey(), ps, pss, props, this);
                    }
                }
            }
            return props;
        }, maps, maps.length));
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
        return get(() -> map().toValues().flatMap(m -> m).reduce(Map.of(), (m, e) -> {
            Integer cnt = m.get(e.getKey());
            return m.put(e.getKey(), cnt == null ? 1 : cnt + 1);
        }, (a, b) -> a.addAll(b, Integer::sum)));
    }

    @Override
    public <R> R get(Supplier<R> supplier) {
        ReadOnlyTransaction tx = universeTransaction.runOnState.openTransaction(universeTransaction);
        try {
            return tx.get(supplier, this);
        } finally {
            universeTransaction.runOnState.closeTransaction(tx);
        }
    }

    @Override
    public void run(Runnable action) {
        ReadOnlyTransaction tx = universeTransaction.runOnState.openTransaction(universeTransaction);
        try {
            tx.run(action, this);
        } finally {
            universeTransaction.runOnState.closeTransaction(tx);
        }
    }

    public <R> R derive(Supplier<R> supplier, ConstantState constantState) {
        DerivationTransaction tx = universeTransaction.derivation.openTransaction(universeTransaction);
        try {
            return tx.derive(supplier, this, constantState);
        } finally {
            universeTransaction.derivation.closeTransaction(tx);
        }
    }

    public <R> R deriveIdentity(Supplier<R> supplier, int depth, Mutable contextMutable, ConstantState constantState) {
        IdentityDerivationTransaction tx = universeTransaction.identityDerivation.openTransaction(universeTransaction);
        try {
            return tx.derive(supplier, this, depth, contextMutable, constantState);
        } finally {
            universeTransaction.identityDerivation.closeTransaction(tx);
        }
    }

    public String diffString(StateMap other) {
        return diffString(diff(other));
    }

    public String diffString(StateMap other, Predicate<Object> objectFilter, Predicate<Setable> setableFilter) {
        return diffString(diff(other, objectFilter, setableFilter));
    }

    public String diffString(Collection<Entry<Object, Map<Setable, Pair<Object, Object>>>> diff) {
        return get(() -> diff.reduce("", (s1, e1) -> {
            String sub = e1.getValue().reduce("", (s2, e2) -> {
                String r = valueDiffString(e2.getValue().a(), e2.getValue().b());
                return s2 + "\n" + //
                        "      " + StringUtil.toString(e2.getKey()) + " =" + r;
            }, CONCAT);
            return s1 + "\n" + //
                    "  " + StringUtil.toString(e1.getKey()) + " {" + sub + "}";
        }, CONCAT));
    }

    public String shortDiffString(Collection<Entry<Object, Map<Setable, Pair<Object, Object>>>> diff, Object self) {
        return get(() -> diff.reduce("", (s1, e1) -> (s1.isEmpty() ? "" : s1 + ",") + (self.equals(e1.getKey()) ? "" : StringUtil.toString(e1.getKey()) + "{") + //
                e1.getValue().reduce("", (s2, e2) -> (s2.isEmpty() ? "" : s2 + ",") + StringUtil.toString(e2.getKey()) + "=" + //
                        shortValueDiffString(e2.getValue().a(), e2.getValue().b()), CONCAT_COMMA) + (self.equals(e1.getKey()) ? "" : "}"), CONCAT_COMMA));
    }

    @SuppressWarnings("unchecked")
    private static String valueDiffString(Object a, Object b) {
        if (a instanceof Set && b instanceof Set) {
            Set sa = (Set) a;
            Set sb = (Set) b;
            return "\n          <+ " + sa.removeAll(sb) + "\n          +> " + sb.removeAll(sa);
        } else if (a instanceof List && b instanceof List) {
            List la = (List) a;
            List lb = (List) b;
            if (la.filter(lb::contains).asList().equals(lb.filter(la::contains).asList())) {
                // same order
                return "\n          <+ " + la.removeAll(lb) + "\n          +> " + lb.removeAll(la);
            } else {
                // reordered
                return "\n          <- " + StringUtil.toString(a) + "\n          -> " + StringUtil.toString(b);
            }
        } else {
            return "\n          <- " + StringUtil.toString(a) + "\n          -> " + StringUtil.toString(b);
        }
    }

    @SuppressWarnings("unchecked")
    public static String shortValueDiffString(Object a, Object b) {
        if (a instanceof Set && b instanceof Set) {
            Set sa = (Set) a;
            Set sb = (Set) b;
            Set removed = sa.removeAll(sb);
            Set added = sb.removeAll(sa);
            return (removed.isEmpty() ? "" : "-" + removed) + (added.isEmpty() ? "" : "+" + added);
        } else if (a instanceof List && b instanceof List) {
            List la = (List) a;
            List lb = (List) b;
            if (la.filter(lb::contains).asList().equals(lb.filter(la::contains).asList())) {
                // same order
                List removed = la.removeAll(lb);
                List added = lb.removeAll(la);
                return (removed.isEmpty() ? "" : "-" + removed) + (added.isEmpty() ? "" : "+" + added);
            } else {
                // reordered
                return StringUtil.toString(a) + "->" + StringUtil.toString(b);
            }
        } else {
            return StringUtil.toString(a) + "->" + StringUtil.toString(b);
        }
    }

    @Override
    public int hashCode() {
        return universeTransaction.universe().hashCode() ^ super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof State)) {
            return false;
        } else {
            State other = (State) obj;
            return Objects.equals(universeTransaction.universe(), other.universeTransaction.universe()) //
                    && Objects.equals(map(), other.map());
        }
    }

    public UniverseTransaction universeTransaction() {
        return universeTransaction;
    }

    @Override
    public TransactionId transactionId() {
        return get(universeTransaction.universe(), Mutable.D_CHANGE_ID);
    }

}
