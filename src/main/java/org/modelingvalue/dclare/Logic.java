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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.Objects;
import java.util.function.BiFunction;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.struct.impl.StructImpl;
import org.modelingvalue.collections.util.SerializableBiFunction;
import org.modelingvalue.collections.util.SerializableBiFunction.SerializableBiFunctionImpl;
import org.modelingvalue.collections.util.SerializableFunction;
import org.modelingvalue.collections.util.SerializableFunction.SerializableFunctionImpl;
import org.modelingvalue.collections.util.SerializableQuadFunction;
import org.modelingvalue.collections.util.SerializableQuadFunction.SerializableQuadFunctionImpl;
import org.modelingvalue.collections.util.SerializableSupplier;
import org.modelingvalue.collections.util.SerializableSupplier.SerializableSupplierImpl;
import org.modelingvalue.collections.util.SerializableTriFunction;
import org.modelingvalue.collections.util.SerializableTriFunction.SerializableTriFunctionImpl;

public final class Logic {
    private Logic() {
    }

    private static final boolean                                              USE_EXTEND = Boolean.getBoolean("USE_EXTEND");

    @SuppressWarnings("rawtypes")
    private static final BiFunction<Set<TermImpl>, TermImpl, Set<TermImpl>>   ADD_FACT   = (s, e) -> s == null ? Set.of(e) : s.add(e);

    private static final BiFunction<List<RuleImpl>, RuleImpl, List<RuleImpl>> ADD_RULE   = (l, e) -> {
                                                                                             if (l == null) {
                                                                                                 return List.of(e);
                                                                                             } else {
                                                                                                 int p = e.rulePrio();
                                                                                                 for (int i = 0; i < l.size(); i++) {
                                                                                                     if (l.get(i).rulePrio() > p) {
                                                                                                         return l.insert(i, e);
                                                                                                     }
                                                                                                 }
                                                                                                 return l.append(e);
                                                                                             }
                                                                                         };
    @SuppressWarnings("rawtypes")
    private static final Constant<TermImpl, Set<TermImpl>>                    FACTS      = Constant.of("FACTS", null, CoreSetableModifier.durable);

    @SuppressWarnings("rawtypes")
    private static final Constant<FunctImpl, List<RuleImpl>>                  RULES      = Constant.of("RULES", null, CoreSetableModifier.durable);

    private static abstract class ClauseImpl<F extends Term> extends StructImpl implements InvocationHandler {
        private static final long   serialVersionUID = 7315776001191198132L;

        private static final Method EQUALS;
        private static final Method HASHCODE;
        private static final Method TO_STRING;
        static {
            try {
                EQUALS = Object.class.getMethod("equals", Object.class);
                HASHCODE = Object.class.getMethod("hashCode");
                TO_STRING = Object.class.getMethod("toString");
            } catch (NoSuchMethodException | SecurityException e) {
                throw new Error(e);
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.equals(EQUALS)) {
                if (proxy == args[0]) {
                    return true;
                } else if (args[0] == null) {
                    return false;
                } else if (args[0].getClass() != proxy.getClass()) {
                    return false;
                } else {
                    return super.equals(Logic.unproxy(args[0]));
                }
            } else if (method.equals(HASHCODE)) {
                return super.hashCode();
            } else if (method.equals(TO_STRING)) {
                return toString();
            } else {
                throw new Error("No handler for " + method);
            }
        }

        protected ClauseImpl(Functor<F> functor, Object... args) {
            super(unproxy(functor, args));
        }

        protected ClauseImpl(FunctImpl<F> functor, Object... args) {
            super(array(functor, args));
        }

        protected ClauseImpl(Class<F> type, Object... args) {
            super(array(type, args));
        }

        protected ClauseImpl(Object[] args) {
            super(args);
        }

        private static final Object[] array(Object functor, Object[] args) {
            Object[] result = new Object[args.length + 1];
            result[0] = noProxy(functor);
            for (int i = 0; i < args.length; i++) {
                result[i + 1] = noProxy(args[i]);
            }
            return result;
        }

        @SuppressWarnings("rawtypes")
        private static final Object[] unproxy(Functor functor, Object[] args) {
            Object[] result = new Object[args.length + 1];
            result[0] = Logic.unproxy(functor);
            for (int i = 0; i < args.length; i++) {
                result[i + 1] = Logic.unproxy(args[i]);
            }
            return result;
        }

        protected abstract F proxy();

        protected abstract Class<F> type();

        protected abstract ClauseImpl<F> term(Object[] array);
    }

    private static final Object noProxy(Object object) {
        if (object instanceof Term) {
            throw new IllegalArgumentException();
        } else {
            return object;
        }
    }

    @SuppressWarnings("rawtypes")
    private static final Object unproxy(Object object) {
        if (object instanceof Term) {
            return Proxy.getInvocationHandler(object);
        } else {
            Objects.requireNonNull(object);
            return object;
        }
    }

    @SuppressWarnings("unchecked")
    private static final <T extends Term> ClauseImpl<T> unproxy(T object) {
        return (ClauseImpl<T>) Proxy.getInvocationHandler(object);
    }

    @SuppressWarnings("rawtypes")
    private static final Object proxy(Object object) {
        if (object instanceof ClauseImpl) {
            return ((ClauseImpl) object).proxy();
        } else {
            return object;
        }
    }

    // Functor

    public interface Functor<T> extends Term {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Term> FunctImpl<T> functImpl(SerializableSupplier<T> method, SerializableFunction<TermImpl<T>, Collection<TermImpl>> impl) {
        SerializableSupplierImpl<T> l = method.of();
        return new FunctImpl<T>((Class<T>) l.out(), l.getImplMethodName(), l.in(), impl != null ? impl.of() : null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Term> Functor<T> functor(SerializableSupplier<T> method, SerializableFunction<TermImpl<T>, Collection<TermImpl>> impl) {
        return functImpl(method, impl).proxy();
    }

    @SuppressWarnings("unchecked")
    public static <T extends Term> Functor<T> functor(SerializableSupplier<T> method) {
        return functImpl(method, null).proxy();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Term, A> FunctImpl<T> functImpl(SerializableFunction<A, T> method, SerializableFunction<TermImpl<T>, Collection<TermImpl>> impl) {
        SerializableFunctionImpl<A, T> l = method.of();
        return new FunctImpl<T>((Class<T>) l.out(), l.getImplMethodName(), l.in(), impl != null ? impl.of() : null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Term, A> Functor<T> functor(SerializableFunction<A, T> method, SerializableFunction<TermImpl<T>, Collection<TermImpl>> impl) {
        return functImpl(method, impl).proxy();
    }

    @SuppressWarnings("unchecked")
    public static <T extends Term, A> Functor<T> functor(SerializableFunction<A, T> method) {
        return functImpl(method, null).proxy();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Term, A, B> FunctImpl<T> functImpl(SerializableBiFunction<A, B, T> method, SerializableFunction<TermImpl<T>, Collection<TermImpl>> impl) {
        SerializableBiFunctionImpl<A, B, T> l = method.of();
        return new FunctImpl<T>((Class<T>) l.out(), l.getImplMethodName(), l.in(), impl != null ? impl.of() : null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Term, A, B> Functor<T> functor(SerializableBiFunction<A, B, T> method, SerializableFunction<TermImpl<T>, Collection<TermImpl>> impl) {
        return functImpl(method, impl.of()).proxy();
    }

    @SuppressWarnings("unchecked")
    public static <T extends Term, A, B> Functor<T> functor(SerializableBiFunction<A, B, T> method) {
        return functImpl(method, null).proxy();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Term, A, B, C> FunctImpl<T> functImpl(SerializableTriFunction<A, B, C, T> method, SerializableFunction<TermImpl<T>, Collection<TermImpl>> impl) {
        SerializableTriFunctionImpl<A, B, C, T> l = method.of();
        return new FunctImpl<T>((Class<T>) l.out(), l.getImplMethodName(), l.in(), impl != null ? impl.of() : null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Term, A, B, C> Functor<T> functor(SerializableTriFunction<A, B, C, T> method, SerializableFunction<TermImpl<T>, Collection<TermImpl>> impl) {
        return functImpl(method, impl).proxy();
    }

    @SuppressWarnings("unchecked")
    public static <T extends Term, A, B, C> Functor<T> functor(SerializableTriFunction<A, B, C, T> method) {
        return functImpl(method, null).proxy();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Term, A, B, C, D> FunctImpl<T> functImpl(SerializableQuadFunction<A, B, C, D, T> method, SerializableFunction<TermImpl<T>, Collection<TermImpl>> impl) {
        SerializableQuadFunctionImpl<A, B, C, D, T> l = method.of();
        return new FunctImpl<T>((Class<T>) l.out(), l.getImplMethodName(), l.in(), impl != null ? impl.of() : null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Term, A, B, C, D> Functor<T> functor(SerializableQuadFunction<A, B, C, D, T> method, SerializableFunction<TermImpl<T>, Collection<TermImpl>> impl) {
        return functImpl(method, impl).proxy();
    }

    @SuppressWarnings("unchecked")
    public static <T extends Term, A, B, C, D> Functor<T> functor(SerializableQuadFunction<A, B, C, D, T> method) {
        return functImpl(method, null).proxy();
    }

    public static final class FunctImpl<T extends Term> extends ClauseImpl<Functor<T>> {
        private static final long serialVersionUID = 285147889847599160L;

        @SuppressWarnings({"unchecked", "rawtypes"})
        private FunctImpl(Class<T> type, String name, List<Class<?>> args, SerializableFunction<TermImpl<T>, Collection<TermImpl>> l) {
            super((Class) Functor.class, type, name, args, l);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private FunctImpl(Object[] args) {
            super(args);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        protected final Functor<T> proxy() {
            return (Functor<T>) Proxy.newProxyInstance(type().getClassLoader(), new Class[]{Functor.class}, this);
        }

        @Override
        public String toString() {
            return ((String) get(2));
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        protected FunctImpl<T> term(Object[] array) {
            return new FunctImpl<T>(array);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Class<Functor<T>> type() {
            return (Class<Functor<T>>) get(0);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        protected SerializableFunction<TermImpl<T>, Collection<TermImpl>> lambda() {
            return (SerializableFunction<TermImpl<T>, Collection<TermImpl>>) get(4);
        }

        @SuppressWarnings("unchecked")
        protected Class<T> functType() {
            return (Class<T>) get(1);
        }
    }

    // Lists

    public interface L<E> extends Term {
    }

    @SuppressWarnings("rawtypes")
    private static final FunctImpl<L> LIST_FUNCTOR_0       = functImpl((SerializableSupplier<L>) Logic::l, null);
    @SuppressWarnings("rawtypes")
    private static final FunctImpl<L> LIST_FUNCTOR_2       = functImpl((SerializableBiFunction<Object, L, L>) Logic::l, null);
    @SuppressWarnings("rawtypes")
    private static final Functor<L>   LIST_FUNCTOR_2_PROXY = LIST_FUNCTOR_2.proxy();
    @SuppressWarnings("rawtypes")
    private static final TermImpl<L>  EMPTY_LIST           = termImpl(LIST_FUNCTOR_0);
    @SuppressWarnings("rawtypes")
    private static final L            EMPTY_LIST_PROXY     = EMPTY_LIST.proxy();

    @SuppressWarnings("unchecked")
    public static <E> L<E> l(E head, L<E> tail) {
        return term(LIST_FUNCTOR_2_PROXY, head, tail);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <E> L<E> l() {
        return EMPTY_LIST_PROXY;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <E> L<E> l(E... es) {
        return list(es).proxy();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <E> TermImpl<L> list(E... es) {
        TermImpl<L> l = EMPTY_LIST;
        for (int i = es.length - 1; i >= 0; i--) {
            l = termImpl(LIST_FUNCTOR_2, unproxy(es[i]), l);
        }
        return l;
    }

    @SuppressWarnings("rawtypes")
    private static <E> TermImpl<L> list(List<E> es) {
        TermImpl<L> l = EMPTY_LIST;
        for (int i = es.size() - 1; i >= 0; i--) {
            l = termImpl(LIST_FUNCTOR_2, unproxy(es.get(i)), l);
        }
        return l;
    }

    // Variables

    public static interface Variable extends Term {
    }

    @SuppressWarnings("unchecked")
    public static <F extends Term> F var(Class<F> type, String id) {
        return new VarImpl<F>(type, id).proxy();
    }

    private static final class VarImpl<F extends Term> extends ClauseImpl<F> {
        private static final long serialVersionUID = -8998368070388908726L;

        private VarImpl(Class<F> type, String name) {
            super(type, name);
        }

        private VarImpl(Object[] args) {
            super(args);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected final F proxy() {
            return (F) Proxy.newProxyInstance(type().getClassLoader(), new Class[]{type(), Variable.class}, this);
        }

        @Override
        public String toString() {
            return get(1).toString();
        }

        @Override
        @SuppressWarnings("unchecked")
        protected VarImpl<F> term(Object[] array) {
            return new VarImpl<F>(array);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Class<F> type() {
            return (Class<F>) get(0);
        }
    }

    // Terms

    public static interface Term {
    }

    @SuppressWarnings("unchecked")
    public static <F extends Term> F term(Functor<F> functor, Object... args) {
        return new TermImpl<F>(functor, args).proxy();
    }

    private static <F extends Term> TermImpl<F> termImpl(FunctImpl<F> functor, Object... args) {
        return new TermImpl<F>(functor, args);
    }

    public static class TermImpl<F extends Term> extends ClauseImpl<F> {
        private static final long serialVersionUID = -1605559565948158856L;

        private TermImpl(Functor<F> functor, Object... args) {
            super(functor, args);
        }

        private TermImpl(FunctImpl<F> functor, Object... args) {
            super(functor, args);
        }

        private TermImpl(Object[] args) {
            super(args);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected F proxy() {
            return (F) Proxy.newProxyInstance(type().getClassLoader(), new Class[]{type(), Term.class}, this);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected TermImpl<F> term(Object[] array) {
            return new TermImpl<F>(array);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public String toString() {
            if (type() == L.class) {
                return list().toString().substring(4);
            } else {
                String string = super.toString();
                return string.substring(1, string.length() - 1).replaceFirst(",", "(") + ")";
            }
        }

        public boolean isAtom() {
            for (int i = 1; i < length(); i++) {
                if (get(i) instanceof TermImpl) {
                    return false;
                }
            }
            return true;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Class<F> type() {
            return functor().functType();
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public FunctImpl<F> functor() {
            return (FunctImpl<F>) get(0);
        }

        @SuppressWarnings("rawtypes")
        protected final void makeFact() {
            FACTS.force(this, ADD_FACT, this);
            Object[] array = toArray();
            if (USE_EXTEND) {
                patterns(1, array);
            } else {
                for (int i = 1; i < array.length; i++) {
                    array[i] = getType(i);
                    FACTS.force(term(array), ADD_FACT, this);
                    array = toArray();
                }
            }
        }

        private void patterns(int i, Object[] array) {
            if (i < length()) {
                array = array.clone();
                if (array[i] == null) {
                    array[i] = get(i);
                    FACTS.force(term(array), ADD_FACT, this);
                }
                patterns(i + 1, array);
                if (array[i] != null) {
                    array[i] = getType(i);
                    FACTS.force(term(array), ADD_FACT, this);
                }
                patterns(i + 1, array);
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        protected Map<VarImpl, Object> variables() {
            Map<VarImpl, Object> vars = Map.of();
            for (int i = 1; i < length(); i++) {
                if (get(i) instanceof VarImpl) {
                    vars = vars.put((VarImpl) get(i), ((VarImpl) get(i)).type());
                } else if (get(i) instanceof TermImpl) {
                    vars = vars.putAll(((TermImpl) get(i)).variables());
                }
            }
            return vars;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        protected Map<VarImpl, Object> getBinding(TermImpl<F> term, Map<VarImpl, Object> vars) {
            if (get(0).equals(term.get(0))) {
                for (int i = 1; i < length(); i++) {
                    Object tv = term.get(i);
                    Class tt = tv instanceof TermImpl ? ((TermImpl) tv).type() : tv instanceof Class ? (Class) tv : null;
                    tv = tv instanceof Class ? null : tv;
                    if (get(i) instanceof VarImpl) {
                        VarImpl var = (VarImpl) get(i);
                        Object vv = vars.get(var);
                        Class vt = vv instanceof TermImpl ? ((TermImpl) vv).type() : vv instanceof Class ? (Class) vv : null;
                        vv = vv instanceof Class ? null : vv;
                        if (vv != null) {
                            if (tv != null && !tv.equals(vv)) {
                                return null;
                            }
                        } else if (tv != null) {
                            if (var.type().isAssignableFrom(tt)) {
                                vars = vars.put(var, tv);
                            } else {
                                return null;
                            }
                        } else if (tt == null || !var.type().isAssignableFrom(tt)) {
                            return null;
                        } else if (vt != null && !vt.equals(tt)) {
                            return null;
                        } else {
                            vars = vars.put(var, tt);
                        }
                    } else if (get(i) instanceof TermImpl) {
                        TermImpl t = (TermImpl) get(i);
                        if (tv != null) {
                            if (tv instanceof TermImpl) {
                                vars = t.getBinding((TermImpl) tv, vars);
                                if (vars == null) {
                                    return null;
                                }
                            } else {
                                return null;
                            }
                        } else if (tt == null || !t.type().isAssignableFrom(tt)) {
                            return null;
                        }
                    } else if (tv != null && !tv.equals(get(i))) {
                        return null;
                    }
                }
                return vars;
            } else {
                return null;
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        protected TermImpl setBinding(Map<VarImpl, Object> vars) {
            Object[] array = toArray();
            for (int i = 1; i < length(); i++) {
                if (get(i) instanceof VarImpl) {
                    array[i] = vars.get((VarImpl) get(i));
                } else if (get(i) instanceof TermImpl) {
                    array[i] = ((TermImpl) get(i)).setBinding(vars);
                }
            }
            return term(array);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public <V extends Term> TermImpl<V> getTerm(int i) {
            Object v = get(i);
            return v instanceof TermImpl ? (TermImpl<V>) v : null;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public Class getType(int i) {
            Object v = get(i);
            return v instanceof Class ? (Class) v : v instanceof TermImpl ? ((TermImpl) v).type() : null;
        }

        @SuppressWarnings("unchecked")
        public <V> V getVal(int i) {
            Object v = get(i);
            return v instanceof Class || v instanceof TermImpl ? null : (V) v;
        }

        public TermImpl<F> set(int i, Object v) {
            Object[] array = toArray();
            array[i] = v;
            return term(array);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public Set<TermImpl> incomplete() {
            return Set.of(Logic.incomplete(Logic.list(this)));
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        protected Collection<TermImpl> match(List<TermImpl> der) {
            int non = nrOfNulls();
            int len = length();
            if (!USE_EXTEND && (non > 1 || non >= len - 1)) {
                return Set.of(Logic.incomplete(der.append(this)));
            } else {
                Set<TermImpl> facts = FACTS.get(this);
                if (facts == null) {
                    SerializableFunction<TermImpl<F>, Collection<TermImpl>> lambda = functor().lambda();
                    if (lambda != null) {
                        return lambda.apply(this);
                    } else {
                        List<RuleImpl> rules = RULES.get(functor());
                        if (rules != null) {
                            int i = der.lastIndexOf(this);
                            if (i >= 0) {
                                return Set.of(Logic.incomplete(der.sublist(i, der.size()).append(this)));
                            } else {
                                Collection<TermImpl> r = Set.of();
                                for (RuleImpl rule : rules) {
                                    Collection<TermImpl> eval = rule.eval(this, der.append(this));
                                    if (non == 0) {
                                        eval = eval.asSet();
                                        if (eval.equals(Set.of(this))) {
                                            r = eval;
                                            break;
                                        }
                                    }
                                    r = Collection.concat(r, eval);
                                }
                                if (non < 2 && non < len - 1) {
                                    Set<TermImpl> set = r.asSet();
                                    FACTS.force(this, set);
                                    for (TermImpl e : set) {
                                        FACTS.force(e, Set.of(e));
                                    }
                                    return set;
                                } else {
                                    return r;
                                }
                            }
                        } else {
                            return Set.of();
                        }
                    }
                } else {
                    return facts;
                }
            }
        }

        @SuppressWarnings("rawtypes")
        protected int termPrio(List<TermImpl> der) {
            int non = nrOfNulls();
            if (!USE_EXTEND && (non > 1 || non >= length() - 1)) {
                return Integer.MAX_VALUE;
            } else {
                Set<TermImpl> facts = FACTS.get(this);
                if (facts != null) {
                    return Integer.MIN_VALUE + facts.size();
                } else {
                    List<RuleImpl> rules = RULES.get(functor());
                    if (rules != null) {
                        if (der.lastIndexOf(this) >= 0) {
                            return Integer.MAX_VALUE;
                        } else {
                            return non;
                        }
                    }
                }
                return Integer.MIN_VALUE;
            }
        }

        @SuppressWarnings("rawtypes")
        protected List<TermImpl> list() {
            List<TermImpl> l = List.of();
            TermImpl t = this;
            while (t.length() == 3) {
                l = l.add((TermImpl) t.get(1));
                t = (TermImpl) t.get(2);
            }
            return l;
        }

        protected int nrOfNulls() {
            int nr = 0;
            for (int i = 1; i < length(); i++) {
                Object v = get(i);
                if (v == null || v instanceof Class) {
                    nr++;
                }
            }
            return nr;
        }
    };

    // Rules

    public static interface Rule extends Term {
    }

    private static final FunctImpl<Rule> RULE_FUNCTOR       = functImpl((SerializableBiFunction<Term, Goal, Rule>) Logic::rule, null);
    private static final Functor<Rule>   RULE_FUNCTOR_PROXY = RULE_FUNCTOR.proxy();

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Rule rule(Term term, Term... goals) {
        return rule(term, goal(goals));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Rule rule(Term term, Goal goal) {
        RuleImpl ruleImpl = new RuleImpl(term, goal);
        TermImpl termImpl = (TermImpl) unproxy(term);
        RULES.force(termImpl.functor(), ADD_RULE, ruleImpl);
        return ruleImpl.proxy();
    }

    private static final class RuleImpl extends TermImpl<Rule> {
        private static final long serialVersionUID = -4602043866952049391L;

        private RuleImpl(Term term, Goal goal) {
            super(RULE_FUNCTOR_PROXY, term, goal);
        }

        @SuppressWarnings("rawtypes")
        private RuleImpl(TermImpl term, GoalImpl goal) {
            super(RULE_FUNCTOR, term, goal);
        }

        private RuleImpl(Object[] args) {
            super(args);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected final Rule proxy() {
            return (Rule) Proxy.newProxyInstance(type().getClassLoader(), new Class[]{Rule.class}, this);
        }

        @SuppressWarnings("rawtypes")
        protected final TermImpl term() {
            return ((TermImpl) get(1));
        }

        @SuppressWarnings("rawtypes")
        protected final GoalImpl goal() {
            return ((GoalImpl) get(2));
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        protected Collection<TermImpl> eval(TermImpl term, List<TermImpl> der) {
            TermImpl head = term();
            Map<VarImpl, Object> binding = head.getBinding(term, Map.of());
            if (binding == null) {
                return Set.of();
            } else {
                Collection<Map<VarImpl, Object>> r = goal().eval(variables().putAll(binding), der);
                return r.map(m -> {
                    TermImpl it = (TermImpl) m.get(INCOMPLETE_VAR);
                    return it != null ? it : head.setBinding(m);
                });
            }
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        protected RuleImpl term(Object[] array) {
            return new RuleImpl(array);
        }

        protected int rulePrio() {
            return goal().goals().size();
        }
    }

    // Goals

    public static interface Goal extends Term {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final FunctImpl<Goal> GOAL_FUNCTOR       = functImpl((SerializableFunction<L, Goal>) Logic::goal, null);
    private static final Functor<Goal>   GOAL_FUNCTOR_PROXY = GOAL_FUNCTOR.proxy();

    public static boolean is(Term... goals) {
        return new GoalImpl(list(goals)).eval().anyMatch(e -> !e.containsKey(INCOMPLETE_VAR));
    }

    @SuppressWarnings("rawtypes")
    public static Set<Map<Variable, Object>> eval(Term... goals) {
        return new GoalImpl(list(goals)).eval().map(m -> m.asMap(e -> Entry.of((Variable) e.getKey().proxy(), proxy(e.getValue())))).asSet();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Goal goal(Term... goals) {
        return new GoalImpl(list(goals)).proxy();
    }

    @SuppressWarnings("unchecked")
    public static Goal goal(L<Term> goals) {
        return new GoalImpl(goals).proxy();
    }

    private static final class GoalImpl extends TermImpl<Goal> {
        private static final long serialVersionUID = -4100263206389367132L;

        private GoalImpl(L<Term> goals) {
            super(GOAL_FUNCTOR_PROXY, goals);
        }

        @SuppressWarnings("rawtypes")
        private GoalImpl(TermImpl<L> goals) {
            super(GOAL_FUNCTOR, goals);
        }

        private GoalImpl(Object[] args) {
            super(args);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected final Goal proxy() {
            return (Goal) Proxy.newProxyInstance(type().getClassLoader(), new Class[]{Goal.class}, this);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        protected GoalImpl term(Object[] array) {
            return new GoalImpl(array);
        }

        @SuppressWarnings("rawtypes")
        public Collection<Map<VarImpl, Object>> eval() {
            return eval(variables(), List.of());
        }

        @SuppressWarnings("rawtypes")
        protected Collection<Map<VarImpl, Object>> eval(Map<VarImpl, Object> vars, List<TermImpl> der) {
            return eval(goals(), Set.of(vars), der);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        protected List<TermImpl> goals() {
            return ((TermImpl) get(1)).list();
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private Collection<Map<VarImpl, Object>> eval(List<TermImpl> goals, Collection<Map<VarImpl, Object>> vars, List<TermImpl> der) {
            if (goals.isEmpty()) {
                return vars;
            } else {
                return vars.<Map<VarImpl, Object>> flatMap(v -> {
                    if (v.containsKey(INCOMPLETE_VAR)) {
                        return Set.of(v);
                    } else {
                        List<TermImpl> actual = List.of();
                        for (TermImpl g : goals) {
                            actual = actual.add(g.setBinding(v));
                        }
                        int i = first(actual, der);
                        TermImpl f = actual.get(i);
                        TermImpl g = goals.get(i);
                        Collection<TermImpl> m = f.match(der);
                        return eval(goals.removeIndex(i), m.<Map<VarImpl, Object>> map(t -> {
                            if (t.type() == Incomplete.class) {
                                return Map.of(Entry.of(INCOMPLETE_VAR, t));
                            } else {
                                Map<VarImpl, Object> b = g.getBinding(t, Map.of());
                                return b == null ? Map.of() : v.putAll(b);
                            }
                        }), der);
                    }
                });
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static int first(List<TermImpl> list, List<TermImpl> der) {
            int first = -1;
            int min = Integer.MAX_VALUE;
            for (int i = 0; i < list.size(); i++) {
                int prio = list.get(i).termPrio(der);
                if (first == -1 || prio < min) {
                    first = i;
                    min = prio;
                }
            }
            return first;
        }
    }

    // Incomplete

    public interface Incomplete extends Term {
    }

    @SuppressWarnings("rawtypes")
    private static final FunctImpl<Incomplete> INCOMPLETE_FUNCTOR       = functImpl((SerializableFunction<L, Incomplete>) Logic::incomplete, null);
    private static final Functor<Incomplete>   INCOMPLETE_FUNCTOR_PROXY = INCOMPLETE_FUNCTOR.proxy();
    private static final VarImpl<Incomplete>   INCOMPLETE_VAR           = new VarImpl<Incomplete>(Incomplete.class, "I");
    private static final Incomplete            INCOMPLETE_VAR_PROXY     = INCOMPLETE_VAR.proxy();

    @SuppressWarnings("unchecked")
    public static Incomplete incompleteVar() {
        return INCOMPLETE_VAR_PROXY;
    }

    public static Map<Variable, Object> incomplete(Term... der) {
        return Map.of(Entry.of((Variable) incompleteVar(), incomplete(list(der)).proxy()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static TermImpl<Incomplete> incomplete(List<TermImpl> der) {
        return incomplete(list(der));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static TermImpl<Incomplete> incomplete(TermImpl der) {
        return termImpl(INCOMPLETE_FUNCTOR, der);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Incomplete incomplete(L der) {
        return term(INCOMPLETE_FUNCTOR_PROXY, der);
    }

    // Facts, Is

    public static void fact(Term term) {
        ((TermImpl<?>) unproxy(term)).makeFact();
    }

    // Variable bindings

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Map<Variable, Object> bind(Term... varVal) {
        Map<Variable, Object> b = Map.of();
        for (int i = 0; i < varVal.length; i += 2) {
            b = b.add(Entry.of((Variable) varVal[i], varVal[i + 1]));
        }
        return b;
    }

    // Is

    private static Functor<Pred> is = functor((SerializableBiFunction<Int, IntLit, Pred>) Logic::is);

    public static Pred is(Int i, IntLit r) {
        return term(is, i, r);
    }

    // Integer

    public static interface Int extends Term {
    }

    public static interface IntLit extends Int {
    }

    public static interface IntFun extends Int {
    }

    private static Functor<IntLit> i = functor((SerializableFunction<BigInteger, IntLit>) Logic::i);

    private static IntLit i(BigInteger x) {
        return term(i, x);
    }

    public static IntLit i(long x) {
        return i(BigInteger.valueOf(x));
    }

    public static IntLit ilv(String name) {
        return var(IntLit.class, name);
    }

    public static IntFun ifv(String name) {
        return var(IntFun.class, name);
    }

    public static Int iv(String name) {
        return var(Int.class, name);
    }

    // Eq

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Functor<Pred> eq = functor(Logic::eq, t -> {
        TermImpl at = t.getTerm(1);
        TermImpl bt = t.getTerm(2);
        if (at == null && bt == null) {
            return t.incomplete();
        } else if (at == null) {
            return Set.of(t.set(1, bt));
        } else if (bt == null) {
            return Set.of(t.set(2, at));
        } else {
            return at.equals(bt) ? Set.of(t) : Set.of();
        }
    });

    public static Pred eq(Term a, Term b) {
        return term(eq, a, b);
    }

    // Plus

    public static interface Pred extends Term {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Functor<Pred> plusPred = functor((SerializableTriFunction<IntLit, IntLit, IntLit, Pred>) Logic::plus, t -> {
        TermImpl<IntLit> at = t.getTerm(1);
        TermImpl<IntLit> bt = t.getTerm(2);
        TermImpl<IntLit> ct = t.getTerm(3);
        BigInteger ai = at != null ? at.getVal(1) : null;
        BigInteger bi = bt != null ? bt.getVal(1) : null;
        BigInteger ci = ct != null ? ct.getVal(1) : null;
        if (ai != null && bi != null && ci != null) {
            return ai.add(bi).equals(ci) ? Set.of(t) : Set.of();
        } else if (ai != null && bi != null && ci == null) {
            return Set.of(t.set(3, at.set(1, ai.add(bi))));
        } else if (ai != null && bi == null && ci != null) {
            return Set.of(t.set(2, at.set(1, ci.subtract(ai))));
        } else if (ai == null && bi != null && ci != null) {
            return Set.of(t.set(1, bt.set(1, ci.subtract(bi))));
        } else {
            return t.incomplete();
        }
    });

    public static Pred plus(IntLit a, IntLit b, IntLit r) {
        return term(plusPred, a, b, r);
    }

    private static Functor<IntFun> plusFunc = functor((SerializableBiFunction<Int, Int, IntFun>) Logic::plus);

    public static IntFun plus(Int a, Int b) {
        return term(plusFunc, a, b);
    }

    public static void isRules() {
        IntLit PL = ilv("PL");
        IntLit QL = ilv("QL");
        IntLit RL = ilv("RL");

        Int X = iv("X");
        Int Y = iv("Y");

        rule(is(PL, RL), eq(PL, RL));
        rule(is(plus(X, Y), RL), is(X, PL), is(Y, QL), plus(PL, QL, RL));
    }

}
