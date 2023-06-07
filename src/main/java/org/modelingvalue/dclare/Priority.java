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

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Concurrent;
import org.modelingvalue.collections.util.Internable;
import org.modelingvalue.collections.util.Pair;

public enum Priority implements LeafModifier, Internable {

    zero, // Running

    one, // Scheduled immediate as possible

    two, // Deferred scheduled inner

    three,

    four,

    five; // Deferred scheduled outer

    // To prevent Array allocations each time Priority.values() is called.
    public static final Priority[] ALL   = Priority.values();
    public static final Priority   INNER = two;
    public static final Priority   OUTER = five;

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

    public static class Concurrents<T> {

        private final Concurrent<T>[] priorities;
        private final Priority        start;

        @SuppressWarnings("unchecked")
        public Concurrents(Priority start) {
            this.start = start;
            priorities = new Concurrent[ALL.length - start.ordinal()];
            for (int i = 0; i < priorities.length; i++) {
                priorities[i] = Concurrent.of();
            }
        }

        public void init(T init) {
            for (int i = 0; i < priorities.length; i++) {
                priorities[i].init(init);
            }
        }

        public void merge() {
            for (int i = 0; i < priorities.length; i++) {
                priorities[i].merge();
            }
        }

        public void clear() {
            for (int i = 0; i < priorities.length; i++) {
                priorities[i].clear();
            }
        }

        public boolean set(Priority priority, T value) {
            return priorities[priority.ordinal() - start.ordinal()].set(value);
        }

        public boolean change(Priority priority, UnaryOperator<T> oper) {
            return priorities[priority.ordinal() - start.ordinal()].change(oper);
        }

        public T get(Priority priority) {
            return priorities[priority.ordinal() - start.ordinal()].get();
        }

        public T result(Priority priority) {
            return priorities[priority.ordinal() - start.ordinal()].result();
        }

        public Priority start() {
            return start;
        }

        public Priority priority(int i) {
            return ALL[i + start.ordinal()];
        }

        public Priority first(Function<T, Boolean> test) {
            for (int i = 0; i < priorities.length; i++) {
                if (test.apply(priorities[i].get())) {
                    return priority(i);
                }
            }
            return null;
        }

        public int length() {
            return priorities.length;
        }
    }

    public static class MutableStates {

        private final MutableState[] priorities;
        private final Priority       start;

        @SuppressWarnings("unchecked")
        public MutableStates(Priority start, Supplier<MutableState> init) {
            this.start = start;
            priorities = new MutableState[ALL.length - start.ordinal()];
            for (int i = 0; i < priorities.length; i++) {
                priorities[i] = init.get();
            }
        }

        public MutableState get(Priority priority) {
            return priorities[priority.ordinal() - start.ordinal()];
        }

        public void setState(State state) {
            for (int i = 0; i < priorities.length; i++) {
                priorities[i].setState(state);
            }
        }

        public Priority start() {
            return start;
        }

        public Priority priority(int i) {
            return ALL[i + start.ordinal()];
        }

        public int length() {
            return priorities.length;
        }
    }

}
