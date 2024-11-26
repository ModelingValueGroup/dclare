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

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.struct.impl.StructImpl;

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

        protected ClauseImpl(Functor<F> functor, Object... args) {
            super(unproxy(functor, args));
        }

        protected ClauseImpl(FunctImpl<F> functor, Object... args) {
            super(unproxy(functor, args));
        }

        protected ClauseImpl(Class<F> type, Object... args) {
            super(unproxy(type, args));
        }

        @SuppressWarnings("rawtypes")
        private static final Object[] unproxy(Object functor, Object[] args) {
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

    @SuppressWarnings("rawtypes")
    private static final Object unproxy(Object object) {
        if (object instanceof Term) {
            return Proxy.getInvocationHandler(object);
        } else {
            return object;
        }
    }

    @SuppressWarnings("unchecked")
    private static final <T extends Term> TermImpl<T> unproxy(T object) {
        return (TermImpl<T>) Proxy.getInvocationHandler(object);
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

    private static <T> FunctImpl<T> functImpl(Class<T> type, String name, int arity) {
        return new FunctImpl<T>(type, name, arity);
    }

    public static <T> Functor<T> functor(Class<T> type, String name, int arity) {
        return functImpl(type, name, arity).proxy();
    }

    private static final class FunctImpl<T> extends ClauseImpl<Functor<T>> {
        private static final long serialVersionUID = 285147889847599160L;

        @SuppressWarnings({"unchecked", "rawtypes"})
        private FunctImpl(Class<T> type, String name, int arity) {
            super((Class) Functor.class, type, name, arity);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected final Functor<T> proxy() {
            return (Functor<T>) Proxy.newProxyInstance(type().getClassLoader(), new Class[]{Functor.class}, this);
        }

        @Override
        public String toString() {
            return ((String) get(2));
        }

        @Override
        @SuppressWarnings("unchecked")
        protected FunctImpl<T> term(Object[] array) {
            return new FunctImpl<T>((Class<T>) array[1], (String) array[2], (Integer) array[2]);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Class<Functor<T>> type() {
            return (Class<Functor<T>>) get(0);
        }

        @SuppressWarnings("unchecked")
        protected Class<T> functType() {
            return (Class<T>) get(1);
        }
    }

    // Variables

    public static interface Variable {
    }

    @SuppressWarnings("unchecked")
    public static <F> F var(Class<F> type, String id) {
        return new VarImpl<F>(type, id).proxy();
    }

    private static final class VarImpl<F> extends ClauseImpl<F> {
        private static final long serialVersionUID = -8998368070388908726L;

        private VarImpl(Class<F> type, String name) {
            super(type, name);
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
            return new VarImpl<F>(type(), (String) array[1]);
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
    public static <F> F term(Functor<F> functor, Object... args) {
        return new TermImpl<F>(functor, args).proxy();
    }

    private static <F> TermImpl<F> termImpl(FunctImpl<F> functor, Object... args) {
        return new TermImpl<F>(functor, args);
    }

    private static class TermImpl<F> extends ClauseImpl<F> {
        private static final long serialVersionUID = -1605559565948158856L;

        private TermImpl(Functor<F> functor, Object... args) {
            super(functor, args);
        }

        private TermImpl(FunctImpl<F> functor, Object... args) {
            super(functor, args);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected F proxy() {
            return (F) Proxy.newProxyInstance(type().getClassLoader(), new Class[]{type(), Term.class}, this);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected TermImpl<F> term(Object[] array) {
            return new TermImpl<F>((FunctImpl<F>) array[0], Arrays.copyOfRange(array, 1, array.length));
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

        @SuppressWarnings("unchecked")
        @Override
        protected Class<F> type() {
            return functor().functType();
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        protected FunctImpl<F> functor() {
            return (FunctImpl<F>) get(0);
        }

        @SuppressWarnings("rawtypes")
        protected final void makeFact() {
            FACTS.force(this, ADD_FACT, this);
            patterns(1, toArray(), 2);
        }

        private void patterns(int i, Object[] array, int nrOfNulls) {
            if (i < length()) {
                array = array.clone();
                if (array[i] == null) {
                    array[i] = get(i);
                    FACTS.force(term(array), ADD_FACT, this);
                }
                patterns(i + 1, array, nrOfNulls);
                if (array[i] != null) {
                    array[i] = null;
                    if (USE_EXTEND || nrOfNulls < array.length) {
                        FACTS.force(term(array), ADD_FACT, this);
                    }
                }
                patterns(i + 1, array, nrOfNulls + 1);
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        protected Map<VarImpl, Object> variables() {
            Map<VarImpl, Object> vars = Map.of();
            for (int i = 1; i < length(); i++) {
                if (get(i) instanceof VarImpl) {
                    vars = vars.put((VarImpl) get(i), null);
                } else if (get(i) instanceof TermImpl) {
                    vars = vars.putAll(((TermImpl) get(i)).variables());
                }
            }
            return vars;
        }

        @SuppressWarnings("rawtypes")
        protected Map<VarImpl, Object> getBinding(TermImpl term) {
            if (term.type() == Incomplete.class) {
                return Map.of(Entry.of(INCOMPLETE_VAR, term));
            } else {
                Map<VarImpl, Object> vars = Map.of();
                for (int i = 1; i < length(); i++) {
                    if (get(i) instanceof VarImpl) {
                        vars = vars.put((VarImpl) get(i), term.get(i));
                    }
                }
                return vars;
            }
        }

        @SuppressWarnings("rawtypes")
        protected TermImpl setBinding(Map<VarImpl, Object> vars) {
            TermImpl inc = (TermImpl) vars.get(INCOMPLETE_VAR);
            if (inc != null) {
                return inc;
            } else {
                Object[] array = toArray();
                for (int i = 1; i < length(); i++) {
                    if (get(i) instanceof VarImpl) {
                        array[i] = vars.get((VarImpl) get(i));
                    }
                }
                return term(array);
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        protected Collection<TermImpl> match(List<TermImpl> der) {
            int non = nrOfNulls();
            if (!USE_EXTEND && non == length() - 1) {
                return Set.of(incompl(der.append(this)));
            } else {
                Set<TermImpl> facts = FACTS.get(this);
                if (facts == null) {
                    List<RuleImpl> rules = RULES.get(functor());
                    if (rules != null) {
                        int i = der.lastIndexOf(this);
                        if (i >= 0) {
                            return Set.of(incompl(der.sublist(i, der.size()).append(this)));
                        } else {
                            Collection<TermImpl> r = Set.of();
                            for (RuleImpl rule : rules) {
                                Collection<TermImpl> eval = rule.eval(this, der.append(this));
                                if (non == 0) {
                                    eval = eval.asSet();
                                    if (eval.equals(Set.of(this))) {
                                        return eval;
                                    }
                                }
                                r = Collection.concat(r, eval);
                            }
                            if (non < 2 && non < length() - 1) {
                                Set<TermImpl> set = r.asSet();
                                FACTS.force(this, set);
                                return set;
                            } else {
                                return r;
                            }
                        }
                    } else {
                        return Set.of();
                    }
                } else {
                    return facts;
                }
            }
        }

        @SuppressWarnings("rawtypes")
        protected int termPrio(List<TermImpl> der) {
            int non = nrOfNulls();
            if (!USE_EXTEND && non == length() - 1) {
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
                if (get(i) == null) {
                    nr++;
                }
            }
            return nr;
        }
    };

    // Rules

    public static interface Rule extends Term {
    }

    private static final FunctImpl<Rule> RULE_FUNCTOR = functImpl(Rule.class, "rule", 2);

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Rule rule(Term term, Term... goals) {
        RuleImpl ruleImpl = new RuleImpl(term, goal(goals));
        TermImpl termImpl = unproxy(term);
        RULES.force(termImpl.functor(), ADD_RULE, ruleImpl);
        return ruleImpl.proxy();
    }

    private static final class RuleImpl extends TermImpl<Rule> {
        private static final long serialVersionUID = -4602043866952049391L;

        private RuleImpl(Term term, Goal goal) {
            super(RULE_FUNCTOR, term, goal);
        }

        private RuleImpl(Object term, Object goal) {
            super(RULE_FUNCTOR, term, goal);
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
        protected Collection<TermImpl> eval(TermImpl ptrn, List<TermImpl> der) {
            TermImpl head = term();
            Collection<Map<VarImpl, Object>> r = goal().eval(variables().putAll(head.getBinding(ptrn)), der);
            return r.map(m -> head.setBinding(m));
        }

        @Override
        @SuppressWarnings("unchecked")
        protected RuleImpl term(Object[] array) {
            return new RuleImpl(array[1], array[2]);
        }

        protected int rulePrio() {
            return goal().goals().size();
        }
    }

    // Goals

    public static interface Goal extends Term {
    }

    private static final FunctImpl<Goal> GOAL_FUNCTOR = functImpl(Goal.class, "goal", 1);

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

    private static final class GoalImpl extends TermImpl<Goal> {
        private static final long serialVersionUID = -4100263206389367132L;

        private GoalImpl(L<Term> goals) {
            super(GOAL_FUNCTOR, goals);
        }

        private GoalImpl(Object goals) {
            super(GOAL_FUNCTOR, goals);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected final Goal proxy() {
            return (Goal) Proxy.newProxyInstance(type().getClassLoader(), new Class[]{Goal.class}, this);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected GoalImpl term(Object[] array) {
            return new GoalImpl(array[1]);
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
                vars = vars.asSet();
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
                        return eval(goals.removeIndex(i), m.map(t -> {
                            Map<VarImpl, Object> b = g.getBinding(t);
                            return b.containsKey(INCOMPLETE_VAR) ? b : v.putAll(b);
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

    private static final FunctImpl<Incomplete> INCOMPLETE_FUNCTOR = functImpl(Incomplete.class, "incomplete", 1);

    private static final VarImpl<Incomplete>   INCOMPLETE_VAR     = new VarImpl<Incomplete>(Incomplete.class, "Incomplete");

    @SuppressWarnings("unchecked")
    public static Incomplete incompleteVar() {
        return INCOMPLETE_VAR.proxy();
    }

    public static Map<Variable, Object> incomplete(Term... der) {
        return Map.of(Entry.of((Variable) incompleteVar(), incompl(list(der)).proxy()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static TermImpl<Incomplete> incompl(List<TermImpl> der) {
        return incompl(list(der));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static TermImpl<Incomplete> incompl(TermImpl der) {
        return termImpl(INCOMPLETE_FUNCTOR, der);
    }

    // Lists

    public interface L<E> extends Term {
    }

    @SuppressWarnings("rawtypes")
    private static final FunctImpl<L> LIST_FUNCTOR_0 = functImpl(L.class, "l", 0);
    @SuppressWarnings("rawtypes")
    private static final FunctImpl<L> LIST_FUNCTOR_2 = functImpl(L.class, "l", 2);

    @SuppressWarnings("unchecked")
    public static <E> L<E> l(E head, L<E> tail) {
        return termImpl(LIST_FUNCTOR_2, head, tail).proxy();
    }

    @SuppressWarnings("rawtypes")
    private static final TermImpl<L> EMPTY_LIST = termImpl(LIST_FUNCTOR_0);

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

    // Facts, Is

    public static void fact(Term term) {
        unproxy(term).makeFact();
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

}
