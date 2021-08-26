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

import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.QualifiedSet;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Internable;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.Construction.Reason;
import org.modelingvalue.dclare.ex.ConsistencyError;
import org.modelingvalue.dclare.ex.ThrowableError;

public class Observer<O extends Mutable> extends Action<O> implements Internable {

    @SuppressWarnings("rawtypes")
    protected static final DefaultMap<Observer, Set<Mutable>> OBSERVER_MAP = DefaultMap.of(k -> Set.of());

    public static <M extends Mutable> Observer<M> of(Object id, Consumer<M> action) {
        return new Observer<M>(id, action, Priority.forward);
    }

    public static <M extends Mutable> Observer<M> of(Object id, Consumer<M> action, Function<M, Direction> direction) {
        return new Observer<M>(id, action, direction, Priority.forward);
    }

    public static <M extends Mutable> Observer<M> of(Object id, Consumer<M> action, Priority initPriority) {
        return new Observer<M>(id, action, initPriority);
    }

    public static <M extends Mutable> Observer<M> of(Object id, Consumer<M> action, Function<M, Direction> direction, Priority initPriority) {
        return new Observer<M>(id, action, direction, initPriority);
    }

    public final Traces                         traces;
    private final ExceptionSetable              exception;
    private final Observerds                    observeds;
    private final Constructed                   constructed;
    private final PreConstructed                preConstructed;

    private long                                runCount     = -1;
    private int                                 instances;
    private int                                 changes;
    private boolean                             stopped;

    @SuppressWarnings("rawtypes")
    private final Entry<Observer, Set<Mutable>> thisInstance = Entry.of(this, Mutable.THIS_SINGLETON);

    @SuppressWarnings("unchecked")
    protected Observer(Object id, Consumer<O> action, Priority initPriority) {
        this(id, action, (Function<O, Direction>) DEFAULT_DIRECTION_FUNCTION, initPriority);
    }

    protected Observer(Object id, Consumer<O> action, Function<O, Direction> direction, Priority initPriority) {
        super(id, action, direction, initPriority);
        traces = new Traces(Pair.of(this, "TRACES"));
        observeds = new Observerds(this);
        exception = ExceptionSetable.of(this);
        constructed = Constructed.of(this);
        preConstructed = PreConstructed.of(this);
    }

    public Observerds observeds() {
        return observeds;
    }

    public ExceptionSetable exception() {
        return exception;
    }

    public Constructed constructed() {
        return constructed;
    }

    public PreConstructed preConstructed() {
        return preConstructed;
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
        observeds.setDefault(mutable);
        for (Priority dir : Priority.values()) {
            dir.actions.setDefault(mutable);
            dir.children.setDefault(mutable);
        }
    }

    protected final void startTransaction(UniverseStatistics stats) {
        long runCount = stats.runCount();
        if (this.runCount != runCount) {
            this.runCount = runCount;
            this.changes = 0;
            this.stopped = false;
        }
    }

    protected final int countChangesPerInstance() {
        ++changes;
        return changesPerInstance();
    }

    protected final int changesPerInstance() {
        int i = instances;
        if (i <= 0) {
            instances = 1;
            return changes;
        } else {
            return changes / i;
        }
    }

    protected final void addInstance() {
        instances++;
    }

    protected final void removeInstance() {
        instances--;
    }

    protected final boolean isStopped() {
        return stopped;
    }

    protected final void stop() {
        stopped = true;
    }

    public static final class Traces extends Setable<Mutable, Set<ObserverTrace>> {
        protected Traces(Object id) {
            super(id, Set.of(), null, null, null);
        }
    }

    @SuppressWarnings("rawtypes")
    public static final class Observerds extends Setable<Mutable, DefaultMap<Observed, Set<Mutable>>> {

        @SuppressWarnings("unchecked")
        private Observerds(Observer observer) {
            super(observer, Observed.OBSERVED_MAP, null, null, (tx, mutable, pre, post) -> {
                for (Observed observed : Collection.concat(pre.toKeys(), post.toKeys()).distinct()) {
                    Setable<Mutable, DefaultMap<Observer, Set<Mutable>>> obs = observed.observers();
                    Setable.<Set<Mutable>, Mutable> diff(pre.get(observed), post.get(observed), a -> {
                        Mutable o = a.dResolve(mutable);
                        tx.set(o, obs, (m, e) -> m.add(e, Set::addAll), observer.entry(mutable, o));
                    }, r -> {
                        Mutable o = r.dResolve(mutable);
                        tx.set(o, obs, (m, e) -> m.remove(e, Set::removeAll), observer.entry(mutable, o));
                    });
                }
            }, CoreSetableModifier.plumbing);
        }

        public Observer observer() {
            return (Observer) id();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + ":" + super.toString();
        }

    }

    @SuppressWarnings("rawtypes")
    public static final class ExceptionSetable extends Setable<Mutable, Pair<Instant, Throwable>> {

        public static ExceptionSetable of(Observer observer) {
            return new ExceptionSetable(observer);
        }

        private final Observer observer;

        private ExceptionSetable(Observer observer) {
            super(Pair.of(observer, "exception"), null, null, null, null, CoreSetableModifier.plumbing);
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
        public Set<ConsistencyError> checkConsistency(State state, Mutable o, Pair<Instant, Throwable> p) {
            return p != null ? Set.of(new ThrowableError(o, observer, p.a(), p.b())) : Set.of();
        }
    }

    @SuppressWarnings("rawtypes")
    public static class Constructed extends Observed<Mutable, Map<Reason, Newable>> {

        public static Constructed of(Observer observer) {
            return new Constructed(observer);
        }

        private Constructed(Observer observer) {
            super(observer, Map.of(), null, null, (tx, o, pre, post) -> {
                for (Reason reason : Collection.concat(pre.toKeys(), post.toKeys()).distinct()) {
                    Newable before = pre.get(reason);
                    Newable after = post.get(reason);
                    if (!Objects.equals(before, after)) {
                        Construction cons = Construction.of(o, observer, reason);
                        if (before != null) {
                            Newable.D_DERIVED_CONSTRUCTIONS.set(before, QualifiedSet::remove, cons);
                        }
                        if (after != null) {
                            Newable.D_DERIVED_CONSTRUCTIONS.set(after, QualifiedSet::put, cons);
                        }
                    }
                }
            }, CoreSetableModifier.plumbing);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + ":" + super.toString();
        }
    }

    @SuppressWarnings("rawtypes")
    public static class PreConstructed extends Setable<Mutable, Map<Reason, Newable>> {

        public static PreConstructed of(Observer observer) {
            return new PreConstructed(observer);
        }

        private PreConstructed(Observer observer) {
            super(observer, Map.of(), null, null, null, CoreSetableModifier.plumbing);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + ":" + super.toString();
        }
    }

    @SuppressWarnings("rawtypes")
    private Entry entry(Mutable object, Mutable self) {
        return object.equals(self) ? thisInstance : Entry.of(this, Set.of(object));
    }

}