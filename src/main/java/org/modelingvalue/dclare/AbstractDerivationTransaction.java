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

import java.util.Objects;
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
    protected static final Context<Set<Pair<Mutable, Observed>>> DERIVED       = Context.of(Set.of());
    @SuppressWarnings("rawtypes")
    protected static final Context<Pair<Mutable, Observer>>      DERIVER       = Context.of(null);
    @SuppressWarnings("rawtypes")
    private static final Context<DerivedValue>                   DERIVED_VALUE = Context.of(null);
    private static final Context<Integer>                        INDENT        = Context.of(0);
    private static final Context<Boolean>                        DERIVE        = Context.of(true);

    public static boolean isDeriving() {
        return !DERIVED.get().isEmpty();
    }

    protected AbstractDerivationTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    private ConstantState         memoization;
    private ILeafTransaction changeHandler;

    public <R> R derive(Supplier<R> action, State state, ConstantState memoization, ILeafTransaction changeHandler) {
        this.memoization = memoization;
        this.changeHandler = changeHandler;
        try {
            return get(action, state);
        } catch (Throwable t) {
            universeTransaction().handleException(t);
            return null;
        } finally {
            this.memoization = null;
            this.changeHandler = null;
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
            DerivedValue<O, T> outerDerivedValue = DERIVED_VALUE.get();
            boolean isDerived = outerDerivedValue != null && outerDerivedValue.isDerived(object, observed);
            ConstantState mem = memoization(object);
            Constant<O, T> constant = observed.constant();
            if (isDerived && outerDerivedValue.isSet()) {
                return outerDerivedValue.get();
            } else if (!mem.isSet(changeHandler, object, constant)) {
                if (Newable.D_ALL_DERIVATIONS.equals(observed) || Mutable.D_PARENT_CONTAINING.equals(observed)) {
                    return nonDerived;
                } else {
                    Pair<Mutable, Observed> derived = Pair.of((Mutable) object, observed);
                    Set<Pair<Mutable, Observed>> oldDerived = DERIVED.get();
                    Set<Pair<Mutable, Observed>> newDerived = oldDerived.add(derived);
                    if (oldDerived == newDerived) {
                        if (isTraceDerivation(object, observed)) {
                            runNonDeriving(() -> System.err.println(tracePre(object) + "RECU " + object + "." + observed + " => RECURSIVE DERIVATION, result is the non-derived value: " + nonDerived));
                        }
                        return nonDerived;
                    } else {
                        if (isTraceDerivation(object, observed)) {
                            runNonDeriving(() -> System.err.println(tracePre(object) + ">>>> " + object + "." + observed));
                        }
                        DerivedValue<O, T> innerDerivedValue = new DerivedValue(object, observed);
                        INDENT.run(INDENT.get() + 1, () -> DERIVED.run(newDerived, () -> DERIVED_VALUE.run(innerDerivedValue, () -> {
                            int i = 0;
                            for (Observer observer : ((Mutable) object).dAllDerivers(observed)) {
                                runDeriver((Mutable) object, observed, observer, ++i);
                            }
                            if (innerDerivedValue.isSet()) {
                                setInMemoization(mem, object, observed, innerDerivedValue.get(), false);
                            }
                        })));
                        if (!mem.isSet(changeHandler, object, constant)) {
                            if (isTraceDerivation(object, observed)) {
                                INDENT.run(INDENT.get() + 1, () -> runNonDeriving(() -> System.err.println(tracePre(object) + "NODR " + object + "." + observed + " => NO DERIVATION, result is the non-derived value: " + nonDerived)));
                            }
                            return nonDerived;
                        }
                    }
                }
            }
            return mem.get(changeHandler, object, constant);
        } else {
            return nonDerived;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void runDeriver(Mutable mutable, Observed observed, Observer observer, int i) {
        if (isTraceDerivation(mutable, observed)) {
            runNonDeriving(() -> System.err.println(tracePre(mutable) + String.format(">>%d> ", i) + mutable + "." + observer + "()"));
        }
        INDENT.run(INDENT.get() + 1, () -> DERIVER.run(Pair.of(mutable, observer), () -> {
            try {
                observer.run(mutable);
            } catch (Throwable t) {
                if (isTraceDerivation(mutable, observed)) {
                    runNonDeriving(() -> System.err.println(tracePre(mutable) + "!!!! " + mutable + "." + observer + "() => THROWS " + t));
                }
                universeTransaction().handleException(new TransactionException(mutable, new TransactionException(observer, t)));
            }
        }));

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

    public void runNonDeriving(Runnable action) {
        DERIVE.run(false, action);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <O, T> T set(O object, Setable<O, T> setable, T post, T nonDerived) {
        if (doDeriveSet(object, setable, nonDerived)) {
            Observed<O, T> observed = (Observed<O, T>) setable;
            DerivedValue<O, T> derivedValue = DERIVED_VALUE.get();
            boolean isDerived = derivedValue != null && derivedValue.isDerived(object, observed);
            ConstantState mem = memoization(object);
            Constant<O, T> constant = observed.constant();
            T pre = isDerived && derivedValue.isSet() ? derivedValue.get() : mem.isSet(changeHandler, object, constant) ? mem.get(changeHandler, object, constant) : nonDerived;
            T result = match(mem, observed, pre, post);
            if (isDerived) {
                derivedValue.set(result);
            } else {
                setInMemoization(mem, object, observed, result, false);
            }
            if (isTraceDerivation(object, observed)) {
                runNonDeriving(() -> {
                    Pair<Mutable, Observer> deriver = DERIVER.get();
                    System.err.println(tracePre(object) + "SET  " + deriver.a() + "." + deriver.b() + "(" + object + "." + observed + "=" + pre + "->" + result + ")");
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
            return changeHandler == this ? super.set(object, setable, post) : changeHandler.set(object, setable, post);
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
                    if (poInit.isDerived() && mem.isSet(changeHandler, po, Newable.D_ALL_DERIVATIONS.constant())) {
                        for (Newable pr : pres) {
                            Construction preInit = Mutable.D_INITIAL_CONSTRUCTION.get(pr);
                            if (preInit.isDirect() && po.dNewableType().equals(pr.dNewableType()) && Objects.equals(po.dIdentity(), pr.dIdentity())) {
                                pres = pres.remove(pr);
                                post = replace(post, po, pr);
                                setInMemoization(mem, pr, Mutable.D_ALL_DERIVATIONS, mem.get(changeHandler, po, Newable.D_ALL_DERIVATIONS.constant()), true);
                            }
                        }
                    } else if (poInit.isDirect()) {
                        for (Newable pr : pres) {
                            Construction preInit = Mutable.D_INITIAL_CONSTRUCTION.get(pr);
                            if (preInit.isDerived() && mem.isSet(changeHandler, pr, Newable.D_ALL_DERIVATIONS.constant()) && po.dNewableType().equals(pr.dNewableType()) && Objects.equals(po.dIdentity(), pr.dIdentity())) {
                                pres = pres.remove(pr);
                                setInMemoization(mem, po, Mutable.D_ALL_DERIVATIONS, mem.get(changeHandler, pr, Newable.D_ALL_DERIVATIONS.constant()), true);
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
        changeHandler.trigger(mutable, action, priority);
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
        return force ? mem.set(changeHandler, object, setable.constant(), result, force) : mem.getOrSet(changeHandler, object, setable.constant(), result);
    }

    protected <O> ConstantState memoization(O object) {
        return object instanceof Mutable ? ((Mutable) object).dMemoization(this) : memoization();
    }

    public <O> ConstantState memoization() {
        return memoization;
    }

    @SuppressWarnings("rawtypes")
    protected <O> boolean isTraceDerivation(O object, Setable setable) {
        return (setable == null || !setable.isPlumbing()) && universeTransaction().getConfig().isTraceDerivation();
    }

    private <O> String tracePre(O object) {
        return DclareTrace.getLineStart(memoization(object).toString(), this);
    }

    @Override
    public int depth() {
        return INDENT.get();
    }

    protected static class DerivedValue<O, T> extends Pair<O, Observed<O, T>> {
        private static final long serialVersionUID = -2566539820227398813L;

        private T                 value;

        protected DerivedValue(O a, Observed<O, T> b) {
            super(a, b);
        }

        protected T get() {
            return value == ConstantState.NULL ? null : value;
        }

        @SuppressWarnings("unchecked")
        protected void set(T value) {
            this.value = value == null ? (T) ConstantState.NULL : value;
        }

        protected boolean isSet() {
            return value != null;
        }

        protected boolean isDerived(O object, Observed<O, T> observed) {
            return a().equals(object) && b().equals(observed);
        }

    }

}
