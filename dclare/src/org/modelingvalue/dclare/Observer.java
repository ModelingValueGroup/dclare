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

import java.time.Instant;
import java.util.function.Consumer;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.ex.ThrowableError;

public class Observer<O extends Mutable> extends Action<O> implements Feature {

    @SuppressWarnings("rawtypes")
    protected static final DefaultMap<Observer, Set<Mutable>> OBSERVER_MAP = DefaultMap.of(k -> Set.of());

    public static <M extends Mutable> Observer<M> of(Object id, Consumer<M> action) {
        return new Observer<>(id, action, Direction.forward);
    }

    public static <M extends Mutable> Observer<M> of(Object id, Consumer<M> action, Direction initDirection) {
        return new Observer<>(id, action, initDirection);
    }

    public final Setable<Mutable, Set<ObserverTrace>> traces;
    public final ExceptionSetable                     exception;
    private final Observerds[]                        observeds;

    protected long                                    runCount     = -1;
    protected int                                     instances;
    protected int                                     changes;
    protected boolean                                 stopped;
    @SuppressWarnings("rawtypes")
    private final Entry<Observer, Set<Mutable>>       thisInstance = Entry.of(this, Mutable.THIS_SINGLETON);

    protected Observer(Object id, Consumer<O> action, Direction initDirection) {
        super(id, action, initDirection);
        this.traces = Setable.of(Pair.of(this, "TRACES"), Set.of());
        observeds = new Observerds[2];
        for (int ia = 0; ia < 2; ia++) {
            observeds[ia] = Observerds.of(this, Direction.FORWARD_BACKWARD[ia]);
        }
        exception = ExceptionSetable.of(this);
    }

    public Observerds[] observeds() {
        return observeds;
    }

    public ExceptionSetable exception() {
        return exception;
    }

    public int countChangesPerInstance() {
        ++changes;
        return changesPerInstance();
    }

    @Override
    public ObserverTransaction openTransaction(MutableTransaction parent) {
        return parent.universeTransaction().observerTransactions.get().open(this, parent);
    }

    @Override
    public void closeTransaction(Transaction tx) {
        tx.universeTransaction().observerTransactions.get().close((ObserverTransaction) tx);
    }

    @Override
    public ObserverTransaction newTransaction(UniverseTransaction universeTransaction) {
        return new ObserverTransaction(universeTransaction);
    }

    public void deObserve(O mutable) {
        for (int ia = 0; ia < 2; ia++) {
            observeds[ia].setDefault(mutable);
        }
        for (Direction dir : Direction.values()) {
            dir.actions.setDefault(mutable);
            dir.children.setDefault(mutable);
        }
    }

    public int changesPerInstance() {
        int i = instances;
        if (i <= 0) {
            instances = 1;
            return changes;
        } else {
            return changes / i;
        }
    }

    @SuppressWarnings("rawtypes")
    public static final class Observerds extends Setable<Mutable, DefaultMap<Observed, Set<Mutable>>> {

        public static Observerds of(Observer observer, Direction direction) {
            return new Observerds(observer, direction);
        }

        @SuppressWarnings("unchecked")
        private Observerds(Observer observer, Direction direction) {
            super(Pair.of(observer, direction), Observed.OBSERVED_MAP, false, null, null, (tx, mutable, pre, post) -> {
                for (Observed observed : Collection.concat(pre.toKeys(), post.toKeys()).distinct()) {
                    Setable<Mutable, DefaultMap<Observer, Set<Mutable>>> obs = observed.observers(direction);
                    Setable.<Set<Mutable>, Mutable> diff(pre.get(observed), post.get(observed), a -> {
                        Mutable o = a.resolve(mutable);
                        tx.set(o, obs, (m, e) -> m.add(e, Set::addAll), observer.entry(mutable, o));
                    }, r -> {
                        Mutable o = r.resolve(mutable);
                        tx.set(o, obs, (m, e) -> m.remove(e, Set::removeAll), observer.entry(mutable, o));
                    });
                }
            }, false);

        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + super.toString().substring(4);
        }

    }

    @SuppressWarnings("rawtypes")
    public static final class ExceptionSetable extends Setable<Mutable, Pair<Instant, Throwable>> {

        public static ExceptionSetable of(Observer observer) {
            return new ExceptionSetable(observer);
        }

        private final Observer observer;

        private ExceptionSetable(Observer observer) {
            super(Pair.of(observer, "exception"), null, false, null, null, null, false);
            this.observer = observer;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + super.toString().substring(4);
        }

        @Override
        public boolean checkConsistency() {
            return true;
        }

        public Observer observer() {
            return observer;
        }

        @Override
        public void checkConsistency(State state, Mutable o, Pair<Instant, Throwable> p) {
            if (p != null) {
                throw new ThrowableError(o, observer, p.a(), p.b());
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private Entry entry(Mutable object, Mutable self) {
        return object.equals(self) ? thisInstance : Entry.of(this, Set.of(object));
    }

}
