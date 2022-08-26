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

import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Context;
import org.modelingvalue.collections.util.ContextThread;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.ex.TransactionException;

public abstract class AbstractDerivationTransaction extends ReadOnlyTransaction {
    private static final String                                  INDENTATION = "    ";
    private static final String                                  SEQ_FORMAT  = " %0" + INDENTATION.length() + "d";
    @SuppressWarnings("rawtypes")
    protected static final Context<Set<Pair<Mutable, Observed>>> DERIVED     = Context.of(Set.of());
    @SuppressWarnings("rawtypes")
    protected static final Context<Pair<Mutable, Observer>>      DERIVER     = Context.of(null);
    private static final Context<Boolean>                        DERIVE      = Context.of(true);
    private static final Context<String>                         INDENT      = Context.of("");

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

    @SuppressWarnings("rawtypes")
    public <O, T> boolean doDerive(O object, Getable<O, T> getable) {
        return object instanceof Mutable && getable instanceof Observed && !((Observed) getable).doNotDerive() && DERIVE.get();
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
            if (Newable.D_DERIVED_CONSTRUCTIONS.equals(observed) || Mutable.D_PARENT_CONTAINING.equals(observed)) {
                return value;
            } else {
                Pair<Mutable, Observed> derived = Pair.of((Mutable) object, observed);
                Set<Pair<Mutable, Observed>> oldDerived = DERIVED.get();
                Set<Pair<Mutable, Observed>> newDerived = oldDerived.add(derived);
                if (oldDerived == newDerived) {
                    if (isTraceDerivation(observed)) {
                        runNonDeriving(() -> System.err.println(tracePre() + " REC " + object + "." + observed + " => RECURSIVE DERIVATION, result is the non-derived value: " + value));
                    }
                    return value;
                } else {
                    if (isTraceDerivation(observed)) {
                        runNonDeriving(() -> System.err.println(tracePre() + " >>> " + object + "." + observed));
                    }
                    INDENT.run(INDENT.get() + INDENTATION, () -> DERIVED.run(newDerived, () -> {
                        int i = 0;
                        Set<Observer> observers = ((Mutable) object).dAllDerivers(observed).toSet();
                        for (Observer observer : observers.filter(Observer::anonymous)) {
                            runDeriver((Mutable) object, observed, observer, ++i);
                        }
                        for (Observer observer : observers.exclude(Observer::anonymous)) {
                            runDeriver((Mutable) object, observed, observer, ++i);
                        }
                    }));
                    if (!constantState.isSet(this, object, constant)) {
                        if (isTraceDerivation(observed)) {
                            runNonDeriving(() -> System.err.println(tracePre() + " NOD " + object + "." + observed + " => NO DERIVATION, result is the non-derived value: " + value));
                        }
                        return value;
                    }
                }
            }
        }
        return constantState.get(this, object, constant);

    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void runDeriver(Mutable mutable, Observed observed, Observer observer, int i) {
        if (isTraceDerivation(observed)) {
            runNonDeriving(() -> System.err.println(tracePre(i) + ">>> " + mutable + "." + observer + "()"));
        }
        try {
            Pair<Mutable, Observer> deriver = Pair.of(mutable, observer);
            DERIVER.run(deriver, () -> deriver.b().run(mutable));
        } catch (Throwable t) {
            if (isTraceDerivation(observed)) {
                runNonDeriving(() -> System.err.println(tracePre() + " !!! " + mutable + "." + observer + "() => THROWS " + t));
            }
            universeTransaction().handleException(new TransactionException(mutable, new TransactionException(observer, t)));
        }
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
            constantState.set(this, object, setable.constant(), post, true);
            T pre = getNonDerived(object, setable);
            if (isTraceDerivation(setable)) {
                runNonDeriving(() -> {
                    Pair<Mutable, Observer> deriver = DERIVER.get();
                    System.err.println(tracePre() + " SET " + deriver.a() + "." + deriver.b() + "(" + object + "." + setable + "=" + pre + "->" + post + ")");
                });
            }
            if (setable.containment()) {
                Setable.<T, Mutable> diff(pre, post, added -> {
                    constantState.set(this, added, Mutable.D_PARENT_CONTAINING.constant(), Pair.of((Mutable) object, (Setable<Mutable, ?>) setable), true);
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

    @SuppressWarnings("rawtypes")
    private boolean isTraceDerivation(Setable setable) {
        return !setable.isPlumbing() && universeTransaction().getConfig().isTraceDerivation();
    }

    private String tracePre() {
        return String.format("DERIVE: %02d%s", ContextThread.getNr(), INDENT.get());
    }

    private String tracePre(int i) {
        String indent = INDENT.get();
        String seqIndent = indent.substring(0, indent.length() - INDENTATION.length()) + String.format(SEQ_FORMAT, i);
        return String.format("DERIVE: %02d%s", ContextThread.getNr(), seqIndent);
    }

    @Override
    protected ConstantState constantState() {
        return constantState;
    }
}
