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

import java.util.function.*;

import org.modelingvalue.collections.*;
import org.modelingvalue.collections.util.*;
import org.modelingvalue.dclare.*;

public class TestObserved<O, T> extends Observed<O, T> {
    private static Map<Object, TestObserved<?, ?>> staticObservedMap = Map.of();

    @SuppressWarnings("unchecked")
    public static <C, V> TestObserved<C, V> existing(Object id) {
        return (TestObserved<C, V>) staticObservedMap.get(id);
    }

    public static <C, V> Observed<C, V> of(Object id, V def) {
        return new TestObserved<>(id, false, def, false, null, null, null, true);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, boolean containment) {
        return new TestObserved<>(id, false, def, containment, null, null, null, true);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, QuadConsumer<LeafTransaction, C, V, V> changed) {
        return new TestObserved<>(id, false, def, false, null, null, changed, true);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, boolean containment, boolean checkConsistency) {
        return new TestObserved<>(id, false, def, containment, null, null, null, checkConsistency);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, Supplier<Setable<?, ?>> opposite) {
        return new TestObserved<>(id, false, def, false, opposite, null, null, true);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, Supplier<Setable<?, ?>> opposite, Supplier<Setable<C, Set<?>>> scope, boolean checkConsistency) {
        return new TestObserved<>(id, false, def, false, opposite, scope, null, checkConsistency);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def) {
        return new TestObserved<>(id, mandatory, def, false, null, null, null, true);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def, boolean containment) {
        return new TestObserved<>(id, mandatory, def, containment, null, null, null, true);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def, boolean containment, boolean checkConsistency) {
        return new TestObserved<>(id, mandatory, def, containment, null, null, null, checkConsistency);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def, QuadConsumer<LeafTransaction, C, V, V> changed) {
        return new TestObserved<>(id, mandatory, def, false, null, null, changed, true);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def, Supplier<Setable<?, ?>> opposite) {
        return new TestObserved<>(id, mandatory, def, false, opposite, null, null, true);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def, Supplier<Setable<?, ?>> opposite, Supplier<Setable<C, Set<?>>> scope, boolean checkConsistency) {
        return new TestObserved<>(id, mandatory, def, false, opposite, scope, null, checkConsistency);
    }

    protected TestObserved(Object id, boolean mandatory, T def, boolean containment, Supplier<Setable<?, ?>> opposite, Supplier<Setable<O, Set<?>>> scope, QuadConsumer<LeafTransaction, O, T, T> changed, boolean checkConsistency) {
        super(id, mandatory, def, containment, opposite, scope, changed, checkConsistency);
        synchronized (TestObserved.class) {
            staticObservedMap = staticObservedMap.put(id, this);
        }
    }
}
