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

    public final Queued<Action<?>>          preDepth;
    public final Queued<Mutable>            depth;
    public final int                        nr;
    public final Queued<TransactionClass>[] sequence;

    @SuppressWarnings("unchecked")
    Direction(int nr) {
        preDepth = new Queued<>(false);
        depth = new Queued<>(true);
        sequence = new Queued[]{preDepth, depth};
        this.nr = nr;
    }

    public static Direction[] forwardAndBackward() {
        return new Direction[]{forward, backward};
    }

    public final class Queued<T extends TransactionClass> extends Setable<Mutable, Set<T>> {
        private final boolean depth;

        private Queued(boolean depth) {
            super(Pair.of(Direction.this, depth), Set.of(), false, null, null, null, false);
            this.depth = depth;
        }

        public Direction direction() {
            return Direction.this;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + super.toString().substring(4);
        }

        public boolean depth() {
            return depth;
        }
    }

}
