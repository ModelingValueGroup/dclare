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
import org.modelingvalue.collections.util.Single;
import org.modelingvalue.collections.util.TriPredicate;
import org.modelingvalue.collections.util.Triple;
import org.modelingvalue.dclare.ex.NonDeterministicException;

public final class Logic {
    private Logic() {
    }

    private static final Context<Map<Object, Object>> VARIABLES      = Context.of(Map.of());
    private static final String                       NO_MATCH_FOUND = "No match found";

    public static abstract class Functor implements Feature {

        protected final <S extends Struct> S bind(S in) {
            Map<Object, Object> vars = VARIABLES.get();
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
            S out = copy(array);
            if (!empty.isEmpty()) {
                Set<S> extend = extend(out);
                if (extend.isEmpty()) {
                    throw new NonDeterministicException(in, this, NO_MATCH_FOUND);
                } else {
                    Set<Object> em = empty;
                    throw new UnboundVariableException(empty, extend.map(s -> {
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
            return out;
        }

        protected abstract <S extends Struct> S copy(Object[] array);

        protected abstract <S extends Struct> Set<S> extend(S s);
    };

    // Functions

    public static final <O, T> Fun1<O, T> fun1(Object id, java.util.function.Function<O, Supplier<T>> f) {
        return new Fun1<O, T>(id, null, (o) -> f.apply(o).get());
    }

    public static final <O, T> Fun1<O, T> fun1(Object id, T def) {
        return new Fun1<O, T>(id, def, null);
    }

    public static final <O1, O2, T> Fun2<O1, O2, T> fun2(Object id, java.util.function.BiFunction<O1, O2, Supplier<T>> f) {
        return new Fun2<O1, O2, T>(id, null, (o1, o2) -> f.apply(o1, o2).get());
    }

    public static final <O1, O2, T> Fun2<O1, O2, T> fun2(Object id, T def) {
        return new Fun2<O1, O2, T>(id, def, null);
    }

    public static final <O1, O2, O3, T> Fun3<O1, O2, O3, T> function(Object id, org.modelingvalue.collections.util.TriFunction<O1, O2, O3, Supplier<T>> f) {
        return new Fun3<O1, O2, O3, T>(id, null, (o1, o2, o3) -> f.apply(o1, o2, o3).get());
    }

    public static final <O1, O2, O3, T> Fun3<O1, O2, O3, T> function(Object id, T def) {
        return new Fun3<O1, O2, O3, T>(id, def, null);
    }

    public static final class Fun1<O, T> extends Functor {
        private final Constant<Single<O>, T>             constant;
        private final Setable<Single<O>, Set<Single<O>>> extend;

        private Fun1(Object id, T def, java.util.function.Function<O, T> f) {
            extend = Setable.<Single<O>, Set<Single<O>>> of(Single.of(id), Set.of());
            constant = Constant.<Single<O>, T> of(id, def, f == null ? null : o -> f.apply(o.a()), (tx, o, b, t) -> {
                if (t != null) {
                    extend.add(Single.of(null), o);
                }
            });
        }

        public Supplier<T> sup(O o) {
            return () -> get(o);
        }

        public T get(O o) {
            return constant.get(bind(Single.of(o)));
        }

        public void set(O o, T t) {
            constant.force(Single.of(o), t);
        }

        @Override
        public String toString() {
            return constant.toString();
        }

        @SuppressWarnings("unchecked")
        @Override
        protected <S extends Struct> S copy(Object[] array) {
            return (S) Single.of(array[0]);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        protected <S extends Struct> Set<S> extend(S s) {
            return extend.get((Single) s);
        }
    }

    public static final class Fun2<O1, O2, T> extends Functor {
        private final Constant<Pair<O1, O2>, T>                constant;
        private final Setable<Pair<O1, O2>, Set<Pair<O1, O2>>> extend;

        private Fun2(Object id, T def, java.util.function.BiFunction<O1, O2, T> f) {
            extend = Setable.<Pair<O1, O2>, Set<Pair<O1, O2>>> of(Single.of(id), Set.of());
            constant = Constant.<Pair<O1, O2>, T> of(id, def, f == null ? null : o -> f.apply(o.a(), o.b()), (tx, o, b, t) -> {
                if (t != null) {
                    extend.add(Pair.of(null, null), o);
                    extend.add(Pair.of(o.a(), null), o);
                    extend.add(Pair.of(null, o.b()), o);
                }
            });
        }

        public Supplier<T> sup(O1 o1, O2 o2) {
            return () -> get(o1, o2);
        }

        public T get(O1 o1, O2 o2) {
            return constant.get(bind(Pair.of(o1, o2)));
        }

        public void set(O1 o1, O2 o2, T t) {
            constant.force(Pair.of(o1, o2), t);
        }

        @Override
        public String toString() {
            return constant.toString();
        }

        @SuppressWarnings("unchecked")
        @Override
        protected <S extends Struct> S copy(Object[] array) {
            return (S) Pair.of(array[0], array[1]);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        protected <S extends Struct> Set<S> extend(S s) {
            return extend.get((Pair) s);
        }
    }

    public static final class Fun3<O1, O2, O3, T> extends Functor {
        private final Constant<Triple<O1, O2, O3>, T>                      constant;
        private final Setable<Triple<O1, O2, O3>, Set<Triple<O1, O2, O3>>> extend;

        private Fun3(Object id, T def, org.modelingvalue.collections.util.TriFunction<O1, O2, O3, T> f) {
            extend = Setable.<Triple<O1, O2, O3>, Set<Triple<O1, O2, O3>>> of(Single.of(id), Set.of());
            constant = Constant.<Triple<O1, O2, O3>, T> of(id, def, f == null ? null : o -> f.apply(o.a(), o.b(), o.c()), (tx, o, b, t) -> {
                if (t != null) {
                    extend.add(Triple.of(null, null, null), o);
                    extend.add(Triple.of(o.a(), null, null), o);
                    extend.add(Triple.of(null, o.b(), null), o);
                    extend.add(Triple.of(null, null, o.c()), o);
                    extend.add(Triple.of(o.a(), o.b(), null), o);
                    extend.add(Triple.of(o.a(), null, o.c()), o);
                    extend.add(Triple.of(null, o.b(), o.c()), o);
                }
            });
        }

        public Supplier<T> sup(O1 o1, O2 o2, O3 o3) {
            return () -> get(o1, o2, o3);
        }

        public T get(O1 o1, O2 o2, O3 o3) {
            return constant.get(bind(Triple.of(o1, o2, o3)));
        }

        public void set(O1 o1, O2 o2, O3 o3, T t) {
            constant.force(Triple.of(o1, o2, o3), t);
        }

        @Override
        public String toString() {
            return constant.toString();
        }

        @SuppressWarnings("unchecked")
        @Override
        protected <S extends Struct> S copy(Object[] array) {
            return (S) Triple.of(array[0], array[1], array[2]);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        protected <S extends Struct> Set<S> extend(S s) {
            return extend.get((Triple) s);
        }
    }

    // Relations

    public static final <O> Rel1<O> rel1(Object id, java.util.function.Function<O, BooleanSupplier> p) {
        return new Rel1<O>(id, (o) -> p.apply(o).getAsBoolean());
    }

    public static final <O> Rel1<O> rel1(Object id) {
        return new Rel1<O>(id, null);
    }

    public static final <O1, O2> Rel2<O1, O2> rel2(Object id, java.util.function.BiFunction<O1, O2, BooleanSupplier> p) {
        return new Rel2<O1, O2>(id, (o1, o2) -> p.apply(o1, o2).getAsBoolean());
    }

    public static final <O1, O2> Rel2<O1, O2> rel2(Object id) {
        return new Rel2<O1, O2>(id, null);
    }

    public static final <O1, O2, O3> Rel3<O1, O2, O3> rel3(Object id, org.modelingvalue.collections.util.TriFunction<O1, O2, O3, BooleanSupplier> p) {
        return new Rel3<O1, O2, O3>(id, (o1, o2, o3) -> p.apply(o1, o2, o3).getAsBoolean());
    }

    public static final <O1, O2, O3> Rel3<O1, O2, O3> rel3(Object id) {
        return new Rel3<O1, O2, O3>(id, null);
    }

    public static final class Rel1<O> extends Functor {
        private final Constant<Single<O>, Boolean>       constant;
        private final Setable<Single<O>, Set<Single<O>>> extend;

        private Rel1(Object id, Predicate<O> p) {
            extend = Setable.<Single<O>, Set<Single<O>>> of(Single.of(id), Set.of());
            constant = Constant.<Single<O>, Boolean> of(id, p != null ? null : false, p == null ? null : o -> p.test(o.a()), (tx, o, b, t) -> {
                if (t != null) {
                    extend.add(Single.of(null), o);
                }
            });
        }

        @SuppressWarnings("unchecked")
        public BooleanSupplier sup(O o) {
            return () -> is(o);
        }

        public boolean is(O o) {
            return constant.get(bind(Single.of(o)));
        }

        public void set(O o) {
            constant.force(Single.of(o), true);
        }

        @Override
        public String toString() {
            return constant.toString();
        }

        @SuppressWarnings("unchecked")
        @Override
        protected <S extends Struct> S copy(Object[] array) {
            return (S) Single.of(array[0]);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        protected <S extends Struct> Set<S> extend(S s) {
            return extend.get((Single) s);
        }
    }

    public static final class Rel2<O1, O2> extends Functor {
        private final Constant<Pair<O1, O2>, Boolean>          constant;
        private final Setable<Pair<O1, O2>, Set<Pair<O1, O2>>> extend;

        private Rel2(Object id, BiPredicate<O1, O2> p) {
            extend = Setable.<Pair<O1, O2>, Set<Pair<O1, O2>>> of(Single.of(id), Set.of());
            constant = Constant.<Pair<O1, O2>, Boolean> of(id, p != null ? null : false, p == null ? null : o -> p.test(o.a(), o.b()), (tx, o, b, t) -> {
                if (t) {
                    extend.add(Pair.of(null, null), o);
                    extend.add(Pair.of(o.a(), null), o);
                    extend.add(Pair.of(null, o.b()), o);
                }
            });
        }

        @SuppressWarnings("unchecked")
        public BooleanSupplier sup(O1 o1, O2 o2) {
            return () -> is(o1, o2);
        }

        public boolean is(O1 o1, O2 o2) {
            return constant.get(bind(Pair.of(o1, o2)));
        }

        public void set(O1 o1, O2 o2) {
            constant.force(Pair.of(o1, o2), true);
        }

        @Override
        public String toString() {
            return constant.toString();
        }

        @SuppressWarnings("unchecked")
        @Override
        protected <S extends Struct> S copy(Object[] array) {
            return (S) Pair.of(array[0], array[1]);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        protected <S extends Struct> Set<S> extend(S s) {
            return extend.get((Pair) s);
        }
    }

    public static final class Rel3<O1, O2, O3> extends Functor {
        private final Constant<Triple<O1, O2, O3>, Boolean>                constant;
        private final Setable<Triple<O1, O2, O3>, Set<Triple<O1, O2, O3>>> extend;

        private Rel3(Object id, TriPredicate<O1, O2, O3> p) {
            extend = Setable.<Triple<O1, O2, O3>, Set<Triple<O1, O2, O3>>> of(Single.of(id), Set.of());
            constant = Constant.<Triple<O1, O2, O3>, Boolean> of(id, p != null ? null : false, p == null ? null : o -> p.test(o.a(), o.b(), o.c()), (tx, o, b, t) -> {
                if (t) {
                    extend.add(Triple.of(null, null, null), o);
                    extend.add(Triple.of(o.a(), null, null), o);
                    extend.add(Triple.of(null, o.b(), null), o);
                    extend.add(Triple.of(null, null, o.c()), o);
                    extend.add(Triple.of(o.a(), o.b(), null), o);
                    extend.add(Triple.of(o.a(), null, o.c()), o);
                    extend.add(Triple.of(null, o.b(), o.c()), o);
                }
            });
        }

        @SuppressWarnings("unchecked")
        public BooleanSupplier sup(O1 o1, O2 o2, O3 o3) {
            return () -> is(o1, o2, o3);
        }

        public boolean is(O1 o1, O2 o2, O3 o3) {
            return constant.get(bind(Triple.of(o1, o2, o3)));
        }

        public void set(O1 o1, O2 o2, O3 o3) {
            constant.force(Triple.of(o1, o2, o3), true);
        }

        @Override
        public String toString() {
            return constant.toString();
        }

        @SuppressWarnings("unchecked")
        @Override
        protected <S extends Struct> S copy(Object[] array) {
            return (S) Triple.of(array[0], array[1], array[2]);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        protected <S extends Struct> Set<S> extend(S s) {
            return extend.get((Triple) s);
        }
    }

    // Inv

    public static final BooleanSupplier sup(boolean b) {
        return () -> b;
    }

    public static final <T> Supplier<T> sup(T t) {
        return () -> t;
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
            RuntimeException[] rte = new RuntimeException[1];
            boolean result = or.anyMatch(p -> {
                try {
                    return p.getAsBoolean();
                } catch (NonDeterministicException nde) {
                    if (!(nde.getFeature() instanceof Functor)) {
                        rte[0] = nde;
                    }
                    return false;
                }
            });
            if (!result && rte[0] != null) {
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
            RuntimeException[] rte = new RuntimeException[1];
            boolean result = and.anyMatch(p -> {
                try {
                    return p.getAsBoolean();
                } catch (NonDeterministicException nde) {
                    if (!(nde.getFeature() instanceof Functor)) {
                        rte[0] = nde;
                    }
                    return true;
                }
            });
            if (result && rte[0] != null) {
                throw rte[0];
            } else {
                return result;
            }
        }
    }

    // Unification

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
