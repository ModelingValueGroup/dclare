//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2022 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class Observer<O extends Mutable> extends Action<O> implements Internable {

    public static final Observer<Mutable>                     DUMMY        = new Observer<>("<dummy>", o -> {
                                                                           }, Priority.immediate);

    @SuppressWarnings("rawtypes")
    protected static final DefaultMap<Observer, Set<Mutable>> OBSERVER_MAP = DefaultMap.of(k -> Set.of());

    public static <M extends Mutable> Observer<M> of(Object id, Consumer<M> action, Set<Setable<M, ?>> targets, LeafModifier... modifiers) {
        return new Observer<M>(id, action, targets, modifiers);
    }

    public static <M extends Mutable> Observer<M> of(Object id, Consumer<M> action, LeafModifier... modifiers) {
        return new Observer<M>(id, action, modifiers);
    }

    public static <M extends Mutable> Observer<M> of(Object id, Predicate<M> predicate, Consumer<M> action, LeafModifier... modifiers) {
        return new Observer<M>(id, predicate, action, modifiers);
    }

    @SuppressWarnings("unchecked")
    public static <M extends Mutable, V> Observer<M> of(Object id, Setable<M, V> setable, Function<M, V> value, LeafModifier... modifiers) {
        return new Observer<M>(id, setable, value, modifiers);
    }

    @SuppressWarnings("unchecked")
    public static <M extends Mutable, V> Observer<M> of(Object id, Setable<M, V> setable, Predicate<M> predicate, Function<M, V> value, LeafModifier... modifiers) {
        return new Observer<M>(id, setable, predicate, value, modifiers);
    }

    public final Traces                         traces;
    private final ExceptionSetable              exception;
    private final Observerds                    observeds;
    private final Constructed                   constructed;
    @SuppressWarnings("rawtypes")
    private final Set<Setable<O, ?>>            targets;
    private final boolean                       anonymous;

    private long                                runCount     = -1;
    private int                                 instances;
    private int                                 changes;
    private boolean                             stopped;

    @SuppressWarnings("rawtypes")
    private final Entry<Observer, Set<Mutable>> thisInstance = Entry.of(this, Mutable.THIS_SINGLETON);

    protected Observer(Object id, Consumer<O> action, LeafModifier... modifiers) {
        this(id, action, Set.of(), modifiers);
    }

    protected Observer(Object id, Predicate<O> predicate, Consumer<O> action, LeafModifier... modifiers) {
        this(id, o -> {
            if (predicate.test(o)) {
                action.accept(o);
            }
        }, Set.of(), modifiers);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected <T> Observer(Object id, Setable<O, T> setable, Function<O, T> value, LeafModifier... modifiers) {
        this(id, o -> setable.set(o, value.apply(o)), Set.of(setable), modifiers);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected <T> Observer(Object id, Setable<O, T> setable, Predicate<O> predicate, Function<O, T> value, LeafModifier... modifiers) {
        this(id, o -> {
            if (predicate.test(o)) {
                setable.set(o, value.apply(o));
            }
        }, Set.of(setable), modifiers);
    }

    @SuppressWarnings("rawtypes")
    protected Observer(Object id, Consumer<O> action, Set<Setable<O, ?>> targets, LeafModifier... modifiers) {
        super(id, action, modifiers);
        traces = new Traces(Pair.of(this, "TRACES"));
        observeds = new Observerds(this);
        exception = ExceptionSetable.of(this);
        constructed = Constructed.of(this);
        this.targets = targets;
        this.anonymous = LeafModifier.anonymous.in(modifiers);
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
        constructed.setDefault(mutable);
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

        @Override
        protected boolean deduplicate(Set<ObserverTrace> value) {
            return false;
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
            }, SetableModifier.plumbing);
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
            super(Pair.of(observer, "exception"), null, null, null, null, SetableModifier.plumbing);
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
        protected boolean deduplicate(Pair<Instant, Throwable> value) {
            return false;
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

        @SuppressWarnings("unchecked")
        private Constructed(Observer observer) {
            super(observer, Map.of(), null, null, (tx, o, pre, post) -> {
                for (Reason reason : Collection.concat(pre.toKeys(), post.toKeys()).distinct()) {
                    Newable before = pre.get(reason);
                    Newable after = post.get(reason);
                    if (!Objects.equals(before, after)) {
                        Construction cons = Construction.of(o, observer, reason);
                        if (before != null) {
                            if (tx.leaf() instanceof Observer && tx.universeTransaction().getConfig().isTraceMatching()) {
                                System.err.println(LeafTransaction.getTraceLineStart("MATCH", tx.parent().depth()) + tx.mutable() + "." + tx.leaf() + " (" + reason + "<=" + before + ")");
                            }
                            Newable.D_DERIVED_CONSTRUCTIONS.set(before, QualifiedSet::remove, cons);
                        }
                        if (after != null) {
                            if (tx.leaf() instanceof Observer && tx.universeTransaction().getConfig().isTraceMatching()) {
                                System.err.println(LeafTransaction.getTraceLineStart("MATCH", tx.parent().depth()) + tx.mutable() +"." + tx.leaf() + " (" + reason + "=>" + after + ")");
                            }
                            Newable.D_DERIVED_CONSTRUCTIONS.set(after, QualifiedSet::put, cons);
                        }
                    }
                }
            }, SetableModifier.plumbing, SetableModifier.doNotMerge);
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

    @SuppressWarnings("rawtypes")
    public Set<Setable<O, ?>> targets() {
        return targets;
    }

    public boolean anonymous() {
        return anonymous;
    }

}
