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

package org.modelingvalue.dclare;

import java.util.function.Supplier;

import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.QuadConsumer;

@SuppressWarnings("unused")
public class NonCheckingObserved<O, T> extends Observed<O, T> {

    public static <C, V> Observed<C, V> of(Object id, V def) {
        return new NonCheckingObserved<>(id, false, def, false, null, null, null);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, boolean containment) {
        return new NonCheckingObserved<>(id, false, def, containment, null, null, null);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, QuadConsumer<LeafTransaction, C, V, V> changed) {
        return new NonCheckingObserved<>(id, false, def, false, null, null, changed);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, Supplier<Setable<?, ?>> opposite) {
        return new NonCheckingObserved<>(id, false, def, false, opposite, null, null);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def) {
        return new NonCheckingObserved<>(id, mandatory, def, false, null, null, null);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def, QuadConsumer<LeafTransaction, C, V, V> changed) {
        return new NonCheckingObserved<>(id, mandatory, def, false, null, null, changed);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def, Supplier<Setable<?, ?>> opposite) {
        return new NonCheckingObserved<>(id, mandatory, def, false, opposite, null, null);
    }

    protected NonCheckingObserved(Object id, boolean mandatory, T def, boolean containment, Supplier<Setable<?, ?>> opposite, Supplier<Setable<O, Set<?>>> scope, QuadConsumer<LeafTransaction, O, T, T> changed) {
        super(id, mandatory, def, containment, opposite, scope, changed, false);
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected void checkTooManyObservers(UniverseTransaction utx, Object object, DefaultMap<Observer, Set<Mutable>> observers) {
    }

}
