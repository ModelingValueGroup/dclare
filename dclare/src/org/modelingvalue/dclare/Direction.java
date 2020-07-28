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

import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Internable;
import org.modelingvalue.collections.util.Pair;

public enum Direction implements Internable {

    forward(0),

    backward(1),

    scheduled(2);

    public static final Direction[] FORWARD_SCHEDULED = new Direction[]{Direction.forward, Direction.scheduled};

    public static final Direction[] FORWARD_BACKWARD  = new Direction[]{Direction.forward, Direction.backward};

    public final Queued<Action<?>>  actions;
    public final Queued<Mutable>    children;
    public final int                nr;

    Direction(int nr) {
        actions = new Queued<>(true);
        children = new Queued<>(false);
        this.nr = nr;
    }

    public final class Queued<T extends TransactionClass> extends Setable<Mutable, Set<T>> {

        private final boolean actions;

        private Queued(boolean actions) {
            super(Pair.of(Direction.this, actions), Set.of(), false, null, null, null, null, false);
            this.actions = actions;
        }

        public Direction direction() {
            return Direction.this;
        }

        public boolean actions() {
            return actions;
        }

        public boolean children() {
            return !actions;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + super.toString().substring(4);
        }

    }

}
