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

package org.modelingvalue.dclare.test.support;

import java.util.function.Supplier;

import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.QuadConsumer;
import org.modelingvalue.collections.util.TriFunction;
import org.modelingvalue.dclare.LeafTransaction;
import org.modelingvalue.dclare.Observed;
import org.modelingvalue.dclare.Setable;
import org.modelingvalue.dclare.SetableModifier;

public class TestObserved<O, T> extends Observed<O, T> {
    private static Map<Object, TestObserved<?, ?>>              staticObservedMap = Map.of();

    private final TriFunction<O, TestObserved<O, T>, T, Object> serializeValue;
    private final TriFunction<O, TestObserved<O, T>, Object, T> deserializeValue;

    @SuppressWarnings("unchecked")
    public static <C, V> TestObserved<C, V> existing(Object id, SetableModifier... modifiers) {
        return (TestObserved<C, V>) staticObservedMap.get(id);
    }

    public static <C, V> Observed<C, V> of(Object id, TriFunction<C, TestObserved<C, V>, V, Object> serializeValue, TriFunction<C, TestObserved<C, V>, Object, V> deserializeValue, V def, SetableModifier... modifiers) {
        return new TestObserved<>(id, serializeValue, deserializeValue, def, null, null, null, modifiers);
    }

    protected TestObserved(Object id, TriFunction<O, TestObserved<O, T>, T, Object> serializeValue, TriFunction<O, TestObserved<O, T>, Object, T> deserializeValue, T def, Supplier<Setable<?, ?>> opposite, Supplier<Setable<O, Set<?>>> scope, QuadConsumer<LeafTransaction, O, T, T> changed, SetableModifier... modifiers) {
        super(id, o -> def, opposite, scope, changed, modifiers);
        this.serializeValue = serializeValue;
        this.deserializeValue = deserializeValue;
        synchronized (TestObserved.class) {
            staticObservedMap = staticObservedMap.put(id, this);
        }
    }

    public TriFunction<O, TestObserved<O, T>, T, Object> getSerializeValue() {
        return serializeValue;
    }

    public TriFunction<O, TestObserved<O, T>, Object, T> getDeserializeValue() {
        return deserializeValue;
    }
}
