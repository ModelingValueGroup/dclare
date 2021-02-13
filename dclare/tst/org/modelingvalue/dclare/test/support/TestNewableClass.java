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
import java.util.function.Function;

import org.modelingvalue.dclare.Setable;

@SuppressWarnings({"unused", "rawtypes"})
public class TestNewableClass extends TestMutableClass {

    @SafeVarargs
    public static TestNewableClass of(Object id, Function<TestNewable, Object> identity, Setable<? extends TestMutable, ?>... setables) {
        return new TestNewableClass(id, identity, setables);
    }

    private final Function<TestNewable, Object> identity;
    private final AtomicInteger                 counter = new AtomicInteger(0);

    protected TestNewableClass(Object id, Function<TestNewable, Object> identity, Setable... setables) {
        super(id, setables);
        this.identity = identity;
    }

    public Function<TestNewable, Object> identity() {
        return identity;
    }

    public int uniqueInt() {
        return counter.getAndIncrement();
    }

}
