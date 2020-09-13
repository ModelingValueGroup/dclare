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

package org.modelingvalue.dclare.test.support;

import org.modelingvalue.collections.*;
import org.modelingvalue.collections.util.*;
import org.modelingvalue.dclare.*;

@SuppressWarnings("unused")
public class TestClass implements MutableClass {
    private static Map<Object, TestClass> staticTestClassMap = Map.of();

    public static TestClass existing(Object id) {
        return staticTestClassMap.get(id);
    }

    @SafeVarargs
    public static TestClass of(Object id, Observer<? extends TestObject>... observers) {
        return new TestClass(id, Set.of(), Set.of(observers));
    }

    @SafeVarargs
    public static TestClass of(Object id, Setable<? extends TestObject, ?> setable, Observer<? extends TestObject>... observers) {
        return new TestClass(id, Set.of(setable), Set.of(observers));
    }

    @SafeVarargs
    public static TestClass of(Object id, Setable<? extends TestObject, ?> setable0, Setable<? extends TestObject, ?> setable1, Observer<? extends TestObject>... observers) {
        return new TestClass(id, Set.of(setable0, setable1), Set.of(observers));
    }

    @SafeVarargs
    public static TestClass of(Object id, Setable<? extends TestObject, ?> setable0, Setable<? extends TestObject, ?> setable1, Setable<? extends TestObject, ?> setable2, Observer<? extends TestObject>... observers) {
        return new TestClass(id, Set.of(setable0, setable1, setable2), Set.of(observers));
    }

    @SafeVarargs
    public static TestClass of(Object id, Setable<? extends TestObject, ?> setable0, Setable<? extends TestObject, ?> setable1, Setable<? extends TestObject, ?> setable2, Setable<? extends TestObject, ?> setable3, Observer<? extends TestObject>... observers) {
        return new TestClass(id, Set.of(setable0, setable1, setable2, setable3), Set.of(observers));
    }

    private final Object                         id;
    private final Set <? extends Observer <?>>   observers;
    private final Set <? extends Setable <?, ?>> setables;

    protected TestClass(Object id, Set<? extends Setable<?, ?>> setables, Set<? extends Observer<?>> observers) {
        this.id = id;
        this.setables = setables;
        this.observers = observers;
        synchronized (TestClass.class) {
            staticTestClassMap = staticTestClassMap.put(id, this);
        }
    }

    @Override
    public Set<? extends Observer<?>> dObservers() {
        return observers;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<? extends Setable<? extends Mutable, ?>> dSetables() {
        return (Set<? extends Setable<? extends Mutable, ?>>) setables;
    }

    public String serializeClass() {
        return id.toString();
    }

    @Override
    public String toString() {
        return StringUtil.toString(id);
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
            TestClass c = (TestClass) obj;
            return id.equals(c.id);
        }
    }
}
