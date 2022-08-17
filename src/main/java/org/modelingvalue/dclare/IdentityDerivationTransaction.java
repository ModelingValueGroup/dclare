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
import java.util.function.Supplier;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.dclare.Construction.Reason;

public class IdentityDerivationTransaction extends AbstractDerivationTransaction {

    protected IdentityDerivationTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    private ObserverTransaction original;

    public <R> R derive(Supplier<R> action, State state, ObserverTransaction original, ConstantState constantState) {
        this.original = original;
        try {
            return derive(action, state, constantState);
        } finally {
            this.original = null;
        }
    }

    @Override
    public State current() {
        return original.current();
    }

    @Override
    protected <O, T> boolean doDeriver(O object, Getable<O, T> getable) {
        if (super.doDeriver(object, getable)) {
            T pre = original.prevOuterStartState().get(object, getable);
            T post = original.outerStartState().get(object, getable);
            if (!Objects.equals(pre, post)) {
                return false;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    protected <O, T> T getNonDerived(O object, Getable<O, T> getable) {
        if (object instanceof Mutable && isOld((Mutable) object)) {
            return original.outerStartState().get(object, getable);
        } else {
            return original.state().get(object, getable);
        }
    }

    private boolean isOld(Mutable object) {
        return original.outerStartState().get(object, Mutable.D_PARENT_CONTAINING) != null;
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected Collection<Observer> derivers(Mutable object, Observed observed) {
        return isChanged(object) ? super.derivers(object, observed).exclude(Observer::anonymous) : super.derivers(object, observed);
    }

    @SuppressWarnings("rawtypes")
    private boolean isChanged(Mutable object) {
        if (isOld(object)) {
            TransactionId txid = original.outerStartState().get(object, Mutable.D_CHANGE_ID);
            return txid != null && txid.number() > original.prevOuterStartState().get(universeTransaction().universe(), Mutable.D_CHANGE_ID).number();
        } else {
            return false;
        }
    }

    @Override
    public <O extends Newable> O directConstruct(Construction.Reason reason, Supplier<O> supplier) {
        return super.construct(reason, supplier);
    }

    @Override
    public <O extends Newable> O construct(Reason reason, Supplier<O> supplier) {
        O result = supplier.get();
        Construction cons = Construction.of(Mutable.THIS, Observer.DUMMY, reason);
        set(result, Newable.D_DERIVED_CONSTRUCTIONS, Newable.D_DERIVED_CONSTRUCTIONS.getDefault().add(cons));
        return result;
    }
}
