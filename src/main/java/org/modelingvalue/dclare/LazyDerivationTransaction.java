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

import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicReference;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Context;
import org.modelingvalue.collections.util.Triple;

public class LazyDerivationTransaction extends AbstractDerivationTransaction {

    private static Context<Boolean>                                                                       RUNNING_GET_IDENTITY = Context.of(false);

    private static final Constant<Triple<LazyDerivationTransaction, Mutable, MutableClass>, DeriveAction> LAZY_ACTION          =                   //
            Constant.of("LAZY_ACTION", null, t -> new DeriveAction(t.a(), t.b(), t.c()), CoreSetableModifier.durable);

    private final MutableState                                                                            state;
    private final AtomicReference<Set<Mutable>>                                                           queue;

    protected LazyDerivationTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
        state = new MutableState(universeTransaction.emptyState());
        queue = new AtomicReference<>(Set.of());
    }

    @Override
    protected String getCurrentTypeForTrace() {
        return "LZ";
    }

    public State derive() {
        state.setState(state());
        try {
            deriveMutable(universeTransaction().universe());
            for (Set<Mutable> todo = queue.getAndSet(Set.of()); !todo.isEmpty(); todo = queue.getAndSet(Set.of())) {
                todo.forEach(this::deriveMutable);
            }
            return state.state();
        } finally {
            state.setState(universeTransaction().emptyState());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void doDeriveMutable(Mutable mutable, MutableClass cls) {
        if (!universeTransaction().isKilled()) {
            try {
                Set<Observer> observers = MutableClass.D_OBSERVERS.get(cls).filter(d -> d.direction().isLazy()).asSet();
                observers.forEach(o -> runDeriver(mutable, null, o, 0));
                Set<Setable> containments = MutableClass.D_CONTAINMENTS.get(cls).asSet();
                Set<Mutable> children = containments.flatMap(s -> s.<Mutable> getCollection(mutable)).asSet();
                children.forEach(this::deriveMutable);
            } catch (Throwable t) {
                universeTransaction().handleException(t);
            }
        }
    }

    private void deriveMutable(Mutable mutable) {
        getAction(mutable).invoke();
    }

    private DeriveAction getAction(Mutable mutable) {
        return memoization().get(this, Triple.of(this, mutable, mutable.dClass()), LAZY_ACTION);
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected <O, T> Collection<Observer> getDerivers(O object, Observed<O, T> observed) {
        if (object instanceof Mutable && isLazyDerived((Mutable) object)) {
            return super.getDerivers(object, observed);
        } else {
            return super.getDerivers(object, observed).filter(d -> d.direction().isLazy());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <O, T> boolean doDeriveGet(O object, Getable<O, T> getable, T nonDerived) {
        return super.doDeriveGet(object, getable, nonDerived) && //
                ((isLazyDerived((Mutable) object) && ifReady((Mutable) object, (Setable<Mutable, T>) getable, nonDerived)) || //
                        hasLazyDeriver((Mutable) object, (Setable<Mutable, T>) getable));
    }

    private boolean isLazyDerived(Mutable mutable) {
        return memoization().isSet(this, mutable, Newable.D_ALL_DERIVATIONS.constant());
    }

    private <O extends Mutable, T> boolean ifReady(O object, Setable<O, T> setable, T nonDerived) {
        if (setable == Mutable.D_PARENT_CONTAINING && nonDerived == null && !RUNNING_GET_IDENTITY.get() && !memoization().isSet(this, object, setable.constant())) {
            getAction(object).join();
        }
        return true;
    }

    @Override
    protected Object getIdentity(Newable po) {
        return RUNNING_GET_IDENTITY.get(true, () -> super.getIdentity(po));
    }

    private <O extends Mutable, T> boolean hasLazyDeriver(O object, Setable<O, T> setable) {
        return getNonDeriving(() -> ((Mutable) object).dClass().dDerivers(setable).anyMatch(d -> d.direction().isLazy()));
    }

    @Override
    protected <T, O> void setInMemoization(ConstantState mem, O object, Setable<O, T> setable, T result) {
        super.setInMemoization(mem, object, setable, result);
        universeTransaction().stats().bumpAndGetTotalChanges();
        if (setable == Mutable.D_PARENT_CONTAINING && result != null) {
            queue.getAndAccumulate(Set.of((Mutable) object), Set::addAll);
        }
        if (setable == Mutable.D_ALL_DERIVATIONS || setable == Mutable.D_PARENT_CONTAINING || (setable.preserved() && !setable.direction().isLazy())) {
            state.set(object, setable, result);
        }
    }

    private static final class DeriveAction extends RecursiveAction {
        private static final long               serialVersionUID = -3996933680861103499L;

        private final LazyDerivationTransaction tx;
        private final Mutable                   mutable;
        private final MutableClass              cls;

        private DeriveAction(LazyDerivationTransaction tx, Mutable mutable, MutableClass cls) {
            this.tx = tx;
            this.mutable = mutable;
            this.cls = cls;
        }

        @Override
        protected void compute() {
            tx.doDeriveMutable(mutable, cls);
        }
    }

}
