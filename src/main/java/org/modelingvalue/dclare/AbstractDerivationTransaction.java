//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2022 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import org.modelingvalue.collections.Entry;
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
    protected <O, T> boolean doDerive(O object, Getable<O, T> getable) {
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
        if (doDerive(object, getable)) {
            Observed<O, T> observed = (Observed<O, T>) getable;
            ConstantState mem = memoization(object);
            Constant<O, T> constant = observed.constant();
            if (!mem.isSet(this, object, constant)) {
                if (Newable.D_DERIVED_CONSTRUCTIONS.equals(observed) || Mutable.D_PARENT_CONTAINING.equals(observed)) {
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
                            Set<Observer> observers = ((Mutable) object).dAllDerivers(observed).toSet();
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void runDeriver(Mutable mutable, Observed observed, Observer observer, int i) {
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
        return set(object, setable, function.apply(getNonDerived(object, setable), element));
    }

    @Override
    public <O, T> T set(O object, Setable<O, T> setable, UnaryOperator<T> oper) {
        return set(object, setable, oper.apply(getNonDerived(object, setable)));
    }

    public void runNonDeriving(Runnable action) {
        DERIVE.run(false, action);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <O, T> T set(O object, Setable<O, T> setable, T post) {
        if (doDerive(object, setable)) {
            ConstantState mem = memoization(object);
            mem.set(this, object, setable.constant(), post, true);
            T pre = getNonDerived(object, setable);
            if (isTraceDerivation(object, setable)) {
                runNonDeriving(() -> {
                    Pair<Mutable, Observer> deriver = DERIVER.get();
                    System.err.println(tracePre(object) + "SET  " + deriver.a() + "." + deriver.b() + "(" + object + "." + setable + "=" + pre + "->" + post + ")");
                });
            }
            if (setable.containment()) {
                Setable.<T, Mutable> diff(pre, post, added -> {
                    mem.set(this, added, Mutable.D_PARENT_CONTAINING.constant(), Pair.of((Mutable) object, (Setable<Mutable, ?>) setable), true);
                    for (Entry<Setable, Set<Observer>> d : MutableClass.D_DERIVERS.get(added.dClass())) {
                        if (!d.getValue().isEmpty()) {
                            derive(added, d.getKey(), d.getKey().getDefault());
                        }
                    }
                }, removed -> {
                });
            }
            return pre;
        } else if (!Objects.equals(getNonDerived(object, setable), post)) {
            return super.set(object, setable, post);
        } else {
            return post;
        }
    }

    @Override
    public <O extends Newable> O directConstruct(Construction.Reason reason, Supplier<O> supplier) {
        return super.construct(reason, supplier);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <O extends Newable> O construct(Reason reason, Supplier<O> supplier) {
        Pair<Mutable, Observer> deriver = DERIVER.get();
        O result = supplier.get();
        Construction cons = Construction.of(deriver.a(), deriver.b(), reason);
        memoization(deriver.a()).set(this, result, Newable.D_DERIVED_CONSTRUCTIONS.constant(), Newable.D_DERIVED_CONSTRUCTIONS.getDefault().add(cons), true);
        return result;
    }

    protected <O> ConstantState memoization(O object) {
        return object instanceof Mutable ? ((Mutable) object).dMemoization(this) : memoization();
    }

    public <O> ConstantState memoization() {
        return memoization;
    }

    @SuppressWarnings("rawtypes")
    protected <O> boolean isTraceDerivation(O object, Setable setable) {
        return !setable.isPlumbing() && universeTransaction().getConfig().isTraceDerivation();
    }

    private <O> String tracePre(O object) {
        return DclareTrace.getLineStart(memoization(object).toString(), this);
    }

    @Override
    public int depth() {
        return INDENT.get();
    }

}
