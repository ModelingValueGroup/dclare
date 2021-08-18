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

import java.util.function.Consumer;
import java.util.function.Function;

public class Action<O extends Mutable> extends Leaf {

    protected static final Function<Mutable, Direction> DEFAULT_DIRECTION_FUNCTION = m -> m.dDirection();

    public static <M extends Mutable> Action<M> of(Object id) {
        return new Action<>(id, o -> {
        }, Priority.forward);
    }

    public static <M extends Mutable> Action<M> of(Object id, Consumer<M> action) {
        return new Action<>(id, action, Priority.forward);
    }

    public static <M extends Mutable> Action<M> of(Object id, Consumer<M> action, Priority initPriority) {
        return new Action<>(id, action, initPriority);
    }

    public static <M extends Mutable> Action<M> of(Object id, Consumer<M> action, Function<M, Direction> direction) {
        return new Action<>(id, action, direction, Priority.forward);
    }

    public static <M extends Mutable> Action<M> of(Object id, Consumer<M> action, Function<M, Direction> direction, Priority initPriority) {
        return new Action<>(id, action, direction, initPriority);
    }

    private final Consumer<O>            action;
    private final Function<O, Direction> direction;

    @SuppressWarnings("unchecked")
    protected Action(Object id, Consumer<O> action, Priority initPriority) {
        this(id, action, (Function<O, Direction>) DEFAULT_DIRECTION_FUNCTION, initPriority);
    }

    protected Action(Object id, Consumer<O> action, Function<O, Direction> direction, Priority initPriority) {
        super(id, initPriority);
        this.action = action;
        this.direction = direction;
    }

    @Override
    public ActionTransaction openTransaction(MutableTransaction parent) {
        return parent.universeTransaction().actionTransactions.get().open(this, parent);
    }

    @Override
    public void closeTransaction(Transaction tx) {
        tx.universeTransaction().actionTransactions.get().close((ActionTransaction) tx);
    }

    public void run(O object) {
        action.accept(object);
    }

    public void trigger(O mutable) {
        LeafTransaction.getCurrent().trigger(mutable, this, initPriority());
    }

    @Override
    public ActionTransaction newTransaction(UniverseTransaction universeTransaction) {
        return new ActionTransaction(universeTransaction);
    }

    protected Direction direction(O mutable) {
        return direction.apply(mutable);
    }

}
