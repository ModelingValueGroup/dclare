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
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.struct.impl.StructImpl;
import org.modelingvalue.collections.util.Context;
import org.modelingvalue.collections.util.Pair;

public final class Logic {
    private Logic() {
    }

    @SuppressWarnings("rawtypes")
    private static final Context<List<TermImpl>>                       DERIVED = Context.of(List.of());

    @SuppressWarnings("rawtypes")
    private static final Constant<TermImpl, Set<TermImpl>>             FACTS   = Constant.of("FACTS", Set.of(), CoreSetableModifier.durable);

    @SuppressWarnings("rawtypes")
    private static final Constant<Pair<Class, Integer>, Set<RuleImpl>> RULES   = Constant.of("RULES", Set.of(), CoreSetableModifier.durable);

    private static abstract class AbstractTermImpl<F> extends StructImpl implements InvocationHandler {
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

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public String toString() {
            if (functor() == L.class) {
                List list = List.of();
                AbstractTermImpl ht = this;
                while (ht.length() == 2) {
                    list = list.prepend(ht.get(1));
                    ht = (AbstractTermImpl) ht.get(2);
                }
                return list.toString().substring(4);
            } else {
                String string = super.toString();
                return string.substring(1, string.length() - 1).replaceFirst(",", "(") + ")";
            }
        }

        protected AbstractTermImpl(Class<F> functor, Object... args) {
            super(unproxy(functor, args));
        }

        @SuppressWarnings("rawtypes")
        private static final Object[] unproxy(Class functor, Object[] args) {
            Object[] result = new Object[args.length + 1];
            result[0] = functor;
            for (int i = 0; i < args.length; i++) {
                result[i + 1] = Logic.unproxy(args[i]);
            }
            return result;
        }

        protected abstract F proxy();

        @SuppressWarnings("unchecked")
        protected Class<F> functor() {
            return (Class<F>) get(0);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        protected <R extends AbstractTermImpl<F>> R replace(Function<Object, Object> replacer) {
            Object[] array = toArray();
            boolean changed = false;
            for (int i = 0; i < array.length; i++) {
                Object old = array[i];
                if (array[i] instanceof AbstractTermImpl) {
                    array[i] = ((AbstractTermImpl) array[i]).replace(replacer);
                }
                array[i] = replacer.apply(array[i]);
                if (old != array[i]) {
                    changed = true;
                }
            }
            return (R) (changed ? term(array) : this);
        }

        protected abstract AbstractTermImpl<F> term(Object[] array);
    }

    @SuppressWarnings("rawtypes")
    private static final Object unproxy(Object object) {
        if (object instanceof Term) {
            return Proxy.getInvocationHandler(object);
        } else {
            return object;
        }
    }

    @SuppressWarnings("rawtypes")
    private static final TermImpl unproxy(Term object) {
        return (TermImpl) Proxy.getInvocationHandler(object);
    }

    // Variables

    public static interface Variable {
    }

    @SuppressWarnings("unchecked")
    public static <F> F var(Class<F> functor, String id) {
        return new VarImpl<F>(functor, id).proxy();
    }

    private static final class VarImpl<F> extends AbstractTermImpl<F> {
        private static final long serialVersionUID = -8998368070388908726L;

        private VarImpl(Class<F> functor, String name) {
            super(functor, name);
        }

        private VarImpl(Class<F> functor, Object name) {
            super(functor, name);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected final F proxy() {
            return (F) Proxy.newProxyInstance(functor().getClassLoader(), new Class[]{functor(), Variable.class}, this);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected VarImpl<F> term(Object[] array) {
            return new VarImpl<F>(functor(), array[1]);
        }
    }

    // Terms

    public static interface Term {
    }

    @SuppressWarnings("unchecked")
    public static <F> F term(Class<F> functor, Object... args) {
        return new TermImpl<F>(functor).proxy();
    }

    private static class TermImpl<F> extends AbstractTermImpl<F> implements Supplier<Boolean> {
        private static final long serialVersionUID = -1605559565948158856L;

        private TermImpl(Class<F> functor, Object... args) {
            super(functor, args);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected F proxy() {
            return (F) Proxy.newProxyInstance(functor().getClassLoader(), new Class[]{functor(), Term.class}, this);
        }

        @SuppressWarnings("rawtypes")
        protected final void makeFact() {
            FACTS.force(this, Set::add, this);
            patterns(1, toArray());
        }

        private void patterns(int i, Object[] array) {
            if (i < length()) {
                array = array.clone();
                if (array[i] == null) {
                    array[i] = get(i);
                    FACTS.force(term(array), Set::add, this);
                }
                patterns(i + 1, array);
                if (array[i] != null) {
                    array[i] = null;
                    FACTS.force(term(array), Set::add, this);
                }
                patterns(i + 1, array);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        protected TermImpl<F> term(Object[] array) {
            return new TermImpl<F>(functor(), Arrays.copyOfRange(array, 1, array.length));
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override
        public Boolean get() {
            Set<RuleImpl> rules = RULES.get(Pair.of(functor(), length() - 1));
            TermImpl pattern = replace(e -> e instanceof VarImpl ? null : e);
            Set<TermImpl> set = rules.isEmpty() || pattern == this ? FACTS.get(pattern) : Set.of();
            if (set.isEmpty()) {
                if (!rules.isEmpty()) {
                    List<TermImpl> pre = DERIVED.get();
                    if (pre.contains(pattern)) {
                        throw new CircularLogicException(pre, pattern);
                    } else {
                        Set<RuleImpl> bound = rules.map(r -> {
                            List<VarImpl> vars = r.term().variables();
                            return r.<RuleImpl> replace(e -> {
                                int i = e instanceof VarImpl ? vars.index(e) : -1;
                                return i >= 0 ? get(i + 1) : e;
                            });
                        }).asSet();
                        Boolean result = DERIVED.get(pre.prepend(pattern), () -> any(bound));
                        if (result) {
                            FACTS.force(this, Set::add, this);
                        }
                        return result;
                    }
                } else {
                    return Boolean.FALSE;
                }
            } else if (pattern != this) {
                throw new BindingsFoundException(set.map(b -> {
                    Map<VarImpl, Object> vs = Map.of();
                    for (int i = 0; i < length(); i++) {
                        if (get(i) instanceof VarImpl) {
                            vs = vs.put((VarImpl) get(i), b.get(i));
                        }
                    }
                    return vs;
                }).asSet());
            } else {
                return Boolean.TRUE;
            }
        }

        @SuppressWarnings("rawtypes")
        protected List<VarImpl> variables() {
            List<VarImpl> vars = List.of();
            for (int i = 0; i < length(); i++) {
                if (get(i) instanceof VarImpl) {
                    vars.add((VarImpl) get(i));
                }
            }
            return vars;
        }
    };

    // Rules

    public static interface Rule extends Term {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Rule rule(Term term, Term... goals) {
        RuleImpl ruleImpl = new RuleImpl(term, goal(goals));
        TermImpl termImpl = unproxy(term);
        RULES.force(Pair.of(termImpl.functor(), termImpl.length() - 1), Set::add, ruleImpl);
        return ruleImpl.proxy();
    }

    private static final class RuleImpl extends TermImpl<Rule> {
        private static final long serialVersionUID = -4602043866952049391L;

        private RuleImpl(Term term, Goal goal) {
            super(Rule.class, term, goal);
        }

        private RuleImpl(Object term, Object goal) {
            super(Rule.class, term, goal);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected final Rule proxy() {
            return (Rule) Proxy.newProxyInstance(functor().getClassLoader(), new Class[]{Rule.class}, this);
        }

        @SuppressWarnings("rawtypes")
        protected final TermImpl term() {
            return ((TermImpl) get(2));
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Boolean get() {
            return ((GoalImpl) get(2)).get();
        }

        @Override
        @SuppressWarnings("unchecked")
        protected RuleImpl term(Object[] array) {
            return new RuleImpl(array[1], array[2]);
        }
    }

    // Goals

    public static interface Goal extends Term {
    }

    public static boolean is(Term... goals) {
        return new GoalImpl(l(goals)).get();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Goal goal(Term... goals) {
        return new GoalImpl(l(goals)).proxy();
    }

    private static final class GoalImpl extends TermImpl<Goal> {
        private static final long serialVersionUID = -4100263206389367132L;

        private GoalImpl(L<Term> goals) {
            super(Goal.class, goals);
        }

        private GoalImpl(Object goals) {
            super(Goal.class, goals);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected final Goal proxy() {
            return (Goal) Proxy.newProxyInstance(functor().getClassLoader(), new Class[]{Goal.class}, this);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected GoalImpl term(Object[] array) {
            return new GoalImpl(array[1]);
        }

        @SuppressWarnings("rawtypes")
        @Override
        public Boolean get() {
            try {
                return all(goals());
            } catch (BindingsFoundException bve) {
                return any(bve.bindings.map(b -> replace(e -> {
                    if (e instanceof VarImpl) {
                        Object v = b.get((VarImpl) e);
                        if (v != null) {
                            return v;
                        }
                    }
                    return e;
                })));
            }
        }

        @SuppressWarnings("rawtypes")
        private Set<TermImpl<?>> goals() {
            Set<TermImpl<?>> set = Set.of();
            TermImpl ht = (TermImpl) get(1);
            while (ht.length() == 2) {
                set = set.add((TermImpl) ht.get(1));
                ht = (TermImpl) ht.get(2);
            }
            return set;
        }
    }

    // Lists

    public interface L<E> {
    }

    @SuppressWarnings("unchecked")
    public static <E> L<E> l(E head, L<E> tail) {
        return term(L.class, head, tail);
    }

    @SuppressWarnings("rawtypes")
    private static final L EMPTY_LIST = term(L.class);

    @SuppressWarnings("unchecked")
    public static <E> L<E> l(E... es) {
        L<E> l = EMPTY_LIST;
        for (int i = es.length - 1; i >= 0; i--) {
            l = l(es[i], l);
        }
        return l;
    }

    // Facts, Is

    public static void fact(Term term) {
        unproxy(term).makeFact();
    }

    // Any

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Boolean any(Collection<? extends Supplier<Boolean>> terms) {
        List<? extends Supplier<Boolean>> any = terms.random().asList();
        if (any.isEmpty()) {
            return false;
        } else if (any.size() == 1) {
            return any.get(0).get();
        } else {
            AtomicReference<RuntimeException> ref = new AtomicReference<>(null);
            boolean result = any.anyMatch(t -> {
                try {
                    return t.get();
                } catch (BindingsFoundException bve) {
                    ref.updateAndGet(rte -> {
                        if (rte instanceof BindingsFoundException) {
                            return ((BindingsFoundException) rte).merge(bve);
                        } else {
                            return rte == null || rte instanceof CircularLogicException ? bve : rte;
                        }
                    });
                    return false;
                } catch (CircularLogicException ce) {
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
    }

    // All

    private static Boolean all(Collection<? extends Supplier<Boolean>> terms) {
        List<? extends Supplier<Boolean>> all = terms.random().asList();
        if (all.isEmpty()) {
            return true;
        } else if (all.size() == 1) {
            return all.get(0).get();
        } else {
            AtomicReference<RuntimeException> ref = new AtomicReference<>(null);
            boolean result = all.allMatch(p -> {
                try {
                    return p.get();
                } catch (BindingsFoundException bve) {
                    ref.updateAndGet(rte -> {
                        if (rte instanceof BindingsFoundException) {
                            return ((BindingsFoundException) rte).merge(bve);
                        } else {
                            return rte == null || rte instanceof CircularLogicException ? bve : rte;
                        }
                    });
                    return true;
                } catch (CircularLogicException ce) {
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
    }

    // Runtime Exceptions

    public static final class BindingsFoundException extends RuntimeException {
        private static final long               serialVersionUID = 4505117271488648346L;

        @SuppressWarnings("rawtypes")
        private final Set<Map<VarImpl, Object>> bindings;

        @SuppressWarnings("rawtypes")
        private BindingsFoundException(Set<Map<VarImpl, Object>> bindings) {
            this.bindings = bindings;
        }

        private BindingsFoundException merge(BindingsFoundException other) {
            return new BindingsFoundException(bindings.addAll(other.bindings));
        }
    }

    @SuppressWarnings("rawtypes")
    public static final class CircularLogicException extends RuntimeException {
        private static final long    serialVersionUID = 293433487448006753L;

        private final List<TermImpl> derived;
        private final TermImpl       current;

        private CircularLogicException(List<TermImpl> derived, TermImpl current) {
            this.derived = derived;
            this.current = current;
        }

        @Override
        public String getMessage() {
            int i = derived.firstIndexOf(current);
            List<TermImpl> cycle = derived.sublist(0, i + 1).prepend(current);
            return "Circular Logic " + cycle.reverse().asList().toString().substring(4);
        }
    }

}
