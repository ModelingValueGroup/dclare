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
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Entry;
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
    private static final BiFunction<Set<TermImpl>, TermImpl, Set<TermImpl>>                   ADD_FACT   = (s, e) -> s == null ? Set.of(e) : s.add(e);

    private static final BiFunction<Set<RuleImpl>, RuleImpl, Set<RuleImpl>>                   ADD_RULE   = (s, e) -> s == null ? Set.of(e) : s.add(e);

    @SuppressWarnings("rawtypes")
    private static final Context<List<TermImpl>>                                              DERIVED    = Context.of(List.of());

    @SuppressWarnings("rawtypes")
    private static final Constant<TermImpl, Set<TermImpl>>                                    FACTS      = Constant.of("FACTS", null, CoreSetableModifier.durable);

    @SuppressWarnings("rawtypes")
    private static final Constant<Pair<Class, Integer>, Set<RuleImpl>>                        RULES      = Constant.of("RULES", null, CoreSetableModifier.durable);

    @SuppressWarnings("rawtypes")
    private static final Pair<Collection<TermImpl>, Collection<Supplier<String>>>             EMPTY      = Pair.of(Set.of(), Set.of());

    @SuppressWarnings("rawtypes")
    private static final Pair<Collection<Map<VarImpl, Object>>, Collection<Supplier<String>>> EMPTY_VARS = Pair.of(Set.of(), Set.of());

    private static abstract class ClauseImpl<F> extends StructImpl implements InvocationHandler {
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
                ClauseImpl ht = this;
                while (ht.length() == 3) {
                    list = list.prepend(ht.get(1));
                    ht = (ClauseImpl) ht.get(2);
                }
                return list.toString().substring(4);
            } else {
                String string = super.toString();
                return string.substring(1, string.length() - 1).replaceFirst(",", "(") + ")";
            }
        }

        protected ClauseImpl(Class<F> functor, Object... args) {
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

        protected abstract ClauseImpl<F> term(Object[] array);
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

    private static final class VarImpl<F> extends ClauseImpl<F> {
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
        public String toString() {
            return get(1).toString();
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
        return new TermImpl<F>(functor, args).proxy();
    }

    private static class TermImpl<F> extends ClauseImpl<F> {
        private static final long serialVersionUID = -1605559565948158856L;

        private TermImpl(Class<F> functor, Object... args) {
            super(functor, args);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected F proxy() {
            return (F) Proxy.newProxyInstance(functor().getClassLoader(), new Class[]{functor(), Term.class}, this);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected TermImpl<F> term(Object[] array) {
            return new TermImpl<F>((Class<F>) array[0], Arrays.copyOfRange(array, 1, array.length));
        }

        @SuppressWarnings("rawtypes")
        protected final void makeFact() {
            FACTS.force(this, ADD_FACT, this);
            patterns(1, toArray());
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
                    array[i] = null;
                    FACTS.force(term(array), ADD_FACT, this);
                }
                patterns(i + 1, array);
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        protected Set<VarImpl> variables() {
            Set<VarImpl> vars = Set.of();
            for (int i = 0; i < length(); i++) {
                if (get(i) instanceof VarImpl) {
                    vars = vars.add((VarImpl) get(i));
                }
            }
            return vars;
        }

        @SuppressWarnings("rawtypes")
        protected Map<VarImpl, Object> getBinding(TermImpl term) {
            Map<VarImpl, Object> vars = Map.of();
            for (int i = 1; i < length(); i++) {
                if (get(i) instanceof VarImpl) {
                    vars = vars.put((VarImpl) get(i), term.get(i));
                }
            }
            return vars;
        }

        @SuppressWarnings("rawtypes")
        protected TermImpl<F> setBinding(Map<VarImpl, Object> vars) {
            Object[] array = toArray();
            for (int i = 1; i < length(); i++) {
                if (get(i) instanceof VarImpl) {
                    array[i] = vars.get((VarImpl) get(i));
                }
            }
            return term(array);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        protected Pair<Collection<TermImpl>, Collection<Supplier<String>>> match() {
            Set<TermImpl> facts = FACTS.get(this);
            if (facts == null) {
                Set<RuleImpl> rules = RULES.get(Pair.of(functor(), length() - 1));
                if (rules != null) {
                    List<TermImpl> pre = DERIVED.get();
                    if (pre.contains(this)) {
                        return Pair.of(Set.of(), Set.of(() -> {
                            int i = pre.firstIndexOf(this);
                            List<TermImpl> cycle = pre.sublist(0, i + 1).prepend(this);
                            return "Circular Logic " + cycle.reverse().asList().toString().substring(4);
                        }));
                    } else {
                        List<TermImpl> post = pre.prepend(this);
                        Pair<Collection<TermImpl>, Collection<Supplier<String>>> result = DERIVED.get(post, () -> {
                            Pair<Collection<TermImpl>, Collection<Supplier<String>>> r = EMPTY;
                            for (RuleImpl rule : rules) {
                                Pair<Collection<TermImpl>, Collection<Supplier<String>>> e = rule.eval(this);
                                r = Pair.of(Collection.concat(r.a(), e.a()), Collection.concat(r.b(), e.b()));
                            }
                            return r;
                        });
                        if (result.b().isEmpty()) {
                            facts = result.a().asSet();
                            FACTS.force(this, facts);
                            return Pair.of(facts, Set.of());
                        } else {
                            return result;
                        }
                    }
                } else {
                    return EMPTY;
                }
            } else {
                return Pair.of(facts, Set.of());
            }
        }
    };

    // Rules

    public static interface Rule extends Term {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Rule rule(Term term, Term... goals) {
        RuleImpl ruleImpl = new RuleImpl(term, goal(goals));
        TermImpl termImpl = unproxy(term);
        RULES.force(Pair.of(termImpl.functor(), termImpl.length() - 1), ADD_RULE, ruleImpl);
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
            return ((TermImpl) get(1));
        }

        @SuppressWarnings("rawtypes")
        protected final GoalImpl goal() {
            return ((GoalImpl) get(2));
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        protected Pair<Collection<TermImpl>, Collection<Supplier<String>>> eval(TermImpl ptrn) {
            TermImpl head = term();
            Pair<Collection<Map<VarImpl, Object>>, Collection<Supplier<String>>> result = goal().eval(head.getBinding(ptrn));
            return Pair.of(result.a().map(m -> head.setBinding(m)), result.b());
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
        @SuppressWarnings("rawtypes")
        Pair<Collection<Map<VarImpl, Object>>, Collection<Supplier<String>>> result = new GoalImpl(l(goals)).eval();
        return result.b().isEmpty() && !result.a().isEmpty();
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
        public Pair<Collection<Map<VarImpl, Object>>, Collection<Supplier<String>>> eval() {
            return eval(variables().asMap(v -> Entry.of(v, null)));
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        protected Pair<Collection<Map<VarImpl, Object>>, Collection<Supplier<String>>> eval(Map<VarImpl, Object> vars) {
            Pair<Collection<Map<VarImpl, Object>>, Collection<Supplier<String>>> r = EMPTY_VARS;
            TermImpl list = (TermImpl) get(1);
            while (list.length() == 3) {
                TermImpl goal = (TermImpl) list.get(1);
                Pair<Collection<TermImpl>, Collection<Supplier<String>>> e = goal.setBinding(vars).match();
                r = Pair.of(Collection.concat(r.a(), e.a().map(m -> goal.getBinding(m))), Collection.concat(r.b(), e.b()));
                list = (TermImpl) list.get(2);
            }
            return r;
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

}
