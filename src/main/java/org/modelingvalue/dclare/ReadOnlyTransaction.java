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

import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@SuppressWarnings("unused")
public class ReadOnlyTransaction extends LeafTransaction {

    private State state;

    protected ReadOnlyTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    public final ReadOnly readOnlyCls() {
        return (ReadOnly) cls();
    }

    @Override
    protected State run(State state) {
        throw new UnsupportedOperationException();
    }

    public <R> R get(Supplier<R> action, State state) {
        this.state = state;
        try {
            return LeafTransaction.getContext().get(this, action);
        } finally {
            this.state = null;
        }
    }

    public void run(Runnable action, State state) {
        this.state = state;
        try {
            LeafTransaction.getContext().run(this, action);
        } finally {
            this.state = null;
        }
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public <O, T> void changed(O object, Setable<O, T> property, T preValue, T rawPreValue, T postValue) {
        if (property instanceof Constant) {
            if (property.isHandlingChange()) {
                universeTransaction().put(new Object(), () -> super.changed(object, property, preValue, rawPreValue, postValue));
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public <O, T, E> T set(O object, Setable<O, T> property, BiFunction<T, E, T> function, E element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <O, T> T set(O object, Setable<O, T> property, UnaryOperator<T> oper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <O, T> T set(O object, Setable<O, T> property, T post) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ActionInstance actionInstance() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected <O extends Mutable> void trigger(O mutable, Action<O> action, Priority priority) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void stop() {
        super.stop();
        state = null;
    }

    @Override
    public Direction direction() {
        return Direction.DEFAULT;
    }

    @Override
    protected String getCurrentTypeForTrace() {
        return "RO";
    }
}
