//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2025 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.modelingvalue.collections.ContainingCollection;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Context;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.Construction.Reason;
import org.modelingvalue.dclare.ex.TransactionException;

public abstract class AbstractDerivationTransaction extends ReadOnlyTransaction {
    @SuppressWarnings("rawtypes")
    protected static final Context<Set<Derived>>            ALL_DERIVED = Context.of(Set.of());
    @SuppressWarnings("rawtypes")
    protected static final Context<Pair<Mutable, Observer>> DERIVER     = Context.of(null);
    @SuppressWarnings("rawtypes")
    private static final Context<Derived>                   DERIVED     = Context.of(null);
    private static final Context<Integer>                   INDENT      = Context.of(0);
    private static final Context<Boolean>                   DERIVE      = Context.of(true);

    public static boolean isDeriving() {
        return !ALL_DERIVED.get().isEmpty();
    }

    protected AbstractDerivationTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    private ConstantState    memoization;
    private ILeafTransaction iLeafTransaction;

    public <R> R derive(Supplier<R> action, State state, ConstantState memoization, ILeafTransaction iLeafTransaction) {
        this.memoization = memoization;
        this.iLeafTransaction = iLeafTransaction;
        try {
            return get(action, state);
        } catch (Throwable t) {
            universeTransaction().handleException(t);
            return null;
        } finally {
            this.memoization = null;
            this.iLeafTransaction = null;
        }
    }

    @SuppressWarnings("rawtypes")
    protected <O, T> boolean doDeriveGet(O object, Getable<O, T> getable, T nonDerived) {
        return object instanceof Mutable && getable instanceof Observed && DERIVE.get();
    }

    @SuppressWarnings("rawtypes")
    protected <O, T> boolean doDeriveSet(O object, Getable<O, T> getable, T nonDerived) {
        return object instanceof Mutable && getable instanceof Observed && isDeriving();
    }

    protected <O, T> T getNonDerived(O object, Getable<O, T> getable) {
        return super.current(object, getable);
    }

    @Override
    public <O, T> T get(O object, Getable<O, T> getable) {
        T nonDerived = getNonDerived(object, getable);
        return derive(object, getable, nonDerived);
    }

    @Override
    protected <O, T> T current(O object, Getable<O, T> getable) {
        T nonDerived = super.current(object, getable);
        return derive(object, getable, nonDerived);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <O, T> T derive(O object, Getable<O, T> getable, T nonDerived) {
        if (doDeriveGet(object, getable, nonDerived)) {
            Observed<O, T> observed = (Observed<O, T>) getable;
            Derived<O, T> outerDerived = DERIVED.get();
            boolean isDerived = outerDerived != null && outerDerived.isDerived(object, observed);
            if (isDerived && outerDerived.isSet()) {
                return outerDerived.get();
            } else {
                ConstantState mem = memoization(object);
                Constant<O, T> constant = observed.constant();
                if (!mem.isSet(iLeafTransaction, object, constant)) {
                    if (isDerived || Newable.D_ALL_DERIVATIONS.equals(observed) || Mutable.D_PARENT_CONTAINING.equals(observed)) {
                        return nonDerived;
                    } else {
                        Derived<O, T> innerDerived = new Derived(object, observed, outerDerived);
                        Set<Derived> oldAllDerived = ALL_DERIVED.get();
                        if (oldAllDerived.contains(innerDerived)) {
                            if (pull() && !observed.synthetic()) {
                                runSilent(() -> System.err.println(tracePre(object, null) + "CYCLE " + innerDerived));
                            }
                            if (isTraceDerivation(object, observed)) {
                                runSilent(() -> System.err.println(tracePre(object, this) + "RECU " + object + "." + observed + " => RECURSIVE DERIVATION, result is the non-derived value: " + nonDerived));
                            }
                            return nonDerived;
                        } else {
                            if (isTraceDerivation(object, observed)) {
                                runSilent(() -> System.err.println(tracePre(object, this) + ">>>> " + object + "." + observed));
                            }
                            INDENT.run(INDENT.get() + 1, () -> ALL_DERIVED.run(oldAllDerived.add(innerDerived), () -> DERIVED.run(innerDerived, () -> {
                                int i = 0;
                                for (Observer observer : ((Mutable) object).dAllDerivers(observed)) {
                                    runDeriver((Mutable) object, observed, observer, ++i);
                                }
                                if (innerDerived.isSet()) {
                                    setInMemoization(mem, object, observed, innerDerived.get(), false);
                                }
                            })));
                            if (!mem.isSet(iLeafTransaction, object, constant)) {
                                if (isTraceDerivation(object, observed)) {
                                    INDENT.run(INDENT.get() + 1, () -> runSilent(() -> System.err.println(tracePre(object, this) + "NODR " + object + "." + observed + " => NO DERIVATION, result is the non-derived value: " + nonDerived)));
                                }
                                return nonDerived;
                            }
                        }
                    }
                }
                return mem.get(iLeafTransaction, object, constant);
            }
        } else {
            return nonDerived;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void runDeriver(Mutable mutable, Observed observed, Observer observer, int i) {
        if (isTraceDerivation(mutable, observed)) {
            runSilent(() -> System.err.println(tracePre(mutable, this) + String.format(">>%d> ", i) + mutable + "." + observer + "()"));
        }
        INDENT.run(INDENT.get() + 1, () -> DERIVER.run(Pair.of(mutable, observer), () -> {
            try {
                observer.run(mutable);
            } catch (Throwable t) {
                if (isTraceDerivation(mutable, observed)) {
                    runSilent(() -> System.err.println(tracePre(mutable, this) + "!!!! " + mutable + "." + observer + "() => THROWS " + t));
                }
                handleException(mutable, observer, t);
            }
        }));

    }

    @SuppressWarnings("rawtypes")
    protected void handleException(Mutable mutable, Observer observer, Throwable t) {
        universeTransaction().handleException(new TransactionException(mutable, new TransactionException(observer, t)));
    }

    @Override
    public <O, T, E> T set(O object, Setable<O, T> setable, BiFunction<T, E, T> function, E element) {
        T nonDerived = getNonDerived(object, setable);
        return set(object, setable, function.apply(nonDerived, element), nonDerived);
    }

    @Override
    public <O, T> T set(O object, Setable<O, T> setable, UnaryOperator<T> oper) {
        T nonDerived = getNonDerived(object, setable);
        return set(object, setable, oper.apply(nonDerived), nonDerived);
    }

    @Override
    public <O, T> T set(O object, Setable<O, T> setable, T post) {
        T nonDerived = getNonDerived(object, setable);
        return set(object, setable, post, nonDerived);
    }

    @Override
    public void runSilent(Runnable action) {
        DERIVE.run(false, action);
    }

    @Override
    public <R> R getSilent(Supplier<R> action) {
        return DERIVE.get(false, action);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <O, T> T set(O object, Setable<O, T> setable, T post, T nonDerived) {
        if (doDeriveSet(object, setable, nonDerived)) {
            Observed<O, T> observed = (Observed<O, T>) setable;
            Derived<O, T> derived = DERIVED.get();
            boolean isDerived = derived != null && derived.isDerived(object, observed);
            ConstantState mem = memoization(object);
            Constant<O, T> constant = observed.constant();
            T pre = isDerived && derived.isSet() ? derived.get() : mem.isSet(iLeafTransaction, object, constant) ? mem.get(iLeafTransaction, object, constant) : nonDerived;
            T result = match(mem, observed, pre, post);
            if (isDerived && derived.isSet() && !Objects.equals(pre, nonDerived) && Objects.equals(result, nonDerived)) {
                return post;
            }
            if (isDerived) {
                derived.set(result);
            } else {
                setInMemoization(mem, object, observed, result, false);
            }
            if (isTraceDerivation(object, observed)) {
                runSilent(() -> {
                    Pair<Mutable, Observer> deriver = DERIVER.get();
                    if (deriver != null) {
                        System.err.println(tracePre(object, this) + "SET  " + deriver.a() + "." + deriver.b() + "(" + object + "." + observed + "=" + pre + "->" + result + ")");
                    } else {
                        System.err.println(tracePre(object, this) + "SET  (" + object + "." + observed + "=" + pre + "->" + result + ")");
                    }
                });
            }
            if (observed.containment()) {
                Setable.<T, Mutable> diff(pre, result, added -> {
                    setInMemoization(mem, added, Mutable.D_PARENT_CONTAINING, Pair.of((Mutable) object, (Setable<Mutable, ?>) observed), true);
                }, removed -> {
                });
            }
            return pre;
        } else if (!Objects.equals(nonDerived, post)) {
            return iLeafTransaction == this ? super.set(object, setable, post) : iLeafTransaction.set(object, setable, post);
        } else {
            return post;
        }
    }

    private <T, O> T match(ConstantState mem, Setable<O, T> setable, T pre, T post) {
        List<Newable> posts = setable.collection(post).filter(Newable.class).distinct().asList();
        if (!posts.isEmpty()) {
            List<Newable> pres = setable.collection(pre).filter(Newable.class).exclude(posts::contains).distinct().asList();
            if (!pres.isEmpty()) {
                for (Newable po : posts) {
                    Construction poInit = Mutable.D_INITIAL_CONSTRUCTION.get(po);
                    if (poInit.isDerived() && mem.isSet(iLeafTransaction, po, Newable.D_ALL_DERIVATIONS.constant())) {
                        for (Newable pr : pres) {
                            Construction preInit = Mutable.D_INITIAL_CONSTRUCTION.get(pr);
                            if (preInit.isDirect() && po.dNewableType().equals(pr.dNewableType()) && Objects.equals(po.dIdentity(), pr.dIdentity())) {
                                pres = pres.remove(pr);
                                post = replace(post, po, pr);
                                setInMemoization(mem, pr, Mutable.D_ALL_DERIVATIONS, mem.get(iLeafTransaction, po, Newable.D_ALL_DERIVATIONS.constant()), true);
                            }
                        }
                    } else if (poInit.isDirect()) {
                        for (Newable pr : pres) {
                            Construction preInit = Mutable.D_INITIAL_CONSTRUCTION.get(pr);
                            if (preInit.isDerived() && mem.isSet(iLeafTransaction, pr, Newable.D_ALL_DERIVATIONS.constant()) && po.dNewableType().equals(pr.dNewableType()) && Objects.equals(po.dIdentity(), pr.dIdentity())) {
                                pres = pres.remove(pr);
                                setInMemoization(mem, po, Mutable.D_ALL_DERIVATIONS, mem.get(iLeafTransaction, pr, Newable.D_ALL_DERIVATIONS.constant()), true);
                            }
                        }
                    }
                }
            }
        }
        return post;
    }

    @SuppressWarnings("unchecked")
    private <T> T replace(T post, Newable po, Newable pr) {
        if (post instanceof ContainingCollection) {
            post = (T) ((ContainingCollection<Newable>) post).replace(po, pr);
        } else if (post.equals(po)) {
            post = (T) pr;
        }
        return post;
    }

    @Override
    public <O extends Mutable> void trigger(O mutable, Action<O> action, Priority priority) {
        iLeafTransaction.trigger(mutable, action, priority);
    }

    @Override
    public <O extends Newable> O directConstruct(Construction.Reason reason, Supplier<O> supplier) {
        return super.construct(reason, supplier);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <O extends Mutable> O construct(Reason reason, Supplier<O> supplier) {
        Pair<Mutable, Observer> deriver = DERIVER.get();
        O result = supplier.get();
        Construction cons = Construction.of(deriver.a(), deriver.b(), reason);
        setInMemoization(memoization(deriver.a()), result, Newable.D_ALL_DERIVATIONS, Newable.D_ALL_DERIVATIONS.getDefault(result).add(cons), true);
        Mutable.D_INITIAL_CONSTRUCTION.force(result, cons);
        return result;
    }

    protected <T, O> T setInMemoization(ConstantState mem, O object, Setable<O, T> setable, T result, boolean force) {
        return force ? mem.set(iLeafTransaction, object, setable.constant(), result, force) : mem.getOrSet(iLeafTransaction, object, setable.constant(), result);
    }

    protected <O> ConstantState memoization(O object) {
        return object instanceof Mutable ? ((Mutable) object).dMemoization(this) : memoization();
    }

    public <O> ConstantState memoization() {
        return memoization;
    }

    @SuppressWarnings("rawtypes")
    protected <O> boolean isTraceDerivation(O object, Setable setable) {
        return (setable == null || (!setable.isPlumbing() && !setable.synthetic())) && universeTransaction().getConfig().isTraceDerivation();
    }

    private <O> String tracePre(O object, Transaction transaction) {
        return DclareTrace.getLineStart(memoization(object).toString(), transaction);
    }

    @Override
    public int depth() {
        return INDENT.get();
    }

    protected static class Derived<O, T> extends Pair<O, Observed<O, T>> {
        private static final long        serialVersionUID = -2566539820227398813L;

        @SuppressWarnings("rawtypes")
        private final Derived<?, ?>      outer;
        private final AtomicReference<T> value;

        @SuppressWarnings("rawtypes")
        protected Derived(O a, Observed<O, T> b, Derived<?, ?> outer) {
            super(a, b);
            this.outer = outer;
            this.value = new AtomicReference<>();
        }

        protected T get() {
            T value = this.value.get();
            return value == ConstantState.NULL ? null : value;
        }

        @SuppressWarnings("unchecked")
        protected void set(T value) {
            this.value.set(value == null ? (T) ConstantState.NULL : value);
        }

        protected boolean isSet() {
            return this.value.get() != null;
        }

        protected boolean isDerived(Object object, Observed<?, ?> observed) {
            return a().equals(object) && b().equals(observed);
        }

        @SuppressWarnings("rawtypes")
        protected Derived outer() {
            return outer;
        }

        @SuppressWarnings("rawtypes")
        protected Derived outerNotSynthetic() {
            return outer != null && outer.b().synthetic() ? outer.outerNotSynthetic() : outer;
        }

        @Override
        public String toString() {
            return toString(this);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private String toString(Derived<?, ?> deepest) {
            Derived ons = outerNotSynthetic();
            return (ons != null && !ons.isDerived(deepest.a(), deepest.b()) ? ons.toString(deepest) : "") + "[" + a() + "." + b() + "]";
        }

    }

}
