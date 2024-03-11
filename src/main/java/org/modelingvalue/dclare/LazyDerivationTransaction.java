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
import java.util.concurrent.RecursiveAction;

import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Pair;

public class LazyDerivationTransaction extends AbstractDerivationTransaction {

    private static final Constant<Pair<LazyDerivationTransaction, Mutable>, RecursiveAction> LAZY_ACTION =     //
            Constant.of("LAZY_ACTION", null, p -> new DeriveAction(p.a(), p.b()), CoreSetableModifier.durable);

    private final MutableState                                                               state;

    protected LazyDerivationTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
        state = new MutableState(universeTransaction.emptyState());
    }

    @Override
    protected String getCurrentTypeForTrace() {
        return "LZ";
    }

    public State derive() {
        state.setState(state());
        try {
            deriveMutable(universeTransaction().universe());
            return state.state();
        } finally {
            state.setState(universeTransaction().emptyState());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void doDeriveMutable(Mutable mutable) {
        try {
            MutableClass dClass = mutable.dClass();
            Set<Setable> containments = MutableClass.D_CONTAINMENTS.get(dClass);
            Set<Mutable> children = containments.flatMap(s -> s.<Mutable> getCollection(mutable)).asSet();
            children.forEach(m -> deriveMutable((Mutable) m));
            Set<Observer> nonDerivers = MutableClass.D_NON_DERIVERS.get(dClass).filter(d -> d.direction().isLazy()).asSet();
            nonDerivers.forEach(o -> runDeriver(mutable, null, o, 0));
            Set<Observed> observeds = MutableClass.D_OBSERVEDS.get(dClass);
            observeds.forEach(o -> o.get(mutable));
        } catch (Throwable t) {
            universeTransaction().handleException(t);
        }
    }

    private void deriveMutable(Mutable mutable) {
        getAction(mutable).invoke();
    }

    private RecursiveAction getAction(Mutable mutable) {
        return memoization().get(this, Pair.of(this, mutable), LAZY_ACTION);
    }

    @Override
    protected <O, T> boolean doDeriveGet(O object, Getable<O, T> getable, T nonDerived) {
        return super.doDeriveGet(object, getable, nonDerived) && Objects.equals(nonDerived, getable.getDefault(object)) && ifReady(object, (Setable<O, T>) getable, nonDerived);
    }

    private <O, T> boolean ifReady(O object, Setable<O, T> setable, T nonDerived) {
        //        if (setable == Mutable.D_PARENT_CONTAINING && nonDerived == null && !memoization().isSet(this, object, setable.constant())) {
        //            getAction((Mutable) object).join();
        //        }
        return true;
    }

    @Override
    protected <T, O> T setInMemoization(ConstantState mem, O object, Setable<O, T> setable, T result, boolean force) {
        result = super.setInMemoization(mem, object, setable, result, force);
        if (!force && setable.preserved() && !setable.direction().isLazy()) {
            state.set(object, setable, result);
        }
        return result;
    }

    private static final class DeriveAction extends RecursiveAction {
        private static final long               serialVersionUID = -3996933680861103499L;

        private final LazyDerivationTransaction tx;
        private final Mutable                   mutable;

        private DeriveAction(LazyDerivationTransaction tx, Mutable mutable) {
            this.tx = tx;
            this.mutable = mutable;
        }

        @Override
        protected void compute() {
            tx.doDeriveMutable(mutable);
        }
    }

}
