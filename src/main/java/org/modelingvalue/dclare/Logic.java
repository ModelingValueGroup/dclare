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

import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.struct.Struct;
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

    private static final class CircularLogicException extends RuntimeException {
        private static final long                 serialVersionUID = 293433487448006753L;

        @SuppressWarnings("rawtypes")
        private final List<Pair<Functor, Struct>> derived;

        @SuppressWarnings("rawtypes")
        private CircularLogicException(List<Pair<Functor, Struct>> derived, Pair<Functor, Struct> current) {
            int i = derived.firstIndexOf(current);
            this.derived = derived.sublist(0, i + 1).prepend(current);
        }

        @Override
        public String getMessage() {
            return "Cycle " + derived.reverse().asList().toString().substring(4);
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

        protected final S bind(S in) {
            Map<Object, Object> vars = VARIABLES.get();
            if (!vars.isEmpty()) {
                Object[] array = in.toArray();
                Set<Object> empty = Set.of();
                for (int i = 0; i < in.length(); i++) {
                    Object vin = in.get(i);
                    if (vars.containsKey(vin)) {
                        Object vout = vars.get(vin);
                        array[i] = vout;
                        if (vout == null) {
                            empty = empty.add(vin);
                        }
                    }
                }
                if (!empty.isEmpty()) {
                    Set<S> set = extend.get(copy(array));
                    if (!set.isEmpty()) {
                        Set<Object> em = empty;
                        throw new UnboundVariableException(empty, set.map(s -> {
                            Map<Object, Object> vs = vars;
                            for (int i = 0; i < in.length(); i++) {
                                Object vin = in.get(i);
                                if (em.contains(vin)) {
                                    vs = vs.put(vin, s.get(i));
                                }
                            }
                            return vs;
                        }).asSet());
                    }
                }
            }
            return in;
        }

        protected final void extend(S in) {
            extend(0, in, in.toArray());
        }

        private void extend(int i, S in, Object[] array) {
            if (i < array.length) {
                array = array.clone();
                if (array[i] == null) {
                    array[i] = in.get(i);
                    extend.force(copy(array), Set::add, in);
                }
                extend(i + 1, in, array);
                if (array[i] != null) {
                    array[i] = null;
                    extend.force(copy(array), Set::add, in);
                }
                extend(i + 1, in, array);
            }
        }

        protected abstract S copy(Object[] array);
    };

    // Relations

    public static final <O> Rel1<O> rel1(Object id, //
            java.util.function.Function<O, BooleanSupplier> p) {
        return new Rel1<O>(id, (o) -> p.apply(o).getAsBoolean());
    }

    public static final <O> Rel1<O> rel1(Object id) {
        return new Rel1<O>(id, null);
    }

    public static final <O1, O2> Rel2<O1, O2> rel2(Object id, //
            java.util.function.BiFunction<O1, O2, BooleanSupplier> p) {
        return new Rel2<O1, O2>(id, (o1, o2) -> p.apply(o1, o2).getAsBoolean());
    }

    public static final <O1, O2> Rel2<O1, O2> rel2(Object id) {
        return new Rel2<O1, O2>(id, null);
    }

    public static final <O1, O2, O3> Rel3<O1, O2, O3> rel3(Object id, //
            org.modelingvalue.collections.util.TriFunction<O1, O2, O3, BooleanSupplier> p) {
        return new Rel3<O1, O2, O3>(id, (o1, o2, o3) -> p.apply(o1, o2, o3).getAsBoolean());
    }

    public static final <O1, O2, O3> Rel3<O1, O2, O3> rel3(Object id) {
        return new Rel3<O1, O2, O3>(id, null);
    }

    public static final <O1, O2, O3, O4> Rel4<O1, O2, O3, O4> rel4(Object id, //
            org.modelingvalue.collections.util.QuadFunction<O1, O2, O3, O4, BooleanSupplier> p) {
        return new Rel4<O1, O2, O3, O4>(id, (o1, o2, o3, o4) -> p.apply(o1, o2, o3, o4).getAsBoolean());
    }

    public static final <O1, O2, O3, O4> Rel4<O1, O2, O3, O4> rel4(Object id) {
        return new Rel4<O1, O2, O3, O4>(id, null);
    }

    public static final class Rel1<O> extends Functor<Single<O>> {
        private final Constant<Single<O>, Boolean> constant;

        private Rel1(Object id, Predicate<O> p) {
            super(id);
            constant = Constant.<Single<O>, Boolean> of(id, p != null ? null : false, //
                    p == null ? null : derive(o -> p.test(o.a())), //
                    (tx, o, b, t) -> extend(o), CoreSetableModifier.durable);
        }

        @SuppressWarnings("unchecked")
        public BooleanSupplier is(O o) {
            return () -> constant.get(bind(Single.of(o)));
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
        protected Single<O> copy(Object[] array) {
            return Single.of((O) array[0]);
        }
    }

    public static final class Rel2<O1, O2> extends Functor<Pair<O1, O2>> {
        private final Constant<Pair<O1, O2>, Boolean> constant;

        private Rel2(Object id, BiPredicate<O1, O2> p) {
            super(id);
            constant = Constant.<Pair<O1, O2>, Boolean> of(id, p != null ? null : false, //
                    p == null ? null : derive(o -> p.test(o.a(), o.b())), //
                    (tx, o, b, t) -> extend(o), CoreSetableModifier.durable);
        }

        @SuppressWarnings("unchecked")
        public BooleanSupplier is(O1 o1, O2 o2) {
            return () -> constant.get(bind(Pair.of(o1, o2)));
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
        protected Pair<O1, O2> copy(Object[] array) {
            return Pair.of((O1) array[0], (O2) array[1]);
        }
    }

    public static final class Rel3<O1, O2, O3> extends Functor<Triple<O1, O2, O3>> {
        private final Constant<Triple<O1, O2, O3>, Boolean> constant;

        private Rel3(Object id, TriPredicate<O1, O2, O3> p) {
            super(id);
            constant = Constant.<Triple<O1, O2, O3>, Boolean> of(id, p != null ? null : false, //
                    p == null ? null : derive(o -> p.test(o.a(), o.b(), o.c())), //
                    (tx, o, b, t) -> extend(o), CoreSetableModifier.durable);
        }

        @SuppressWarnings("unchecked")
        public BooleanSupplier is(O1 o1, O2 o2, O3 o3) {
            return () -> constant.get(bind(Triple.of(o1, o2, o3)));
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
        protected Triple<O1, O2, O3> copy(Object[] array) {
            return Triple.of((O1) array[0], (O2) array[1], (O3) array[2]);
        }
    }

    public static final class Rel4<O1, O2, O3, O4> extends Functor<Quadruple<O1, O2, O3, O4>> {
        private final Constant<Quadruple<O1, O2, O3, O4>, Boolean> constant;

        private Rel4(Object id, QuadPredicate<O1, O2, O3, O4> p) {
            super(id);
            constant = Constant.<Quadruple<O1, O2, O3, O4>, Boolean> of(id, p != null ? null : false, //
                    p == null ? null : derive(o -> p.test(o.a(), o.b(), o.c(), o.d())), //
                    (tx, o, b, t) -> extend(o), CoreSetableModifier.durable);
        }

        @SuppressWarnings("unchecked")
        public BooleanSupplier is(O1 o1, O2 o2, O3 o3, O4 o4) {
            return () -> constant.get(bind(Quadruple.of(o1, o2, o3, o4)));
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
        protected Quadruple<O1, O2, O3, O4> copy(Object[] array) {
            return Quadruple.of((O1) array[0], (O2) array[1], (O3) array[2], (O4) array[3]);
        }
    }

    // Not

    public static final BooleanSupplier not(BooleanSupplier predicate) {
        return () -> !predicate.getAsBoolean();
    }

    // Or

    @SafeVarargs
    public static final BooleanSupplier or(BooleanSupplier... predicates) {
        return () -> or(List.of(predicates));
    }

    public static final BooleanSupplier any(Collection<BooleanSupplier> predicates) {
        return () -> or(predicates.asList());
    }

    private static boolean or(List<BooleanSupplier> predicates) {
        if (predicates.isEmpty()) {
            return false;
        } else if (predicates.size() == 1) {
            return predicates.first().getAsBoolean();
        } else {
            List<BooleanSupplier> or = predicates.random().asList();
            RuntimeException[] rte = new RuntimeException[2];
            boolean result = or.anyMatch(p -> {
                try {
                    return p.getAsBoolean();
                } catch (CircularLogicException cle) {
                    rte[0] = cle;
                    return false;
                } catch (UnboundVariableException uve) {
                    rte[1] = uve;
                    return true;
                }
            });
            if (rte[1] != null) {
                throw rte[1];
            } else if (!result && rte[0] != null) {
                throw rte[0];
            } else {
                return result;
            }
        }
    }

    // And

    @SafeVarargs
    public static final BooleanSupplier and(BooleanSupplier... predicates) {
        return () -> and(List.of(predicates));
    }

    public static final BooleanSupplier all(Collection<BooleanSupplier> predicates) {
        return () -> and(predicates.asList());
    }

    private static boolean and(List<BooleanSupplier> predicates) {
        if (predicates.isEmpty()) {
            return true;
        } else if (predicates.size() == 1) {
            return predicates.first().getAsBoolean();
        } else {
            List<BooleanSupplier> and = predicates.random().asList();
            RuntimeException[] rte = new RuntimeException[2];
            boolean result = and.anyMatch(p -> {
                try {
                    return p.getAsBoolean();
                } catch (CircularLogicException cle) {
                    rte[0] = cle;
                    return true;
                } catch (UnboundVariableException uve) {
                    rte[1] = uve;
                    return false;
                }
            });
            if (rte[1] != null) {
                throw rte[1];
            } else if (result && rte[0] != null) {
                throw rte[0];
            } else {
                return result;
            }
        }
    }

    // Unification

    private static final Context<Map<Object, Object>> VARIABLES = Context.of(Map.of());

    private static final class UnboundVariableException extends RuntimeException {
        private static final long              serialVersionUID = 4505117271488648346L;

        private final Set<Object>              vars;
        private final Set<Map<Object, Object>> bindings;

        private UnboundVariableException(Set<Object> vars, Set<Map<Object, Object>> bindings) {
            this.vars = vars;
            this.bindings = bindings;
        }
    }

    @SuppressWarnings("unchecked")
    public static final <V1> BooleanSupplier uni(V1 v1, BooleanSupplier predicate) {
        return () -> doUni(Collection.of(v1).asMap(o -> Entry.of(o, null)), () -> predicate.getAsBoolean());
    }

    @SuppressWarnings("unchecked")
    public static final <V1, V2> BooleanSupplier uni(V1 v1, V2 v2, BooleanSupplier predicate) {
        return () -> doUni(Collection.of(v1, v2).asMap(o -> Entry.of(o, null)), () -> predicate.getAsBoolean());
    }

    @SuppressWarnings("unchecked")
    public static final <V1, V2, V3> BooleanSupplier uni(V1 v1, V2 v2, V3 v3, BooleanSupplier predicate) {
        return () -> doUni(Collection.of(v1, v2, v3).asMap(o -> Entry.of(o, null)), () -> predicate.getAsBoolean());
    }

    private static boolean doUni(Map<Object, Object> vars, Supplier<Boolean> predicate) {
        try {
            return VARIABLES.get(vars, predicate);
        } catch (UnboundVariableException uve) {
            if (uve.vars.anyMatch(vars::containsKey)) {
                return any(uve.bindings.map(vs -> () -> doUni(vs, predicate))).getAsBoolean();
            } else {
                throw uve;
            }
        }
    }

}
