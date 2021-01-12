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

import java.util.function.Supplier;

import org.modelingvalue.collections.ContainingCollection;
import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.collections.util.QuadConsumer;
import org.modelingvalue.dclare.ex.EmptyMandatoryException;
import org.modelingvalue.dclare.ex.TooManyObserversException;

public class Observed<O, T> extends Setable<O, T> {

    @SuppressWarnings("rawtypes")
    protected static final DefaultMap<Observed, Set<Mutable>> OBSERVED_MAP = DefaultMap.of(k -> Set.of());

    public static <C, V> Observed<C, V> of(Object id, V def, SetableModifier... modifiers) {
        return new Observed<>(id, def, null, null, null, modifiers);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, QuadConsumer<LeafTransaction, C, V, V> changed, SetableModifier... modifiers) {
        return new Observed<>(id, def, null, null, changed, modifiers);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, Supplier<Setable<?, ?>> opposite, SetableModifier... modifiers) {
        return new Observed<>(id, def, opposite, null, null, modifiers);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, QuadConsumer<LeafTransaction, C, V, V> changed, Supplier<Setable<C, Set<?>>> scope, SetableModifier... modifiers) {
        return new Observed<>(id, def, null, scope, changed, modifiers);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, Supplier<Setable<?, ?>> opposite, Supplier<Setable<C, Set<?>>> scope, SetableModifier... modifiers) {
        return new Observed<>(id, def, opposite, scope, null, modifiers);
    }

    private final Setable<Object, Set<ObserverTrace>> readers      = Setable.of(Pair.of(this, "readers"), Set.of());
    private final Setable<Object, Set<ObserverTrace>> writers      = Setable.of(Pair.of(this, "writers"), Set.of());
    private final boolean                             mandatory;
    private final boolean                             checkMandatory;
    private final Observers<O, T>                     observers;
    @SuppressWarnings("rawtypes")
    private final Entry<Observed, Set<Mutable>>       thisInstance = Entry.of(this, Mutable.THIS_SINGLETON);

    @SuppressWarnings("unchecked")
    protected Observed(Object id, T def, Supplier<Setable<?, ?>> opposite, Supplier<Setable<O, Set<?>>> scope, QuadConsumer<LeafTransaction, O, T, T> changed, SetableModifier... modifiers) {
        this(id, def, opposite, scope, new Observers<>(id), changed, modifiers);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Observed(Object id, T def, Supplier<Setable<?, ?>> opposite, Supplier<Setable<O, Set<?>>> scope, Observers<O, T> observers, QuadConsumer<LeafTransaction, O, T, T> changed, SetableModifier... modifiers) {
        super(id, def, opposite, scope, (l, o, p, n) -> {
            if (changed != null) {
                changed.accept(l, o, p, n);
            }
            if (o instanceof Mutable) {
                l.setChanged((Mutable) o);
            }
            Mutable source = l.mutable();
            for (Entry<Observer, Set<Mutable>> e : l.get(o, observers)) {
                Observer observer = e.getKey();
                for (Mutable m : e.getValue()) {
                    Mutable target = m.resolve((Mutable) o);
                    if (!l.cls().equals(observer) || !source.equals(target)) {
                        l.trigger(target, observer, Direction.forward);
                    }
                }
            }
        }, modifiers);
        this.mandatory = hasModifier(modifiers, SetableModifier.mandatory);
        this.checkMandatory = !hasModifier(modifiers, SetableModifier.doNotCheckMandatory);
        this.observers = observers;
        observers.observed = this;
    }

    @SuppressWarnings("rawtypes")
    protected void checkTooManyObservers(UniverseTransaction utx, Object object, DefaultMap<Observer, Set<Mutable>> observers) {
        if (utx.stats().maxNrOfObservers() < LeafTransaction.size(observers)) {
            throw new TooManyObserversException(object, this, observers, utx);
        }
    }

    public Observers<O, T> observers() {
        return observers;
    }

    public boolean mandatory() {
        return mandatory;
    }

    public boolean checkMandatory() {
        return checkMandatory;
    }

    public Setable<Object, Set<ObserverTrace>> readers() {
        return readers;
    }

    public Setable<Object, Set<ObserverTrace>> writers() {
        return writers;
    }

    public int getNrOfObservers(O object) {
        return LeafTransaction.getCurrent().get(object, observers).size();
    }

    @SuppressWarnings("rawtypes")
    public static final class Observers<O, T> extends Setable<O, DefaultMap<Observer, Set<Mutable>>> {

        private Observed<O, T> observed; // can not be made final because it has to be set after construction

        private Observers(Object id) {
            super(id, Observer.OBSERVER_MAP, null, null, null, SetableModifier.doNotCheckConsistency);
            // changed can not be passed as arg above because it references 'observed'
            changed = (tx, o, b, a) -> observed.checkTooManyObservers(tx.universeTransaction(), o, a);
        }

        public Observed<O, T> observed() {
            return observed;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + super.toString();
        }

    }

    @SuppressWarnings("rawtypes")
    protected Entry<Observed, Set<Mutable>> entry(Mutable object, Mutable self) {
        return object.equals(self) ? thisInstance : Entry.of(this, Set.of(object));
    }

    @Override
    public boolean checkConsistency() {
        return checkConsistency && ((mandatory && checkMandatory) || super.checkConsistency());
    }

    @Override
    public void checkConsistency(State state, O object, T post) {
        if (super.checkConsistency()) {
            super.checkConsistency(state, object, post);
        }
        if (checkConsistency && checkMandatory && mandatory && isEmpty(post)) {
            throw new EmptyMandatoryException(object, this);
        }
    }

    @SuppressWarnings("rawtypes")
    protected boolean isEmpty(T result) {
        return result == null || (result instanceof ContainingCollection && ((ContainingCollection) result).isEmpty());
    }

}
