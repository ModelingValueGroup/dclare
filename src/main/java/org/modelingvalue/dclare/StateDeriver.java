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
import java.util.concurrent.atomic.AtomicReference;

public abstract class StateDeriver implements IState {

    private final AtomicReference<State> state;

    public StateDeriver(State state) {
        this.state = new AtomicReference<>(state);
    }

    @Override
    public <O, T> T get(O object, Getable<O, T> property) {
        if (property instanceof Setable) {
            T val = state.get().get(object, property);
            if (Objects.equals(val, property.getDefault())) {
                T derived = derive(object, (Setable<O, T>) property);
                if (!Objects.equals(val, derived)) {
                    state.updateAndGet(s -> s.set(object, (Setable<O, T>) property, derived));
                    return derived;
                }
            }
            return val;
        } else {
            return property.get(object);
        }
    }

    @Override
    public State state() {
        return state.get();
    }

    protected abstract <O, T> T derive(O object, Setable<O, T> property);

}
