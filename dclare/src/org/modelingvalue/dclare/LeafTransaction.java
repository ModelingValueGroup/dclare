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

import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Context;

@SuppressWarnings("unused")
public abstract class LeafTransaction extends Transaction {

    private static final Context<LeafTransaction> CURRENT = Context.of();

    protected LeafTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    public final Leaf leaf() {
        return (Leaf) cls();
    }

    @SuppressWarnings("rawtypes")
    public static int size(DefaultMap<?, Set<Mutable>> map) {
        return map.reduce(0, (a, e) -> a + (!(e.getKey() instanceof Observed) || ((Observed) e.getKey()).checkConsistency() ? e.getValue().size() : 0), Integer::sum);
    }

    public static LeafTransaction getCurrent() {
        return CURRENT.get();
    }

    public static Context<LeafTransaction> getContext() {
        return CURRENT;
    }

    public abstract State state();

    public State current() {
        return state();
    }

    public abstract <O, T, E> T set(O object, Setable<O, T> property, BiFunction<T, E, T> function, E element);

    public abstract <O, T, E> T set(O object, Setable<O, T> property, UnaryOperator<T> oper);

    public abstract <O, T> T set(O object, Setable<O, T> property, T post);

    public <O, T> T get(O object, Getable<O, T> property) {
        return state().get(object, property);
    }

    public <O, T> T current(O object, Getable<O, T> property) {
        return current().get(object, property);
    }

    public <O, T> T pre(O object, Getable<O, T> property) {
        return universeTransaction().preState().get(object, property);
    }

    protected <O, T> void changed(O object, Setable<O, T> property, T preValue, T postValue) {
        property.changed(this, object, preValue, postValue);
        if (property instanceof Observed) {
            trigger((Observed<O, T>) property, object);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public <O> void clear(O object) {
        for (Entry<Setable, Object> e : state().getProperties(object)) {
            set(object, e.getKey(), e.getKey().getDefault());
        }
    }

    protected abstract void setChanged(Mutable changed);

    protected <O extends Mutable> void trigger(O target, Action<O> action, Direction direction) {
        Mutable object = target;
        set(object, direction.actions, Set::add, action);
        if (direction == Direction.forward) {
            set(object, Direction.backward.actions, Set::remove, action);
        }
        Mutable container = dParent(object);
        while (container != null && !ancestorEqualsMutable(object)) {
            set(container, direction.children, Set::add, object);
            if (direction == Direction.forward && current(object, Direction.backward.actions).isEmpty() && current(object, Direction.backward.children).isEmpty()) {
                set(container, Direction.backward.children, Set::remove, object);
            }
            object = container;
            container = dParent(object);
        }
    }

    private boolean ancestorEqualsMutable(Mutable object) {
        MutableTransaction mt = parent();
        while (mt != null && !mt.mutable().equals(object)) {
            mt = mt.parent();
        }
        return mt != null;
    }

    protected Mutable dParent(Mutable object) {
        return state().getA(object, Mutable.D_PARENT_CONTAINING);
    }

    public void runNonObserving(Runnable action) {
        action.run();
    }

    public <T> T getNonObserving(Supplier<T> action) {
        return action.get();
    }

    @Override
    public Mutable mutable() {
        return parent().mutable();
    }

    public abstract ActionInstance actionInstance();

    @SuppressWarnings("unchecked")
    public <O extends Newable> O construct(Construction.Reason reason, Supplier<O> supplier) {
        O result = (O) universeTransaction().constantState.get(this, reason, Construction.CONSTRUCTED, c -> supplier.get());
        set(result, Newable.D_CONSTRUCTIONS, Set::add, Construction.of(reason));
        return result;
    }

    protected <O> void trigger(Observed<O, ?> observed, O o) {
    }

    public abstract boolean isChanged();

}
