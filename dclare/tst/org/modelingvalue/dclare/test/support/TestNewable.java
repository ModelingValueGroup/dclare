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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.Construction;
import org.modelingvalue.dclare.LeafTransaction;
import org.modelingvalue.dclare.Newable;
import org.modelingvalue.dclare.Observer;

@SuppressWarnings("rawtypes")
public class TestNewable extends TestMutable implements Newable {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    @SafeVarargs
    @SuppressWarnings("rawtypes")
    public static TestNewable create(Object reason, TestNewableClass clazz, Consumer<TestNewable>... observers) {
        return LeafTransaction.getCurrent().construct(new TestReason(reason, observers), () -> new TestNewable(COUNTER.getAndIncrement(), clazz));
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

    @SuppressWarnings("rawtypes")
    @Override
    public Comparable id() {
        return (Comparable) super.id();
    }

    @Override
    public TestNewableClass dNewableType() {
        return dClass();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Comparable dSortKey() {
        return id();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<? extends Observer<?>> dMutableObservers() {
        return dConstructions().map(Construction::reason).filter(TestReason.class).flatMap(TestReason::observers);
    }

    @Override
    public String toString() {
        Object id = dIdentity();
        return id != null ? super.toString() + ":" + id : super.toString();
    }

    private static class TestReason extends Construction.Reason {

        private Set<Observer> observers = Set.of();

        @SafeVarargs
        private TestReason(Object id, Consumer<TestNewable>... observers) {
            super(new Object[]{id});
            for (int i = 0; i < observers.length; i++) {
                Observer observer = Observer.of(Pair.of(id, i), observers[i]);
                this.observers = this.observers.add(observer);
            }
        }

        @SuppressWarnings("unchecked")
        public Set<? extends Observer<?>> observers() {
            return (Set<? extends Observer<?>>) observers;
        }

    }

}
