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

import static org.modelingvalue.dclare.CoreSetableModifier.*;

import java.util.function.Predicate;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.QualifiedSet;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.Observer.Constructed;

@SuppressWarnings("unused")
public interface Mutable extends TransactionClass {

    Mutable                                                  THIS                     = new This();

    Set<Mutable>                                             THIS_SINGLETON           = Set.of(THIS);

    @SuppressWarnings("rawtypes")
    ParentContaining                                         D_PARENT_CONTAINING      = new ParentContaining("D_PARENT_CONTAINING", plumbing, preserved);

    Setable<Mutable, TransactionId>                          D_CHANGE_ID              = Setable.of("D_CHANGE_ID", null, plumbing);

    @SuppressWarnings({"rawtypes", "unchecked"})
    Setable<Mutable, Set<? extends Observer<?>>>             D_OBSERVERS              = Setable.of("D_OBSERVERS", Set.of(), (tx, obj, pre, post) -> Setable.<Set<? extends Observer<?>>, Observer> diff(pre, post,              //
            added -> added.trigger(obj, added.fixpointGroup() == FixpointGroup.DEFAULT ? added.initPriority() : Priority.five),                                                                                                 //
            removed -> {
            }));

    Observer<Mutable>                                        D_OBSERVERS_RULE         = NonCheckingObserver.of("D_OBSERVERS_RULE", m -> D_OBSERVERS.set(m, m.dAllObservers().exclude(o -> o.direction().isLazy()).asSet()));

    @SuppressWarnings("unchecked")
    Observer<Mutable>                                        D_PUSHING_CONSTANTS_RULE = NonCheckingObserver.of("D_PUSHING_CONSTANTS_RULE", m -> MutableClass.D_PUSHING_CONSTANTS.get(m.dClass()).forEachOrdered(c -> c.get(m)));

    Constant<Mutable, Construction>                          D_INITIAL_CONSTRUCTION   = Constant.of("D_INITIAL_CONSTRUCTION", null, plumbing, durable);
    @SuppressWarnings({"unchecked", "rawtypes"})
    Observed<Mutable, QualifiedSet<Direction, Construction>> D_ALL_DERIVATIONS        = Observed.of("D_ALL_DERIVATIONS", QualifiedSet.of(c -> c.reason().direction()), (tx, o, b, a) -> {
                                                                                          Setable.<QualifiedSet<Direction, Construction>, Construction> diff(b, a,                                                              //
                                                                                                  add -> {
                                                                                                      Constructed cons = add.observer().constructed();
                                                                                                      cons.set(add.object(), Map::put, Entry.of(add.reason(), o));
                                                                                                  },                                                                                                                            //
                                                                                                  rem -> {
                                                                                                      Constructed cons = rem.observer().constructed();
                                                                                                      if (o.equals(cons.current(rem.object()).get(rem.reason()))) {
                                                                                                          cons.set(rem.object(), Map::removeKey, rem.reason());
                                                                                                      }
                                                                                                  });
                                                                                      }, plumbing, doNotMerge);

    default Construction dInitialConstruction() {
        return D_INITIAL_CONSTRUCTION.get(this);
    }

    default QualifiedSet<Direction, Construction> dAllDerivations() {
        QualifiedSet<Direction, Construction> derivations = D_ALL_DERIVATIONS.current(this);
        Construction initial = dInitialConstruction();
        return initial != null && initial.isDerived() && derivations.get(initial.reason().direction()) == null ? derivations.add(initial) : derivations;
    }

    default Pair<Mutable, Setable<Mutable, ?>> dParentContaining() {
        return D_PARENT_CONTAINING.superGet(this);
    }

    default boolean dBecameOrphan() {
        LeafTransaction tx = LeafTransaction.getCurrent();
        return tx.preStartState(Priority.OUTER).getRaw(this, Mutable.D_PARENT_CONTAINING) != null && //
                tx.state().get(this, Mutable.D_PARENT_CONTAINING) == null;
    }

    default void dChangedParentContaining(Pair<Mutable, Setable<Mutable, ?>> pre, Pair<Mutable, Setable<Mutable, ?>> post) {
    }

    default Mutable dParent() {
        Pair<Mutable, Setable<Mutable, ?>> pair = dParentContaining();
        return pair != null ? pair.a() : null;
    }

    default Setable<Mutable, ?> dContaining() {
        Pair<Mutable, Setable<Mutable, ?>> pair = dParentContaining();
        return pair != null ? pair.b() : null;
    }

    default boolean dDelete() {
        Pair<Mutable, Setable<Mutable, ?>> pair = dParentContaining();
        if (pair != null) {
            pair.b().remove(pair.a(), this);
            return true;
        } else {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    default <C> C dAncestor(Class<C> cls) {
        Mutable parent = this;
        while (parent != null && !cls.isInstance(parent)) {
            parent = parent.dParent();
        }
        return (C) parent;
    }

    @SuppressWarnings("unchecked")
    default <C> List<C> dAncestors(Class<C> cls) {
        List<C> result = List.of();
        Mutable parent = this;
        while (cls.isInstance(parent)) {
            result = result.append((C) parent);
            parent = parent.dParent();
        }
        return result;
    }

    default boolean dHasAncestor(Mutable ancestor) {
        for (Mutable parent = dParent(); parent != null; parent = parent.dParent()) {
            if (parent.equals(ancestor)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    default <C> C dAncestor(Class<C> cls, Predicate<Setable> containing) {
        Mutable result = this;
        Pair<Mutable, Setable<Mutable, ?>> pair;
        while ((pair = result.dParentContaining()) != null) {
            if (cls.isInstance(result) && containing.test(pair.b())) {
                return (C) result;
            } else {
                result = pair.a();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    default <T> T dParent(Class<T> cls) {
        Mutable p = dParent();
        return cls.isInstance(p) ? (T) p : null;
    }

    default void dActivate() {
        D_OBSERVERS_RULE.trigger(this);
        D_PUSHING_CONSTANTS_RULE.trigger(this);
        for (Mutable child : dChildren()) {
            child.dActivate();
        }
    }

    default void dDeactivate(LeafTransaction tx) {
        D_OBSERVERS_RULE.deObserve(tx, this);
        D_PUSHING_CONSTANTS_RULE.deObserve(tx, this);
        D_OBSERVERS.setDefault(this);
    }

    MutableClass dClass();

    @SuppressWarnings("rawtypes")
    default Collection<Observer> dAllDerivers(Setable setable) {
        return dClass().dDerivers(setable);
    }

    default Collection<? extends Observer<?>> dAllObservers() {
        return dClass().dObservers();
    }

    @SuppressWarnings("unchecked")
    default Collection<? extends Mutable> dChildren() {
        return MutableClass.D_CONTAINMENTS.get(dClass()).flatMap(c -> (Collection<? extends Mutable>) c.getCollection(this));
    }

    @SuppressWarnings("unchecked")
    default Collection<? extends Mutable> dChildren(State state) {
        return MutableClass.D_CONTAINMENTS.get(dClass()).flatMap(c -> (Collection<? extends Mutable>) state.getCollection(this, c));
    }

    @Override
    default MutableTransaction openTransaction(MutableTransaction parent) {
        return parent.universeTransaction().mutableTransactions.get().open(this, parent);
    }

    @Override
    default void closeTransaction(Transaction tx) {
        tx.universeTransaction().mutableTransactions.get().close((MutableTransaction) tx);
    }

    @Override
    default MutableTransaction newTransaction(UniverseTransaction universeTransaction) {
        return new MutableTransaction(universeTransaction);
    }

    @Override
    default State run(State state, MutableTransaction parent) {
        Pair<Mutable, Setable<Mutable, ?>> pair = state.get(this, D_PARENT_CONTAINING);
        if (pair != null && parent.mutable().equals(pair.a())) {
            return TransactionClass.super.run(state, parent);
        } else {
            return state;
        }
    }

    default Mutable dResolve(Mutable self) {
        return this;
    }

    @SuppressWarnings("rawtypes")
    default void dHandleRemoved(Mutable parent) {
        for (Observer o : D_OBSERVERS.get(this)) {
            o.constructed().setDefault(this);
        }
    }

    default boolean dCheckConsistency() {
        return true;
    }

    default boolean dIsOrphan(State state) {
        return state.get(this, D_PARENT_CONTAINING) == null;
    }

    default ConstantState dMemoization(AbstractDerivationTransaction tx) {
        return tx.memoization();
    }

    final class ParentContaining extends Observed<Mutable, Pair<Mutable, Setable<Mutable, ?>>> {
        private ParentContaining(Object id, SetableModifier<?>... modifiers) {
            super(id, null, null, null, (tx, m, b, a) -> m.dChangedParentContaining(b, a), modifiers);
        }

        @Override
        public Pair<Mutable, Setable<Mutable, ?>> get(Mutable object) {
            return object.dParentContaining();
        }

        private Pair<Mutable, Setable<Mutable, ?>> superGet(Mutable object) {
            return super.get(object);
        }
    }
}
