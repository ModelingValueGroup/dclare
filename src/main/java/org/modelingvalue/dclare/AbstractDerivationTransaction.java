//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2023 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
//                                                                                                                     ~
// Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in      ~
// compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0  ~
// Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on ~
// an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the  ~
// specific language governing permissions and limitations under the License.                                          ~
//                                                                                                                     ~
// Maintainers:                                                                                                        ~
//     Wim Bast, Tom Brus, Ronald Krijgsheld                                                                           ~
// Contributors:                                                                                                       ~
//     Arjan Kok, Carel Bast                                                                                           ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.dclare;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.ContainingCollection;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.QualifiedSet;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Context;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.Construction.Reason;
import org.modelingvalue.dclare.ex.TransactionException;

public abstract class AbstractDerivationTransaction extends ReadOnlyTransaction {
    @SuppressWarnings("rawtypes")
    protected static final Context<Set<Pair<Mutable, Observed>>> DERIVED = Context.of(Set.of());
    @SuppressWarnings("rawtypes")
    protected static final Context<Pair<Mutable, Observer>>      DERIVER = Context.of(null);
    private static final Context<Boolean>                        DERIVE  = Context.of(true);
    private static final Context<Integer>                        INDENT  = Context.of(0);

    public static boolean isDeriving() {
        return !DERIVED.get().isEmpty();
    }

    protected AbstractDerivationTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    private ConstantState memoization;

    public <R> R derive(Supplier<R> action, State state, ConstantState memoization) {
        this.memoization = memoization;
        try {
            return get(action, state);
        } catch (Throwable t) {
            universeTransaction().handleException(t);
            return null;
        } finally {
            this.memoization = null;
        }
    }

    @SuppressWarnings("rawtypes")
    protected <O, T> boolean doDeriveGet(O object, Getable<O, T> getable, T nonDerived) {
        return doDeriveSet(object, getable);
    }

    @SuppressWarnings("rawtypes")
    protected <O, T> boolean doDeriveSet(O object, Getable<O, T> getable) {
        return object instanceof Mutable && getable instanceof Observed && DERIVE.get();
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

    @SuppressWarnings("rawtypes")
    private <O, T> T derive(O object, Getable<O, T> getable, T nonDerived) {
        if (doDeriveGet(object, getable, nonDerived)) {
            Observed<O, T> observed = (Observed<O, T>) getable;
            ConstantState mem = memoization(object);
            Constant<O, T> constant = observed.constant();
            if (!mem.isSet(this, object, constant)) {
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
                        INDENT.run(INDENT.get() + 1, () -> DERIVED.run(newDerived, () -> {
                            int i = 0;
                            Set<Observer> observers = getDerivers(object, observed).asSet();
                            for (Observer observer : observers.filter(Observer::anonymous)) {
                                runDeriver((Mutable) object, observed, observer, ++i);
                            }
                            for (Observer observer : observers.exclude(Observer::anonymous)) {
                                runDeriver((Mutable) object, observed, observer, ++i);
                            }
                        }));
                        if (!mem.isSet(this, object, constant)) {
                            if (isTraceDerivation(object, observed)) {
                                INDENT.run(INDENT.get() + 1, () -> runNonDeriving(() -> System.err.println(tracePre(object) + "NODR " + object + "." + observed + " => NO DERIVATION, result is the non-derived value: " + nonDerived)));
                            }
                            return nonDerived;
                        }
                    }
                }
            }
            return mem.get(this, object, constant);
        } else {
            return nonDerived;
        }
    }

    @SuppressWarnings("rawtypes")
    protected <O, T> Collection<Observer> getDerivers(O object, Observed<O, T> observed) {
        return ((Mutable) object).dAllDerivers(observed);
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
        return set(object, setable, function.apply(get(object, setable), element));
    }

    @Override
    public <O, T> T set(O object, Setable<O, T> setable, UnaryOperator<T> oper) {
        return set(object, setable, oper.apply(get(object, setable)));
    }

    public void runNonDeriving(Runnable action) {
        DERIVE.run(false, action);
    }

    public <T> T getNonDeriving(Supplier<T> supplier) {
        return DERIVE.get(false, supplier);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <O, T> T set(O object, Setable<O, T> setable, T post) {
        if (doDeriveSet(object, setable)) {
            ConstantState mem = memoization(object);
            Constant<O, T> constant = setable.constant();
            T pre = mem.isSet(this, object, constant) ? mem.get(this, object, constant) : getNonDerived(object, setable);
            T result = match(mem, setable, pre, post);
            setInMemoization(mem, object, setable, result);
            if (isTraceDerivation(object, setable)) {
                runNonDeriving(() -> {
                    Pair<Mutable, Observer> deriver = DERIVER.get();
                    System.err.println(tracePre(object) + "SET  " + (deriver == null ? "" : deriver.a() + "." + deriver.b()) + "(" + object + "." + setable + "=" + pre + "->" + result + ")");
                });
            }
            if (setable.containment()) {
                Pair<Mutable, Setable<Mutable, ?>> pc = Pair.of((Mutable) object, (Setable<Mutable, ?>) setable);
                setable.<Mutable> collection(result).forEachOrdered(m -> {
                    setInMemoization(mem, m, Mutable.D_PARENT_CONTAINING, pc);
                });
            }
            return pre;
        } else if (!Objects.equals(getNonDerived(object, setable), post)) {
            return super.set(object, setable, post);
        } else {
            return post;
        }
    }

    private <T, O> T match(ConstantState mem, Setable<O, T> setable, T pre, T post) {
        List<Newable> pres = setable.collection(pre).filter(Newable.class).distinct().asList();
        if (!pres.isEmpty()) {
            List<Newable> posts = setable.collection(post).filter(Newable.class).exclude(pres::contains).distinct().asList();
            if (!posts.isEmpty()) {
                for (Newable po : posts) {
                    Construction poInit = Mutable.D_INITIAL_CONSTRUCTION.get(po);
                    if (poInit.isDerived() && mem.isSet(this, po, Newable.D_ALL_DERIVATIONS.constant())) {
                        for (Newable pr : pres) {
                            Construction preInit = Mutable.D_INITIAL_CONSTRUCTION.get(pr);
                            if (preInit.isDirect() && po.dNewableType().equals(pr.dNewableType()) && Objects.equals(getIdentity(po), getIdentity(pr))) {
                                pres = pres.remove(pr);
                                post = replace(post, po, pr);
                                addDerivations(mem, po, pr);
                            }
                        }
                    } else if (poInit.isDirect()) {
                        for (Newable pr : pres) {
                            Construction preInit = Mutable.D_INITIAL_CONSTRUCTION.get(pr);
                            if (preInit.isDerived() && mem.isSet(this, pr, Newable.D_ALL_DERIVATIONS.constant()) && po.dNewableType().equals(pr.dNewableType()) && Objects.equals(getIdentity(po), getIdentity(pr))) {
                                pres = pres.remove(pr);
                                addDerivations(mem, pr, po);
                            }
                        }
                    }
                }
            }
        }
        return post;
    }

    @SuppressWarnings("unchecked")
    protected <T, O> void addDerivations(ConstantState mem, Newable from, Newable to) {
        QualifiedSet<Direction, Construction> fromDer = mem.get(this, from, Newable.D_ALL_DERIVATIONS.constant());
        QualifiedSet<Direction, Construction> toDer = getNonDerived(to, Newable.D_ALL_DERIVATIONS);
        mem.clear(this, to);
        setInMemoization(mem, from, Mutable.D_ALL_DERIVATIONS, fromDer.putAll(toDer));
        setInMemoization(mem, to, Mutable.D_ALL_DERIVATIONS, toDer.putAll(fromDer));
    }

    protected Object getIdentity(Newable po) {
        return po.dIdentity();
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
    public <O extends Newable> O directConstruct(Construction.Reason reason, Supplier<O> supplier) {
        return super.construct(reason, supplier);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <O extends Mutable> O construct(Reason reason, Supplier<O> supplier) {
        Pair<Mutable, Observer> deriver = DERIVER.get();
        O result = supplier.get();
        Construction cons = Construction.of(deriver.a(), deriver.b(), reason);
        ConstantState mem = memoization(deriver.a());
        setInMemoization(mem, result, Newable.D_ALL_DERIVATIONS, Newable.D_ALL_DERIVATIONS.getDefault(result).add(cons));
        Mutable.D_INITIAL_CONSTRUCTION.force(result, cons);
        return result;
    }

    protected <T, O> void setInMemoization(ConstantState mem, O object, Setable<O, T> setable, T result) {
        mem.set(this, object, setable.constant(), result, true);
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

}
