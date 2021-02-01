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

import java.util.function.*;

import org.modelingvalue.collections.*;
import org.modelingvalue.collections.util.*;
import org.modelingvalue.dclare.*;

public class TestObserved<O, T> extends Observed<O, T> {
    private static Map<Object, TestObserved<?, ?>> staticObservedMap = Map.of();

    private final BiFunction<TestObserved<O, T>, T, Object> serializeValue;
    private final BiFunction<TestObserved<O, T>, Object, T> deserializeValue;

    @SuppressWarnings("unchecked")
    public static <C, V> TestObserved<C, V> existing(Object id) {
        return (TestObserved<C, V>) staticObservedMap.get(id);
    }

    public static <C, V> Observed<C, V> of(Object id, BiFunction<TestObserved<C, V>, V, Object> serializeValue, BiFunction<TestObserved<C, V>, Object, V> deserializeValue, V def) {
        return new TestObserved<>(id, serializeValue, deserializeValue, false, def, false, null, null, null, true);
    }

    public static <C, V> Observed<C, V> of(Object id, BiFunction<TestObserved<C, V>, V, Object> serializeValue, BiFunction<TestObserved<C, V>, Object, V> deserializeValue, V def, boolean containment) {
        return new TestObserved<>(id, serializeValue, deserializeValue, false, def, containment, null, null, null, true);
    }

    protected TestObserved(Object id, BiFunction<TestObserved<O, T>, T, Object> serializeValue, BiFunction<TestObserved<O, T>, Object, T> deserializeValue, boolean mandatory, T def, boolean containment, Supplier<Setable<?, ?>> opposite, Supplier<Setable<O, Set<?>>> scope, QuadConsumer<LeafTransaction, O, T, T> changed, boolean checkConsistency) {
        super(id, mandatory, def, containment, opposite, scope, changed, checkConsistency);
        this.serializeValue = serializeValue;
        this.deserializeValue = deserializeValue;
        synchronized (TestObserved.class) {
            staticObservedMap = staticObservedMap.put(id, this);
        }
    }

    public BiFunction<TestObserved<O, T>, T, Object> getSerializeValue() {
        return serializeValue;
    }

    public BiFunction<TestObserved<O, T>, Object, T> getDeserializeValue() {
        return deserializeValue;
    }
}
