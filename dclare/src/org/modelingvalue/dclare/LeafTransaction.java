//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2019 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Context;

@SuppressWarnings("unused")
public abstract class LeafTransaction extends Transaction {

    protected static final Context<LeafTransaction> CURRENT = Context.of();

    protected LeafTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    public final Leaf leaf() {
        return (Leaf) cls();
    }

    public static int size(DefaultMap<?, Set<Mutable>> map) {
        return map.reduce(0, (a, e) -> a + e.getValue().size(), Integer::sum);
    }

    public static LeafTransaction getCurrent() {
        return CURRENT.get();
    }

    public static void setCurrent(LeafTransaction t) {
        CURRENT.set(t);
    }

    public static int depth() {
        int i = 0;
        for (Transaction t = CURRENT.get().parent().parent(); t != null; t = t.parent()) {
            i++;
        }
        return i;
    }

    public static String indent(String indent) {
        StringBuffer i = new StringBuffer();
        for (Transaction t = CURRENT.get().parent().parent(); t != null; t = t.parent()) {
            i.append(indent);
        }
        return i.toString();
    }

    public abstract State state();

    public abstract <O, T, E> T set(O object, Setable<O, T> property, BiFunction<T, E, T> function, E element);

    public abstract <O, T> T set(O object, Setable<O, T> property, T post);

    public <O, T> T get(O object, Getable<O, T> property) {
        return state().get(object, property);
    }

    public <O, T> T pre(O object, Getable<O, T> property) {
        return universeTransaction().preState().get(object, property);
    }

    public abstract <O, T> T current(O object, Getable<O, T> property);

    protected <O, T> void changed(O object, Setable<O, T> property, T preValue, T postValue) {
        property.changed(this, object, preValue, postValue);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public <O> void clear(O object) {
        for (Entry<Setable, Object> e : state().getProperties(object)) {
            set(object, e.getKey(), e.getKey().getDefault());
        }
    }

    protected <O extends Mutable> void trigger(O mutable, Action<O> action, Direction direction) {
        Mutable object = mutable;
        set(object, direction.preDepth, Set::add, action);
        Mutable container = dParent(object);
        while (container != null && !parent().ancestorEqualsMutable(object)) {
            set(container, direction.depth, Set::add, object);
            object = container;
            container = dParent(object);
        }
    }

    protected Mutable dParent(Mutable object) {
        return state().getA(object, Mutable.D_PARENT_CONTAINING);
    }

    public void runNonObserving(Runnable action) {
        action.run();
    }

    @Override
    public final Mutable mutable() {
        return parent().mutable();
    }

    public abstract ActionInstance actionInstance();

}
