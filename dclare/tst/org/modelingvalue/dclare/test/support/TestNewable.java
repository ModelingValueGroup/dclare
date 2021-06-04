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

package org.modelingvalue.dclare.test.support;

import java.util.function.Consumer;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Triple;
import org.modelingvalue.dclare.Construction;
import org.modelingvalue.dclare.Direction;
import org.modelingvalue.dclare.LeafTransaction;
import org.modelingvalue.dclare.Mutable;
import org.modelingvalue.dclare.Newable;
import org.modelingvalue.dclare.Observed;
import org.modelingvalue.dclare.Observer;

@SuppressWarnings("rawtypes")
public class TestNewable extends TestMutable implements Newable {

    public static final Observed<TestMutable, String> n = Observed.of("n", null);

    @SafeVarargs
    public static TestNewable create(Direction direction, Object reason, TestNewableClass clazz, Consumer<TestNewable>... observers) {
        LeafTransaction tx = LeafTransaction.getCurrent();
        return create(tx, clazz, new TestReason(direction, tx.mutable(), new Object[]{reason}, observers));
    }

    @SafeVarargs
    public static TestNewable create(Direction direction, Object reason1, Object reason2, TestNewableClass clazz, Consumer<TestNewable>... observers) {
        LeafTransaction tx = LeafTransaction.getCurrent();
        return create(tx, clazz, new TestReason(direction, tx.mutable(), new Object[]{reason1, reason2}, observers));
    }

    private static TestNewable create(LeafTransaction tx, TestNewableClass clazz, TestReason reason) {
        return tx.construct(reason, () -> new TestNewable(clazz.uniqueInt(), clazz));
    }

    private TestNewable(Comparable id, TestMutableClass clazz) {
        super(id, clazz);
    }

    @Override
    public Object dIdentity() {
        return dClass().identity().apply(this);
    }

    @Override
    public TestNewableClass dClass() {
        return (TestNewableClass) super.dClass();
    }

    @Override
    public Integer id() {
        return (Integer) super.id();
    }

    @Override
    public TestNewableClass dNewableType() {
        return dClass();
    }

    @Override
    public Comparable dSortKey() {
        return id();
    }

    @Override
    public Direction dDirection() {
        return dClass().direction();
    }

    @Override
    public Collection<? extends Observer<?>> dMutableObservers() {
        return Collection.concat(Newable.super.dMutableObservers(), dDerivedConstructions().map(Construction::reason).filter(TestReason.class).flatMap(TestReason::observers));
    }

    @Override
    public String toString() {
        Object id = n.get(this);
        return id != null ? super.toString() + ":" + id : super.toString();
    }

    private static class TestReason extends Construction.Reason {

        private final Direction        direction;
        private final Set<Observer<?>> observers;

        @SuppressWarnings("unchecked")
        private TestReason(Direction direction, Mutable thiz, Object[] id, Consumer<TestNewable>[] observers) {
            super(thiz, id);
            Set<Observer<?>> obs = Set.of();
            for (int i = 0; i < observers.length; i++) {
                Consumer<TestNewable> finalCons = observers[i];
                Observer observer = Observer.<TestNewable> of(Triple.of(thiz, this, i), n -> {
                    Mutable t = ((Triple<Mutable, ?, ?>) LeafTransaction.getCurrent().leaf().id()).a();
                    if (n.dDerivedConstructions().anyMatch(c -> c.reason().equals(this) && c.object().equals(t))) {
                        finalCons.accept(n);
                    }
                }, m -> direction);
                obs = obs.add(observer);
            }
            this.observers = obs;
            this.direction = direction;
        }

        @SuppressWarnings("unchecked")
        public Set<? extends Observer<?>> observers() {
            return (Set<? extends Observer<?>>) observers;
        }

        @Override
        public Direction direction() {
            return direction;
        }

    }

}
