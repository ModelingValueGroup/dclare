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

    public static final class CircularLogicException extends RuntimeException {
        private static final long                 serialVersionUID = 293433487448006753L;

        @SuppressWarnings("rawtypes")
        private final List<Pair<Functor, Struct>> derived;
        @SuppressWarnings("rawtypes")
        private final Pair<Functor, Struct>       current;

        @SuppressWarnings("rawtypes")
        private CircularLogicException(List<Pair<Functor, Struct>> derived, Pair<Functor, Struct> current) {
            this.derived = derived;
            this.current = current;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public String getMessage() {
            int i = derived.firstIndexOf(current);
            List<Pair<Functor, Struct>> cycle = derived.sublist(0, i + 1).prepend(current);
            return "Cycle " + cycle.reverse().asList().toString().substring(4);
        }
    }

    public static final class Relation extends StructImpl {
        private static final long serialVersionUID = -3477936037166526320L;

        public Relation(Object... data) {
            super(data);
        }
    }

    public static abstract class Functor<S extends Struct> {
        private final Constant<S, Set<S>> extend;

        protected Functor(Object id) {
            extend = Constant.<S, Set<S>> of(Single.of(id), Set.of(), CoreSetableModifier.durable);
        }

        @SuppressWarnings("rawtypes")
        protected final Function<S, Boolean> derive(Function<S, Boolean> f) {
            return o -> {
                Pair<Functor, Struct> slot = Pair.of(this, o);
                List<Pair<Functor, Struct>> pre = DERIVED.get();
                if (pre.contains(slot)) {
                    throw new CircularLogicException(pre, slot);
                } else {
                    return DERIVED.get(pre.prepend(slot), () -> f.apply(o));
                }
            };
        }

        protected final S bind(Object... in) {
            Map<Object, Object> vars = VARIABLES.get();
            Object[] pat = in.clone();
            Object[] out = in.clone();
            Set<Object> empty = Set.of();
            for (int i = 0; i < in.length; i++) {
                Object vin = in[i];
                if (vars.containsKey(vin)) {
                    Object vout = vars.get(vin);
                    pat[i] = vout;
                    if (vout == null) {
                        empty = empty.add(vin);
                    } else {
                        out[i] = vout;
                    }
                }
            }
            if (!empty.isEmpty()) {
                Set<S> set = extend.get(struct(pat));
                if (!set.isEmpty()) {
                    throw new UnboundVariableException(empty, set, in);
                }
            }
            return struct(out);
        }

        @SuppressWarnings("rawtypes")
        protected final void extend(S in, Boolean res, boolean der) {
            if (res && !der) {
                extend(0, in, in.toArray());
            }
        }

        private void extend(int i, S in, Object[] array) {
            if (i < array.length) {
                array = array.clone();
                if (array[i] == null) {
                    array[i] = in.get(i);
                    extend.force(struct(array), Set::add, in);
                }
                extend(i + 1, in, array);
                if (array[i] != null) {
                    array[i] = null;
                    extend.force(struct(array), Set::add, in);
                }
                extend(i + 1, in, array);
            }
        }

        protected abstract S struct(Object[] array);
    };

    private static boolean run(Map<Object, Object> vars, Supplier<Boolean> predicate) {
        return VARIABLES.get(VARIABLES.get().putAll(vars), predicate);
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

    public static final class Rel1<O> extends Functor<Single<O>> {
        private final Constant<Single<O>, Boolean> constant;
        private Set<Predicate<O>>                  rules   = Set.of();
        private Function<Single<O>, Boolean>       deriver = null;

        private Rel1(Object id) {
            super(id);
            constant = Constant.<Single<O>, Boolean> of(id, false, //
                    (tx, o, b, t) -> extend(o, t, deriver != null), CoreSetableModifier.durable);
        }

        public void rule(O v1, Supplier<Boolean> p) {
            rules = rules.add(o -> run(Map.of(Entry.of(v1, o)), p));
            deriver = s -> any(rules.map(r -> () -> r.test(s.a()))).get();
        }

        @SuppressWarnings("unchecked")
        public Supplier<Boolean> is(O o) {
            return () -> constant.get(bind(o), deriver);
        }

        public void fact(O o) {
            constant.set(Single.of(o), true);
        }

        @Override
        public String toString() {
            return constant.toString();
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Single<O> struct(Object[] array) {
            return Single.of((O) array[0]);
        }
    }

    public static final class Rel2<O1, O2> extends Functor<Pair<O1, O2>> {
        private final Constant<Pair<O1, O2>, Boolean> constant;
        private Set<BiPredicate<O1, O2>>              rules   = Set.of();
        private Function<Pair<O1, O2>, Boolean>       deriver = null;

        private Rel2(Object id) {
            super(id);
            constant = Constant.<Pair<O1, O2>, Boolean> of(id, false, //
                    (tx, o, b, t) -> extend(o, t, deriver != null), CoreSetableModifier.durable);
        }

        public void rule(O1 v1, O2 v2, Supplier<Boolean> p) {
            rules = rules.add((o1, o2) -> run(Map.of(Entry.of(v1, o1), Entry.of(v2, o2)), p));
            deriver = s -> any(rules.map(r -> () -> r.test(s.a(), s.b()))).get();
        }

        @SuppressWarnings("unchecked")
        public Supplier<Boolean> is(O1 o1, O2 o2) {
            return () -> constant.get(bind(o1, o2), deriver);
        }

        public void fact(O1 o1, O2 o2) {
            constant.set(Pair.of(o1, o2), true);
        }

        @Override
        public String toString() {
            return constant.toString();
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Pair<O1, O2> struct(Object[] array) {
            return Pair.of((O1) array[0], (O2) array[1]);
        }
    }

    public static final class Rel3<O1, O2, O3> extends Functor<Triple<O1, O2, O3>> {
        private final Constant<Triple<O1, O2, O3>, Boolean> constant;
        private Set<TriPredicate<O1, O2, O3>>               rules   = Set.of();
        private Function<Triple<O1, O2, O3>, Boolean>       deriver = null;

        private Rel3(Object id) {
            super(id);
            constant = Constant.<Triple<O1, O2, O3>, Boolean> of(id, false, //
                    (tx, o, b, t) -> extend(o, t, deriver != null), CoreSetableModifier.durable);
        }

        public void rule(O1 v1, O2 v2, O3 v3, Supplier<Boolean> p) {
            rules = rules.add((o1, o2, o3) -> run(Map.of(Entry.of(v1, o1), Entry.of(v2, o2), Entry.of(v3, o3)), p));
            deriver = s -> any(rules.map(r -> () -> r.test(s.a(), s.b(), s.c()))).get();
        }

        @SuppressWarnings("unchecked")
        public Supplier<Boolean> is(O1 o1, O2 o2, O3 o3) {
            return () -> constant.get(bind(o1, o2, o3), deriver);
        }

        public void fact(O1 o1, O2 o2, O3 o3) {
            constant.set(Triple.of(o1, o2, o3), true);
        }

        @Override
        public String toString() {
            return constant.toString();
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Triple<O1, O2, O3> struct(Object[] array) {
            return Triple.of((O1) array[0], (O2) array[1], (O3) array[2]);
        }
    }

    public static final class Rel4<O1, O2, O3, O4> extends Functor<Quadruple<O1, O2, O3, O4>> {
        private final Constant<Quadruple<O1, O2, O3, O4>, Boolean> constant;
        private Set<QuadPredicate<O1, O2, O3, O4>>                 rules   = Set.of();
        private Function<Quadruple<O1, O2, O3, O4>, Boolean>       deriver = null;

        private Rel4(Object id) {
            super(id);
            constant = Constant.<Quadruple<O1, O2, O3, O4>, Boolean> of(id, false, //
                    (tx, o, b, t) -> extend(o, t, deriver != null), CoreSetableModifier.durable);
        }

        public void rule(O1 v1, O2 v2, O3 v3, O4 v4, Supplier<Boolean> p) {
            rules = rules.add((o1, o2, o3, o4) -> run(Map.of(Entry.of(v1, o1), Entry.of(v2, o2), Entry.of(v3, o3), Entry.of(v4, o4)), p));
            deriver = s -> any(rules.map(r -> () -> r.test(s.a(), s.b(), s.c(), s.d()))).get();
        }

        @SuppressWarnings("unchecked")
        public Supplier<Boolean> is(O1 o1, O2 o2, O3 o3, O4 o4) {
            return () -> constant.get(bind(o1, o2, o3, o4), deriver);
        }

        public void fact(O1 o1, O2 o2, O3 o3, O4 o4) {
            constant.set(Quadruple.of(o1, o2, o3, o4), true);
        }

        @Override
        public String toString() {
            return constant.toString();
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
            } catch (UnboundVariableException uve) {
                ref.updateAndGet(rte -> rte == null || rte instanceof CircularLogicException ? uve : rte);
                return true;
            } catch (CircularLogicException cle) {
                ref.updateAndGet(rte -> rte == null ? cle : rte);
                return false;
            }
        });
        RuntimeException exc = ref.get();
        if (exc instanceof UnboundVariableException) {
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
            } catch (UnboundVariableException uve) {
                ref.updateAndGet(rte -> rte == null || rte instanceof CircularLogicException ? uve : rte);
                return false;
            } catch (CircularLogicException cle) {
                ref.updateAndGet(rte -> rte == null ? cle : rte);
                return true;
            }
        });
        RuntimeException exc = ref.get();
        if (exc instanceof UnboundVariableException) {
            throw exc;
        } else if (result && exc != null) {
            throw exc;
        } else {
            return result;
        }
    }

    // Unification

    private static final Context<Map<Object, Object>> VARIABLES = Context.of(Map.of());

    public static final class UnboundVariableException extends RuntimeException {
        private static final long           serialVersionUID = 4505117271488648346L;

        private final Set<Object>           empty;
        private final Set<? extends Struct> set;
        private final Object[]              in;

        private UnboundVariableException(Set<Object> empty, Set<? extends Struct> set, Object[] in) {
            this.empty = empty;
            this.set = set;
            this.in = in;
        }

        private Set<Map<Object, Object>> bindings(Map<Object, Object> vars) {
            return set.map(s -> {
                Map<Object, Object> vs = vars;
                for (int i = 0; i < s.length(); i++) {
                    Object vin = in[i];
                    if (empty.contains(vin)) {
                        vs = vs.put(vin, s.get(i));
                    }
                }
                return vs;
            }).asSet();
        }
    }

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
        } catch (UnboundVariableException uve) {
            if (uve.empty.anyMatch(vars::containsKey)) {
                return any(uve.bindings(vars).map(vs -> () -> doUni(vs, predicate))).get();
            } else {
                throw uve;
            }
        }
    }

}
