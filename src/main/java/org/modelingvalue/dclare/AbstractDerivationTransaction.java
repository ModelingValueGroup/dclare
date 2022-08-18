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

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Context;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.ex.TransactionException;

public abstract class AbstractDerivationTransaction extends ReadOnlyTransaction {

    @SuppressWarnings("rawtypes")
    private static final Context<Set<Pair<Object, Observed>>> DERIVED = Context.of(Set.of());
    @SuppressWarnings("rawtypes")
    private static final Context<Observer>                    DERIVER = Context.of(null);

    private static final Context<Boolean>                     DERIVE  = Context.of(true);

    public boolean isDeriving() {
        return !DERIVED.get().isEmpty();
    }

    protected AbstractDerivationTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    private ConstantState constantState;

    public <R> R derive(Supplier<R> action, State state, ConstantState constantState) {
        this.constantState = constantState;
        try {
            return get(action, state);
        } catch (Throwable t) {
            universeTransaction().handleException(t);
            return null;
        } finally {
            this.constantState = null;
        }
    }

    public <O, T> boolean doDerive(O object, Getable<O, T> getable) {
        return object instanceof Mutable && getable instanceof Observed && DERIVE.get();
    }

    protected <O, T> T getNonDerived(O object, Getable<O, T> getable) {
        return super.get(object, getable);
    }

    @Override
    public <O, T> T get(O object, Getable<O, T> getable) {
        T value = getNonDerived(object, getable);
        if (doDerive(object, getable)) {
            return derive(object, (Observed<O, T>) getable, value);
        } else {
            return value;
        }
    }

    @Override
    protected <O, T> T current(O object, Getable<O, T> getable) {
        T value = super.current(object, getable);
        if (doDerive(object, getable)) {
            return derive(object, (Observed<O, T>) getable, value);
        } else {
            return value;
        }
    }

    @SuppressWarnings("rawtypes")
    private <O, T> T derive(O object, Observed<O, T> observed, T value) {
        Constant<O, T> constant = observed.constant();
        if (!constantState.isSet(this, object, constant)) {
            if (Newable.D_DERIVED_CONSTRUCTIONS.equals(observed) || Newable.D_DIRECT_CONSTRUCTION.equals(observed)) {
                return value;
            } else {
                Pair<Object, Observed> slot = Pair.of(object, observed);
                Set<Pair<Object, Observed>> oldDerived = DERIVED.get();
                Set<Pair<Object, Observed>> newDerived = oldDerived.add(slot);
                if (oldDerived == newDerived) {
                    return value;
                } else {
                    DERIVED.run(newDerived, () -> {
                        for (Observer deriver : derivers((Mutable) object, observed)) {
                            runDeriver((Mutable) object, deriver);
                        }
                    });
                    if (!constantState.isSet(this, object, constant)) {
                        return value;
                    }
                }
            }
        }
        return constantState.get(this, object, constant);

    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void runDeriver(Mutable mutable, Observer deriver) {
        try {
            DERIVER.run(deriver, () -> deriver.run(mutable));
        } catch (Throwable t) {
            universeTransaction().handleException(new TransactionException(mutable, new TransactionException(deriver, t)));
        }
    }

    @SuppressWarnings("rawtypes")
    protected Collection<Observer> derivers(Mutable object, Observed observed) {
        return object.dAllDerivers(observed);
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

    @SuppressWarnings("unchecked")
    @Override
    public <O, T> T set(O object, Setable<O, T> setable, T post) {
        if (doDerive(object, setable)) {
            constantState.set(this, object, setable.constant(), post, true);
            T pre = getNonDerived(object, setable);
            if (universeTransaction().getConfig().isTraceDerivation()) {
                runNonDeriving(() -> System.err.println("DERIVE: " + parent().indent("    ") + object + "." + DERIVER.get() + " (" + object + "." + setable + "=" + pre + "->" + post + ")"));
            }
            if (setable.containment()) {
                Setable.<T, Mutable> diff(pre, post, added -> {
                    set(added, Mutable.D_PARENT_CONTAINING, Pair.of((Mutable) object, (Setable<Mutable, ?>) setable));
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

}
