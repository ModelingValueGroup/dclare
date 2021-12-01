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

package org.modelingvalue.dclare;

import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Context;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.collections.util.QuadConsumer;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class Constant<O, T> extends Setable<O, T> {

    public static final Context<Pair<Object, Constant<?, ?>>> DERIVED = Context.of(null);

    public static <C, V> Constant<C, V> of(Object id, V def, SetableModifier... modifiers) {
        return new Constant<>(id, def, null, null, null, null, modifiers);
    }

    public static <C, V> Constant<C, V> of(Object id, V def, QuadConsumer<LeafTransaction, C, V, V> changed, SetableModifier... modifiers) {
        return new Constant<>(id, def, null, null, null, changed, modifiers);
    }

    public static <C, V> Constant<C, V> of(Object id, Function<C, V> deriver, SetableModifier... modifiers) {
        return new Constant<>(id, null, null, null, deriver, null, modifiers);
    }

    public static <C, V> Constant<C, V> of(Object id, V def, Function<C, V> deriver, SetableModifier... modifiers) {
        return new Constant<>(id, def, null, null, deriver, null, modifiers);
    }

    public static <C, V> Constant<C, V> of(Object id, Function<C, V> deriver, QuadConsumer<LeafTransaction, C, V, V> changed, SetableModifier... modifiers) {
        return new Constant<>(id, null, null, null, deriver, changed, modifiers);
    }

    public static <C, V> Constant<C, V> of(Object id, V def, Supplier<Setable<?, ?>> opposite, Function<C, V> deriver, SetableModifier... modifiers) {
        return new Constant<>(id, def, opposite, null, deriver, null, modifiers);
    }

    public static <C, V> Constant<C, V> of(Object id, V def, Supplier<Setable<?, ?>> opposite, Supplier<Setable<C, Set<?>>> scope, Function<C, V> deriver, SetableModifier... modifiers) {
        return new Constant<>(id, def, opposite, scope, deriver, null, modifiers);
    }

    private final Function<O, T> deriver;

    protected Constant(Object id, T def, Supplier<Setable<?, ?>> opposite, Supplier<Setable<O, Set<?>>> scope, Function<O, T> deriver, QuadConsumer<LeafTransaction, O, T, T> changed, SetableModifier... modifiers) {
        super(id, def, opposite, scope, changed, modifiers);
        this.deriver = deriver;
    }

    public Function<O, T> deriver() {
        return deriver;
    }

    @Override
    protected Constant<O, T> constant() {
        return this;
    }

    @Override
    public <E> T set(O object, BiFunction<T, E, T> function, E element) {
        LeafTransaction leafTransaction = LeafTransaction.getCurrent();
        ConstantState constants = leafTransaction.constantState();
        return constants.set(leafTransaction, object, this, function, element);
    }

    @Override
    public T set(O object, T value) {
        if (deriver != null) {
            throw new Error("Constant " + this + " is derived");
        }
        LeafTransaction leafTransaction = LeafTransaction.getCurrent();
        ConstantState constants = leafTransaction.constantState();
        return constants.set(leafTransaction, object, this, value, false);
    }

    public T force(O object, T value) {
        LeafTransaction leafTransaction = LeafTransaction.getCurrent();
        ConstantState constants = leafTransaction.constantState();
        return constants.set(leafTransaction, object, this, value, true);
    }

    @Override
    public T get(O object) {
        LeafTransaction leafTransaction = LeafTransaction.getCurrent();
        ConstantState constants = leafTransaction.constantState();
        return constants.get(leafTransaction, object, this);
    }

    public T get(O object, Function<O, T> deriver) {
        LeafTransaction leafTransaction = LeafTransaction.getCurrent();
        ConstantState constants = leafTransaction.constantState();
        return constants.get(leafTransaction, object, this, deriver);
    }

    public boolean isSet(O object) {
        LeafTransaction leafTransaction = LeafTransaction.getCurrent();
        ConstantState constants = leafTransaction.constantState();
        return constants.isSet(leafTransaction, object, this);
    }

    @Override
    public T pre(O object) {
        return get(object);
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected Entry<Setable, Object> entry(T value, DefaultMap<Setable, Object> properties) {
        return Entry.of(this, value);
    }

}
