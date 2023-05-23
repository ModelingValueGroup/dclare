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

import java.util.Arrays;

import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Internable;
import org.modelingvalue.collections.util.Pair;

public enum Priority implements LeafModifier, Internable {

    immediate,

    inner,

    mid,

    outer,

    scheduled;

    // To prevent Array allocations each time Priority.values() is called.
    public static final Priority[] ALL           = Priority.values();
    public static final Priority[] NON_SCHEDULED = Arrays.copyOf(Priority.ALL, Priority.ALL.length - 1);

    public final Queued<Action<?>> actions;
    public final Queued<Mutable>   children;

    Priority() {
        actions = new Queued<>(true);
        children = new Queued<>(false);
    }

    public final class Queued<T extends TransactionClass> extends Setable<Mutable, Set<T>> {

        private final boolean actions;

        private Queued(boolean actions) {
            super(Pair.of(Priority.this, actions), m -> Set.of(), null, null, null, SetableModifier.plumbing);
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
            return getClass().getSimpleName() + super.toString();
        }

    }

}
