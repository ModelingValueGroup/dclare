//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2021 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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
import java.util.function.*;

import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Context;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.ex.TransactionException;

public class DerivationTransaction extends ReadOnlyTransaction {

    @SuppressWarnings("rawtypes")
    private static final Context<Set<Derivation>> DERIVED = Context.of(Set.of());

    protected DerivationTransaction(UniverseTransaction universeTransaction) {
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
            constantState.stop();
            this.constantState = null;
        }
    }

    @Override
    public <O, T> T get(O object, Getable<O, T> getable) {
        if (doDeriver(object, getable)) {
            return derive(object, (Observed<O, T>) getable);
        } else {
            return super.get(object, getable);
        }
    }

    @Override
    protected <O, T> T current(O object, Getable<O, T> getable) {
        if (doDeriver(object, getable)) {
            return derive(object, (Observed<O, T>) getable);
        } else {
            return super.current(object, getable);
        }
    }

    private <O, T> boolean doDeriver(O object, Getable<O, T> getable) {
        return object instanceof Mutable && getable instanceof Observed && state().get((Mutable) object, Mutable.D_PARENT_CONTAINING) == null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <O, T> T derive(O object, Observed<O, T> observed) {
        Constant<O, T> constant = observed.constant();
        LeafTransaction leafTransaction = LeafTransaction.getCurrent();
        if (!constantState.isSet(leafTransaction, object, constant)) {
            Derivation<O, T> derivation = new Derivation<O, T>(object, observed);
            Set<Derivation> oldDerived = DERIVED.get();
            Set<Derivation> newDerived = oldDerived.add(derivation);
            if (oldDerived == newDerived) {
                return (T) oldDerived.get(derivation).value;
            } else {
                DERIVED.run(newDerived, () -> {
                    for (Observer deriver : MutableClass.D_DERIVERS.get(((Mutable) object).dClass()).get(observed)) {
                        try {
                            deriver.run((Mutable) object);
                        } catch (Throwable t) {
                            universeTransaction().handleException(new TransactionException((Mutable) object, new TransactionException(deriver, t)));
                        }
                    }
                });
                constantState.set(LeafTransaction.getCurrent(), object, observed.constant(), derivation.value, true);
            }
        }
        return constantState.get(leafTransaction, object, constant);
    }

    @Override
    public <O, T, E> T set(O object, Setable<O, T> setable, BiFunction<T, E, T> function, E element) {
        return set(object, setable, function.apply(setable.getDefault(), element));
    }

    @Override
    public <O, T> T set(O object, Setable<O, T> setable, UnaryOperator<T> oper) {
        return set(object, setable, oper.apply(setable.getDefault()));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <O, T> T set(O object, Setable<O, T> setable, T value) {
        if (doDeriver(object, setable)) {
            Derivation<O, T> derivation = DERIVED.get().get(new Derivation<O, T>(object, (Observed<O, T>) setable));
            T pre = derivation.value;
            derivation.value = value;
            return pre;
        } else if (!Objects.equals(state().get(object, setable), value)) {
            return super.set(object, setable, value);
        } else {
            return value;
        }
    }

    @SuppressWarnings("rawtypes")
    private static class Derivation<O, T> extends Pair<O, Observed<O, T>> {
        private static final long serialVersionUID = -4899856581075715796L;
        private T                 value;

        private Derivation(O a, Observed<O, T> b) {
            super(a, b);
            value = b.getDefault();
        }
    }

}
