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

package org.modelingvalue.dclare.test.support;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.MutationWrapper;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.collections.util.SerializableConsumer;
import org.modelingvalue.collections.util.SerializableFunction;
import org.modelingvalue.collections.util.SerializablePredicate;
import org.modelingvalue.collections.util.StringUtil;
import org.modelingvalue.collections.util.Triple;
import org.modelingvalue.dclare.LeafModifier;
import org.modelingvalue.dclare.Mutable;
import org.modelingvalue.dclare.MutableClass;
import org.modelingvalue.dclare.Observer;
import org.modelingvalue.dclare.Setable;

@SuppressWarnings({"unused", "rawtypes"})
public class TestMutableClass implements MutableClass {
    private static Map<Object, TestMutableClass> staticTestClassMap = Map.of();

    public static TestMutableClass existing(String id) {
        return staticTestClassMap.get(id);
    }

    @SafeVarargs
    public static TestMutableClass of(String id, Setable<? extends TestMutable, ?>... setables) {
        TestMutableClass result = new TestMutableClass(id, setables);
        synchronized (TestMutableClass.class) {
            staticTestClassMap = staticTestClassMap.put(id, result);
        }
        return result;
    }

    protected final Object                            id;
    private final Set<Setable>                        setables;
    protected final MutationWrapper<Set<Observer<?>>> observers;

    @SuppressWarnings("unchecked")
    protected TestMutableClass(Object id, Setable[] setables) {
        this.id = id;
        this.setables = Collection.of(setables).toSet();
        this.observers = new MutationWrapper(Set.of());
    }

    @SuppressWarnings("unchecked")
    public TestMutableClass observe(SerializableConsumer<TestMutable> action, LeafModifier... modifiers) {
        action = action.of();
        Observer<?> of = Observer.of(action, action, modifiers);
        observers.update(Set::add, of);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <V> TestMutableClass observe(Setable<TestMutable, V> setable, SerializableFunction<TestMutable, V> value, LeafModifier... modifiers) {
        value = value.of();
        Observer<?> of = Observer.of(Pair.of(setable, value), setable, value, modifiers);
        observers.update(Set::add, of);
        return this;
    }

    @SuppressWarnings("unchecked")
    public TestMutableClass observe(SerializablePredicate<TestMutable> predicate, SerializableConsumer<TestMutable> action, LeafModifier... modifiers) {
        predicate = predicate.of();
        action = action.of();
        Observer<?> of = Observer.of(Pair.of(predicate, action), predicate, action, modifiers);
        observers.update(Set::add, of);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <V> TestMutableClass observe(SerializablePredicate<TestMutable> predicate, Setable<TestMutable, V> setable, SerializableFunction<TestMutable, V> value, LeafModifier... modifiers) {
        predicate = predicate.of();
        value = value.of();
        Observer<?> of = Observer.of(Triple.of(predicate, setable, value), setable, predicate, value, modifiers);
        observers.update(Set::add, of);
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<? extends Observer<?>> dObservers() {
        return (Set<? extends Observer<?>>) observers.get();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<? extends Setable<? extends Mutable, ?>> dSetables() {
        return (Set) setables;
    }

    public String serializeClass() {
        return id.toString();
    }

    @Override
    public String toString() {
        return StringUtil.toString(id);
    }

    protected Object id() {
        return id;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (getClass() != obj.getClass()) {
            return false;
        } else {
            TestMutableClass c = (TestMutableClass) obj;
            return id.equals(c.id);
        }
    }

    public boolean isInstance(TestMutable mutable) {
        return equals(mutable.dClass());
    }

}
