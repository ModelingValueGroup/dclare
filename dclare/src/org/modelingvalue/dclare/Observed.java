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

package org.modelingvalue.dclare;

import static org.modelingvalue.dclare.Direction.backward;
import static org.modelingvalue.dclare.Direction.forward;

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

    public static <C, V> Observed<C, V> of(Object id, V def) {
        return new Observed<>(id, false, def, false, null, null, null, true);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, boolean containment) {
        return new Observed<>(id, false, def, containment, null, null, null, true);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, QuadConsumer<LeafTransaction, C, V, V> changed) {
        return new Observed<>(id, false, def, false, null, null, changed, true);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, boolean containment, boolean checkConsistency) {
        return new Observed<>(id, false, def, containment, null, null, null, checkConsistency);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, Supplier<Setable<?, ?>> opposite) {
        return new Observed<>(id, false, def, false, opposite, null, null, true);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, Supplier<Setable<?, ?>> opposite, Supplier<Setable<C, Set<?>>> scope, boolean checkConsistency) {
        return new Observed<>(id, false, def, false, opposite, scope, null, checkConsistency);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def) {
        return new Observed<>(id, mandatory, def, false, null, null, null, true);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def, boolean containment) {
        return new Observed<>(id, mandatory, def, containment, null, null, null, true);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def, boolean containment, boolean checkConsistency) {
        return new Observed<>(id, mandatory, def, containment, null, null, null, checkConsistency);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def, QuadConsumer<LeafTransaction, C, V, V> changed) {
        return new Observed<>(id, mandatory, def, false, null, null, changed, true);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def, Supplier<Setable<?, ?>> opposite) {
        return new Observed<>(id, mandatory, def, false, opposite, null, null, true);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def, Supplier<Setable<?, ?>> opposite, Supplier<Setable<C, Set<?>>> scope, boolean checkConsistency) {
        return new Observed<>(id, mandatory, def, false, opposite, scope, null, checkConsistency);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def, boolean containment, Supplier<Setable<?, ?>> opposite, Supplier<Setable<C, Set<?>>> scope, boolean checkConsistency) {
        return new Observed<>(id, mandatory, def, containment, opposite, scope, null, checkConsistency);
    }

    private final Setable<Object, Set<ObserverTrace>> readers      = Setable.of(Pair.of(this, "readers"), Set.of());
    private final Setable<Object, Set<ObserverTrace>> writers      = Setable.of(Pair.of(this, "writers"), Set.of());
    private boolean                                   mandatory;
    private final Observers<O, T>[]                   observers;
    @SuppressWarnings("rawtypes")
    private final Entry<Observed, Set<Mutable>>       thisInstance = Entry.of(this, Mutable.THIS_SINGLETON);

    @SuppressWarnings("unchecked")
    protected Observed(Object id, boolean mandatory, T def, boolean containment, Supplier<Setable<?, ?>> opposite, Supplier<Setable<O, Set<?>>> scope, QuadConsumer<LeafTransaction, O, T, T> changed, boolean checkConsistency) {
        this(id, mandatory, def, containment, opposite, scope, newObservers(id), changed, checkConsistency);
    }

    @SuppressWarnings("rawtypes")
    private static Observers[] newObservers(Object id) {
        assert forward.nr == 0;
        assert backward.nr == 1;
        return new Observers[]{new Observers<>(id, forward), new Observers<>(id, backward),};
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Observed(Object id, boolean mandatory, T def, boolean containment, Supplier<Setable<?, ?>> opposite, Supplier<Setable<O, Set<?>>> scope, Observers<O, T>[] observers, QuadConsumer<LeafTransaction, O, T, T> changed, boolean checkConsistency) {
        super(id, def, containment, opposite, scope, (l, o, p, n) -> {
            if (changed != null) {
                changed.accept(l, o, p, n);
            }
            Mutable source = l.mutable();
            for (Direction dir : Direction.FORWARD_BACKWARD) {
                for (Entry<Observer, Set<Mutable>> e : l.get(o, observers[dir.nr])) {
                    Observer observer = e.getKey();
                    for (Mutable m : e.getValue()) {
                        Mutable target = m.resolve((Mutable) o);
                        if (!l.cls().equals(observer) || !source.equals(target)) {
                            l.trigger(target, observer, dir);
                        }
                    }
                }
            }
        }, checkConsistency);
        this.mandatory = mandatory;
        this.observers = observers;
        observers[forward.nr].observed = this;
        observers[backward.nr].observed = this;
    }

    @SuppressWarnings("rawtypes")
    protected void checkTooManyObservers(UniverseTransaction utx, Object object, DefaultMap<Observer, Set<Mutable>> observers) {
        if (utx.stats().maxNrOfObservers() < LeafTransaction.size(observers)) {
            throw new TooManyObserversException(object, this, observers, utx);
        }
    }

    public Observers<O, T> observers(Direction direction) {
        return observers[direction.nr];
    }

    public Observers<O, T>[] observers() {
        return observers;
    }

    public boolean mandatory() {
        return mandatory;
    }

    public Setable<Object, Set<ObserverTrace>> readers() {
        return readers;
    }

    public Setable<Object, Set<ObserverTrace>> writers() {
        return writers;
    }

    public int getNrOfObservers(O object) {
        LeafTransaction leafTransaction = LeafTransaction.getCurrent();
        return leafTransaction.get(object, observers[forward.nr]).size() + leafTransaction.get(object, observers[backward.nr]).size();
    }

    @SuppressWarnings("rawtypes")
    public static final class Observers<O, T> extends Setable<O, DefaultMap<Observer, Set<Mutable>>> {

        private Observed<O, T>  observed; // can not be made final because it has to be set after construction
        private final Direction direction;

        private Observers(Object id, Direction direction) {
            super(Pair.of(id, direction), Observer.OBSERVER_MAP, false, null, null, null, false);
            // changed can not be passed as arg above because it references 'observed'
            changed = (tx, o, b, a) -> observed.checkTooManyObservers(tx.universeTransaction(), o, a);
            this.direction = direction;
        }

        public Observed<O, T> observed() {
            return observed;
        }

        public Direction direction() {
            return direction;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + super.toString().substring(4);
        }

    }

    @SuppressWarnings("rawtypes")
    protected Entry<Observed, Set<Mutable>> entry(Mutable object, Mutable self) {
        return object.equals(self) ? thisInstance : Entry.of(this, Set.of(object));
    }

    @Override
    public boolean checkConsistency() {
        return checkConsistency && (mandatory || super.checkConsistency());
    }

    @Override
    public void checkConsistency(State state, O object, T post) {
        if (super.checkConsistency()) {
            super.checkConsistency(state, object, post);
        }
        if (checkConsistency && isEmpty(post)) {
            handleEmptyCheck(object, post);
        }
    }

    @SuppressWarnings("rawtypes")
    protected boolean isEmpty(T result) {
        return mandatory && (result == null || (result instanceof ContainingCollection && ((ContainingCollection) result).isEmpty()));
    }

    protected void handleEmptyCheck(O object, T value) {
        throw new EmptyMandatoryException(object, this);
    }

}
