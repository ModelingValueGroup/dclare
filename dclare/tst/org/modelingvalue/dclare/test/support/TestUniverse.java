//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2020 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import org.modelingvalue.dclare.Universe;

@SuppressWarnings("unused")
public class TestUniverse extends TestObject implements Universe {
    public static TestUniverse of(Object id, TestClass clazz) {
        return new TestUniverse(id, u -> {
        }, clazz);
    }

    public static TestUniverse of(Object id, Consumer<Universe> init, TestClass clazz) {
        return new TestUniverse(id, init, clazz);
    }

    private final Consumer<Universe> init;

    protected TestUniverse(Object id, Consumer<Universe> init, TestClass clazz) {
        super(id, clazz);
        this.init = init;
    }

    @Override
    public void init() {
        Universe.super.init();
        init.accept(this);
    }
}
