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
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Context;
import org.modelingvalue.collections.util.Pair;

public class DerivationTransaction extends ReadOnlyTransaction {

    @SuppressWarnings("rawtypes")
    public static final Context<Set<Pair<Object, Observed>>> DERIVED = Context.of(Set.of());

    protected DerivationTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    private ConstantState constantState;

    @Override
    protected ConstantState constantState() {
        return constantState;
    }

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
        if (!constant.isSet(object)) {
            Pair<Object, Observed> slot = Pair.of(object, observed);
            Set<Pair<Object, Observed>> derived = DERIVED.get();
            if (derived.contains(slot)) {
                return observed.getDefault();
            } else {
                DERIVED.run(derived.add(slot), () -> {
                    for (Observer deriver : MutableClass.D_DERIVERS.get(((Mutable) object).dClass()).get(observed)) {
                        deriver.run((Mutable) object);
                    }
                });
            }
        }
        return constant.get(object);
    }

    @Override
    public <O, T, E> T set(O object, Setable<O, T> setable, BiFunction<T, E, T> function, E element) {
        return set(object, setable, function.apply(setable.getDefault(), element));
    }

    @Override
    public <O, T> T set(O object, Setable<O, T> setable, UnaryOperator<T> oper) {
        return set(object, setable, oper.apply(setable.getDefault()));
    }

    @Override
    public <O, T> T set(O object, Setable<O, T> setable, T value) {
        if (doDeriver(object, setable)) {
            setable.constant().set(object, value);
            return setable.getDefault();
        } else if (!Objects.equals(state().get(object, setable), value)) {
            return super.set(object, setable, value);
        } else {
            return value;
        }
    }

}
