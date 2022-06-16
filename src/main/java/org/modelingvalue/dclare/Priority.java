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

import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Internable;
import org.modelingvalue.collections.util.Pair;

public enum Priority implements Internable {

    urgent(0),

    forward(1),

    backward(2),

    scheduled(3);

    public final Queued<Action<?>> actions;
    public final Queued<Mutable>   children;
    public final int               nr;

    Priority(int nr) {
        actions = new Queued<>(true, nr);
        children = new Queued<>(false, nr);
        this.nr = nr;
    }

    public final class Queued<T extends TransactionClass> extends Setable<Mutable, Set<T>> {

        private final boolean actions;

        private Queued(boolean actions, int nr) {
            super(Pair.of(Priority.this, actions), Set.of(), null, null, null, CoreSetableModifier.plumbing);
            this.actions = actions;
        }

        public Priority priority() {
            return Priority.this;
        }

        public boolean actions() {
            return actions;
        }

        public boolean children() {
            return !actions;
        }

        @Override
        protected boolean deduplicate(Set<T> value) {
            return false;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + super.toString().substring(4);
        }

    }

}
