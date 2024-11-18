//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2024 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
//                                                                                                                       ~
//  Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in       ~
//  compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0   ~
//  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on  ~
//  an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the   ~
//  specific language governing permissions and limitations under the License.                                           ~
//                                                                                                                       ~
//  Maintainers:                                                                                                         ~
//      Wim Bast, Tom Brus                                                                                               ~
//                                                                                                                       ~
//  Contributors:                                                                                                        ~
//      Ronald Krijgsheld ✝, Arjan Kok, Carel Bast                                                                       ~
// --------------------------------------------------------------------------------------------------------------------- ~
//  In Memory of Ronald Krijgsheld, 1972 - 2023                                                                          ~
//      Ronald was suddenly and unexpectedly taken from us. He was not only our long-term colleague and team member      ~
//      but also our friend. "He will live on in many of the lines of code you see below."                               ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.dclare;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.struct.Struct;
import org.modelingvalue.collections.struct.impl.StructImpl;
import org.modelingvalue.collections.util.Context;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.collections.util.QuadPredicate;
import org.modelingvalue.collections.util.Quadruple;
import org.modelingvalue.collections.util.Single;
import org.modelingvalue.collections.util.TriPredicate;
import org.modelingvalue.collections.util.Triple;

public final class Logic {
    private Logic() {
    }

    @SuppressWarnings("rawtypes")
    private static final Context<List<Pair<Functor, Struct>>> DERIVED = Context.of(List.of());

    public static final class Relation extends StructImpl {
        private static final long serialVersionUID = -3477936037166526320L;

        public Relation(Object... data) {
            super(data);
        }
    }

    public static abstract class Functor<S extends Struct> {
        private final Constant<S, Set<S>> extend;

        protected Functor(Object id) {
            extend = Constant.<S, Set<S>> of(id, Set.of(), CoreSetableModifier.durable);
        }

        @Override
        public String toString() {
            return extend.toString();
        }

        @SuppressWarnings("rawtypes")
        protected final Boolean get(Function<S, Boolean> deriver, Object... in) {
            Set<Object> empty = Set.of();
            Map<Object, Object> vars = VARIABLES.get();
            Object[] out = in.clone();
            for (int i = 0; i < in.length; i++) {
                while (vars.containsKey(in[i])) {
                    Object v = vars.get(in[i]);
                    if (v == null) {
                        empty = empty.add(in[i]);
                        if (deriver == null) {
                            out[i] = null;
                        }
                        break;
                    } else {
                        out[i] = v;
                        in[i] = v;
                    }
                }
            }
            S s = struct(out);
            Set<S> set = deriver == null || empty.isEmpty() ? extend.get(s) : Set.of();
            if (set.isEmpty()) {
                if (deriver != null) {
                    Pair<Functor, Struct> slot = Pair.of(this, s);
                    List<Pair<Functor, Struct>> pre = DERIVED.get();
                    if (pre.contains(slot)) {
                        throw new CircularLogicException(pre, slot);
                    } else {
                        Boolean result = DERIVED.get(pre.prepend(slot), () -> deriver.apply(s));
                        if (result) {
                            extend.force(s, Set::add, s);
                        }
                        return result;
                    }
                } else {
                    return Boolean.FALSE;
                }
            } else if (!empty.isEmpty()) {
                Set<Object> em = empty;
                throw new BindingsFoundException(set.map(b -> {
                    Map<Object, Object> vs = Map.of();
                    for (int i = 0; i < b.length(); i++) {
                        Object vin = in[i];
                        if (em.contains(vin)) {
                            vs = vs.put(vin, b.get(i));
                        }
                    }
                    return vs;
                }).asSet());
            } else {
                return Boolean.TRUE;
            }
        }

        @SuppressWarnings("rawtypes")
        protected final void set(S in) {
            extend.force(in, Set::add, in);
            set(0, in, in.toArray());
        }

        private void set(int i, S in, Object[] array) {
            if (i < array.length) {
                array = array.clone();
                if (array[i] == null) {
                    array[i] = in.get(i);
                    extend.force(struct(array), Set::add, in);
                }
                set(i + 1, in, array);
                if (array[i] != null) {
                    array[i] = null;
                    extend.force(struct(array), Set::add, in);
                }
                set(i + 1, in, array);
            }
        }

        protected abstract S struct(Object[] array);
    };

    @SuppressWarnings("rawtypes")
    private static boolean run(Map<Object, Object> vars, Supplier<Boolean> predicate) {
        Map<Object, Object> pre = VARIABLES.get();
        Set<Object> cycle = vars.filter(kv -> kv.getValue() == null).map(Entry::getKey).filter(k -> pre.containsKey(k) && pre.get(k) == null).asSet();
        if (cycle.isEmpty()) {
            return VARIABLES.get(pre.putAll(vars), predicate);
        } else {
            throw new CircularVariableException(cycle);
        }
    }

    // Relations

    public static final <O> Rel1<O> rel1(Object id) {
        return new Rel1<O>(id);
    }

    public static final <O1, O2> Rel2<O1, O2> rel2(Object id) {
        return new Rel2<O1, O2>(id);
    }

    public static final <O1, O2, O3> Rel3<O1, O2, O3> rel3(Object id) {
        return new Rel3<O1, O2, O3>(id);
    }

    public static final <O1, O2, O3, O4> Rel4<O1, O2, O3, O4> rel4(Object id) {
        return new Rel4<O1, O2, O3, O4>(id);
    }

    public static final class Rel1<O1> extends Functor<Single<O1>> {
        private Set<Predicate<O1>>            rules   = Set.of();
        private Function<Single<O1>, Boolean> deriver = null;

        private Rel1(Object id) {
            super(id);
        }

        public void rule(O1 v1, Supplier<Boolean> p) {
            rules = rules.add(o1 -> run(Map.of(Entry.of(v1, o1)), p));
            deriver = s -> any(rules.map(r -> () -> r.test(s.a()))).get();
        }

        @SuppressWarnings("unchecked")
        public Supplier<Boolean> is(O1 o1) {
            return () -> get(deriver, o1);
        }

        public void fact(O1 o1) {
            set(Single.of(o1));
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Single<O1> struct(Object[] array) {
            return Single.of((O1) array[0]);
        }
    }

    public static final class Rel2<O1, O2> extends Functor<Pair<O1, O2>> {
        private Set<BiPredicate<O1, O2>>        rules   = Set.of();
        private Function<Pair<O1, O2>, Boolean> deriver = null;

        private Rel2(Object id) {
            super(id);
        }

        public void rule(O1 v1, O2 v2, Supplier<Boolean> p) {
            rules = rules.add((o1, o2) -> run(Map.of(Entry.of(v1, o1), Entry.of(v2, o2)), p));
            deriver = s -> any(rules.map(r -> () -> r.test(s.a(), s.b()))).get();
        }

        @SuppressWarnings("unchecked")
        public Supplier<Boolean> is(O1 o1, O2 o2) {
            return () -> get(deriver, o1, o2);
        }

        public void fact(O1 o1, O2 o2) {
            set(Pair.of(o1, o2));
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Pair<O1, O2> struct(Object[] array) {
            return Pair.of((O1) array[0], (O2) array[1]);
        }
    }

    public static final class Rel3<O1, O2, O3> extends Functor<Triple<O1, O2, O3>> {
        private Set<TriPredicate<O1, O2, O3>>         rules   = Set.of();
        private Function<Triple<O1, O2, O3>, Boolean> deriver = null;

        private Rel3(Object id) {
            super(id);
        }

        public void rule(O1 v1, O2 v2, O3 v3, Supplier<Boolean> p) {
            rules = rules.add((o1, o2, o3) -> run(Map.of(Entry.of(v1, o1), Entry.of(v2, o2), Entry.of(v3, o3)), p));
            deriver = s -> any(rules.map(r -> () -> r.test(s.a(), s.b(), s.c()))).get();
        }

        @SuppressWarnings("unchecked")
        public Supplier<Boolean> is(O1 o1, O2 o2, O3 o3) {
            return () -> get(deriver, o1, o2, o3);
        }

        public void fact(O1 o1, O2 o2, O3 o3) {
            set(Triple.of(o1, o2, o3));
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Triple<O1, O2, O3> struct(Object[] array) {
            return Triple.of((O1) array[0], (O2) array[1], (O3) array[2]);
        }
    }

    public static final class Rel4<O1, O2, O3, O4> extends Functor<Quadruple<O1, O2, O3, O4>> {
        private Set<QuadPredicate<O1, O2, O3, O4>>           rules   = Set.of();
        private Function<Quadruple<O1, O2, O3, O4>, Boolean> deriver = null;

        private Rel4(Object id) {
            super(id);
        }

        public void rule(O1 v1, O2 v2, O3 v3, O4 v4, Supplier<Boolean> p) {
            rules = rules.add((o1, o2, o3, o4) -> run(Map.of(Entry.of(v1, o1), Entry.of(v2, o2), Entry.of(v3, o3), Entry.of(v4, o4)), p));
            deriver = s -> any(rules.map(r -> () -> r.test(s.a(), s.b(), s.c(), s.d()))).get();
        }

        @SuppressWarnings("unchecked")
        public Supplier<Boolean> is(O1 o1, O2 o2, O3 o3, O4 o4) {
            return () -> get(deriver, o1, o2, o3, o4);
        }

        public void fact(O1 o1, O2 o2, O3 o3, O4 o4) {
            set(Quadruple.of(o1, o2, o3, o4));
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Quadruple<O1, O2, O3, O4> struct(Object[] array) {
            return Quadruple.of((O1) array[0], (O2) array[1], (O3) array[2], (O4) array[3]);
        }
    }

    // Not

    public static final Supplier<Boolean> not(Supplier<Boolean> predicate) {
        return () -> !predicate.get();
    }

    // Or

    @SafeVarargs
    public static final Supplier<Boolean> or(Supplier<Boolean>... predicates) {
        List<Supplier<Boolean>> list = List.of(predicates);
        return list.isEmpty() ? () -> false : list.size() == 1 ? list.get(0) : () -> or(list);
    }

    public static final Supplier<Boolean> any(Collection<Supplier<Boolean>> predicates) {
        List<Supplier<Boolean>> list = predicates.asList();
        return list.isEmpty() ? () -> false : list.size() == 1 ? list.get(0) : () -> or(list);
    }

    private static boolean or(List<Supplier<Boolean>> predicates) {
        List<Supplier<Boolean>> or = predicates.random().asList();
        AtomicReference<RuntimeException> ref = new AtomicReference<>(null);
        boolean result = or.anyMatch(p -> {
            try {
                return p.get();
            } catch (BindingsFoundException bve) {
                ref.updateAndGet(rte -> {
                    if (rte instanceof BindingsFoundException) {
                        return ((BindingsFoundException) rte).merge(bve);
                    } else {
                        return rte == null || rte instanceof CycleException ? bve : rte;
                    }
                });
                return false;
            } catch (CycleException ce) {
                ref.updateAndGet(rte -> rte == null ? ce : rte);
                return false;
            }
        });
        RuntimeException exc = ref.get();
        if (exc instanceof BindingsFoundException) {
            throw exc;
        } else if (!result && exc != null) {
            throw exc;
        } else {
            return result;
        }
    }

    // And

    @SafeVarargs
    public static final Supplier<Boolean> and(Supplier<Boolean>... predicates) {
        List<Supplier<Boolean>> list = List.of(predicates);
        return list.isEmpty() ? () -> true : list.size() == 1 ? list.get(0) : () -> and(list);
    }

    public static final Supplier<Boolean> all(Collection<Supplier<Boolean>> predicates) {
        List<Supplier<Boolean>> list = predicates.asList();
        return list.isEmpty() ? () -> true : list.size() == 1 ? list.get(0) : () -> and(list);
    }

    private static boolean and(List<Supplier<Boolean>> predicates) {
        List<Supplier<Boolean>> and = predicates.random().asList();
        AtomicReference<RuntimeException> ref = new AtomicReference<>(null);
        boolean result = and.allMatch(p -> {
            try {
                return p.get();
            } catch (BindingsFoundException bve) {
                ref.updateAndGet(rte -> {
                    if (rte instanceof BindingsFoundException) {
                        return ((BindingsFoundException) rte).merge(bve);
                    } else {
                        return rte == null || rte instanceof CycleException ? bve : rte;
                    }
                });
                return true;
            } catch (CycleException ce) {
                ref.updateAndGet(rte -> rte == null ? ce : rte);
                return true;
            }
        });
        RuntimeException exc = ref.get();
        if (exc instanceof BindingsFoundException) {
            throw exc;
        } else if (result && exc != null) {
            throw exc;
        } else {
            return result;
        }
    }

    // Unification

    private static final Context<Map<Object, Object>> VARIABLES = Context.of(Map.of());

    @SuppressWarnings("unchecked")
    public static final <V1> Supplier<Boolean> uni(V1 v1, Supplier<Boolean> predicate) {
        Map<Object, Object> vars = Map.of(Entry.of(v1, null));
        return () -> doUni(vars, predicate);
    }

    @SuppressWarnings("unchecked")
    public static final <V1, V2> Supplier<Boolean> uni(V1 v1, V2 v2, Supplier<Boolean> predicate) {
        Map<Object, Object> vars = Map.of(Entry.of(v1, null), Entry.of(v2, null));
        return () -> doUni(vars, predicate);
    }

    @SuppressWarnings("unchecked")
    public static final <V1, V2, V3> Supplier<Boolean> uni(V1 v1, V2 v2, V3 v3, Supplier<Boolean> predicate) {
        Map<Object, Object> vars = Map.of(Entry.of(v1, null), Entry.of(v2, null), Entry.of(v3, null));
        return () -> doUni(vars, predicate);
    }

    @SuppressWarnings("unchecked")
    public static final <V1, V2, V3, V4> Supplier<Boolean> uni(V1 v1, V2 v2, V3 v3, V4 v4, Supplier<Boolean> predicate) {
        Map<Object, Object> vars = Map.of(Entry.of(v1, null), Entry.of(v2, null), Entry.of(v3, null), Entry.of(v4, null));
        return () -> doUni(vars, predicate);
    }

    private static boolean doUni(Map<Object, Object> vars, Supplier<Boolean> predicate) {
        try {
            return run(vars, predicate);
        } catch (BindingsFoundException bve) {
            return any(bve.bindings.map(vs -> () -> doUni(vars.putAll(vs), predicate))).get();
        }
    }

    // Runtime Exceptions

    public static final class BindingsFoundException extends RuntimeException {
        private static final long              serialVersionUID = 4505117271488648346L;

        private final Set<Map<Object, Object>> bindings;

        private BindingsFoundException(Set<Map<Object, Object>> bindings) {
            this.bindings = bindings;
        }

        private BindingsFoundException merge(BindingsFoundException other) {
            return new BindingsFoundException(bindings.addAll(other.bindings));
        }
    }

    private static abstract class CycleException extends RuntimeException {
        private static final long serialVersionUID = -1561492438458671302L;
    }

    public static final class CircularVariableException extends CycleException {
        private static final long serialVersionUID = 1636584651815799600L;

        private final Set<Object> vars;

        private CircularVariableException(Set<Object> vars) {
            this.vars = vars;
        }

        @Override
        public String getMessage() {
            return "Circular Variables " + vars.toString().substring(3);
        }
    }

    @SuppressWarnings("rawtypes")
    public static final class CircularLogicException extends CycleException {
        private static final long                 serialVersionUID = 293433487448006753L;

        private final List<Pair<Functor, Struct>> derived;
        private final Pair<Functor, Struct>       current;

        private CircularLogicException(List<Pair<Functor, Struct>> derived, Pair<Functor, Struct> current) {
            this.derived = derived;
            this.current = current;
        }

        @Override
        public String getMessage() {
            int i = derived.firstIndexOf(current);
            List<Pair<Functor, Struct>> cycle = derived.sublist(0, i + 1).prepend(current);
            return "Circular Logic " + cycle.reverse().asList().toString().substring(4);
        }
    }

}
