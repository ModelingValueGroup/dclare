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

import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.Construction.Reason;

public class IdentityDerivationTransaction extends AbstractDerivationTransaction {

    protected IdentityDerivationTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    private ObserverTransaction                original;
    private Newable                            child;
    private Pair<Mutable, Setable<Mutable, ?>> parent;

    @SuppressWarnings("rawtypes")
    public <R> R derive(Supplier<R> action, State state, ObserverTransaction original, Newable child, Pair<Mutable, Setable<Mutable, ?>> parent, ConstantState constantState) {
        this.original = original;
        this.child = child;
        this.parent = parent;
        try {
            return derive(action, state, constantState);
        } finally {
            this.original = null;
            this.child = null;
            this.parent = null;
        }
    }

    @Override
    public State current() {
        return original.current();
    }

    @Override
    protected <O, T> boolean doDerive(O object, Getable<O, T> getable) {
        return super.doDerive(object, getable) && !isChanged(object, getable);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <O, T> T getNonDerived(O object, Getable<O, T> getable) {
        if (isOld(object)) {
            return original.outerStartState().get(object, getable);
        } else if (getable == Mutable.D_PARENT_CONTAINING && object.equals(child)) {
            return (T) parent;
        } else {
            return super.getNonDerived(object, getable);
        }
    }

    private <O, T> boolean isChanged(O object, Getable<O, T> getable) {
        T pre = original.preOuterStartState().get(object, getable);
        T post = original.outerStartState().get(object, getable);
        return !Objects.equals(pre, post);
    }

    private <O> boolean isOld(O object) {
        return object instanceof Mutable && original.outerStartState().get((Mutable) object, Mutable.D_PARENT_CONTAINING) != null;
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

    @Override
    public int depth() {
        return original.depth() + super.depth();
    }

    @Override
    protected String getCurrentTypeForTrace() {
        return "ID";
    }
}
