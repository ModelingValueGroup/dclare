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

import java.util.function.BiFunction;

import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Concurrent;
import org.modelingvalue.collections.util.Context;
import org.modelingvalue.dclare.Observer.Observerds;
import org.modelingvalue.dclare.ex.BackwardException;
import org.modelingvalue.dclare.ex.DeferException;
import org.modelingvalue.dclare.ex.NonDeterministicException;
import org.modelingvalue.dclare.ex.StopObserverException;
import org.modelingvalue.dclare.ex.TooManyChangesException;
import org.modelingvalue.dclare.ex.TooManyObservedException;

public class ObserverTransaction extends ActionTransaction {

    private static final boolean                                 TRACE_OBSERVERS = Boolean.getBoolean("TRACE_OBSERVERS");

    public static final Context<Boolean>                         OBSERVE         = Context.of(true);

    @SuppressWarnings("rawtypes")
    private final Concurrent<DefaultMap<Observed, Set<Mutable>>> getted          = Concurrent.of();
    @SuppressWarnings("rawtypes")
    private final Concurrent<DefaultMap<Observed, Set<Mutable>>> setted          = Concurrent.of();

    private boolean                                              changed;

    protected ObserverTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    public final Observer<?> observer() {
        return (Observer<?>) action();
    }

    @Override
    protected String traceId() {
        return "observer";
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void run(State pre, UniverseTransaction universeTransaction) {
        Observer<?> observer = observer();
        try {
            // check if the universe is still in the same transaction, if not: reset my state
            long rootCount = universeTransaction.stats().runCount();
            if (observer.runCount != rootCount) {
                observer.runCount = rootCount;
                observer.changes = 0;
                observer.stopped = false;
            }
            // check if we should do the work...
            if (!observer.stopped && !universeTransaction.isKilled()) {
                getted.init(Observed.OBSERVED_MAP);
                setted.init(Observed.OBSERVED_MAP);
                super.run(pre, universeTransaction);
                observe(pre, observer, setted.result(), getted.result());
            }
        } catch (DeferException de) {
            observe(pre, observer, setted.result(), getted.result());
        } catch (BackwardException be) {
            trigger(mutable(), (Observer<Mutable>) observer(), Direction.backward);
            observe(pre, observer, setted.result(), getted.result());
        } catch (StopObserverException soe) {
            observe(pre, observer, Observed.OBSERVED_MAP, Observed.OBSERVED_MAP);
        } finally {
            changed = false;
            getted.clear();
            setted.clear();
        }
    }

    @SuppressWarnings("rawtypes")
    private void observe(State pre, Observer<?> observer, DefaultMap<Observed, Set<Mutable>> sets, DefaultMap<Observed, Set<Mutable>> gets) {
        if (changed) {
            checkTooManyChanges(pre, sets, gets);
        }
        gets = gets.removeAll(sets, Set::removeAll);
        Mutable mutable = mutable();
        Observerds[] observeds = observer.observeds();
        DefaultMap<Observed, Set<Mutable>> oldGets = observeds[Direction.forward.nr].set(mutable, gets);
        DefaultMap<Observed, Set<Mutable>> oldSets = observeds[Direction.backward.nr].set(mutable, sets);
        if (oldGets.isEmpty() && oldSets.isEmpty() && !(sets.isEmpty() && gets.isEmpty())) {
            observer.instances++;
        } else if (!(oldGets.isEmpty() && oldSets.isEmpty()) && sets.isEmpty() && gets.isEmpty()) {
            observer.instances--;
        }
        checkTooManyObserved(sets, gets);
    }

    @SuppressWarnings("rawtypes")
    protected void checkTooManyObserved(DefaultMap<Observed, Set<Mutable>> sets, DefaultMap<Observed, Set<Mutable>> gets) {
        if (universeTransaction().stats().maxNrOfObserved() < size(gets) + size(sets)) {
            throw new TooManyObservedException(mutable(), observer(), gets.addAll(sets, Set::addAll), universeTransaction());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void checkTooManyChanges(State pre, DefaultMap<Observed, Set<Mutable>> sets, DefaultMap<Observed, Set<Mutable>> gets) {
        UniverseTransaction universeTransaction = universeTransaction();
        Observer<?> observer = observer();
        Mutable mutable = mutable();
        if (TRACE_OBSERVERS) {
            State result = result();
            init(result);
            System.err.println("DCLARE: " + parent().indent("    ") + mutable + "." + observer() + " ("//
                    + toString(gets, mutable, (m, o) -> pre.get(m, o)) + " " + toString(sets, mutable, (m, o) -> pre.get(m, o) + "->" + result.get(m, o)) + ")");
        }
        if (universeTransaction.stats().debugging()) {
            State post = result();
            init(post);
            Set<ObserverTrace> traces = observer.traces.get(mutable);
            ObserverTrace trace = new ObserverTrace(mutable, observer, traces.sorted().findFirst().orElse(null), observer.changesPerInstance(), //
                    gets.addAll(sets, Set::addAll).flatMap(e -> e.getValue().map(m -> {
                        m = m.resolve(mutable);
                        return Entry.of(ObservedInstance.of(m, e.getKey()), pre.get(m, e.getKey()));
                    })).toMap(e -> e), //
                    sets.flatMap(e -> e.getValue().map(m -> {
                        m = m.resolve(mutable);
                        return Entry.of(ObservedInstance.of(m, e.getKey()), post.get(m, e.getKey()));
                    })).toMap(e -> e));
            observer.traces.set(mutable, traces.add(trace));
        }
        int totalChanges = universeTransaction.stats().bumpAndGetTotalChanges();
        int changesPerInstance = observer.countChangesPerInstance();
        if (changesPerInstance > universeTransaction.stats().maxNrOfChanges()) {
            universeTransaction.stats().setDebugging(true);
            if (changesPerInstance > universeTransaction.stats().maxNrOfChanges() * 2) {
                hadleTooManyChanges(universeTransaction, mutable, observer, changesPerInstance);
            }
        } else if (totalChanges > universeTransaction.stats().maxTotalNrOfChanges()) {
            universeTransaction.stats().setDebugging(true);
            if (totalChanges > universeTransaction.stats().maxTotalNrOfChanges() + universeTransaction.stats().maxNrOfChanges()) {
                hadleTooManyChanges(universeTransaction, mutable, observer, totalChanges);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static String toString(DefaultMap<Observed, Set<Mutable>> sets, Mutable self, BiFunction<Mutable, Observed, Object> value) {
        return sets.reduce("", (r1, e) -> (r1.isEmpty() ? "" : r1 + " ") + e.getValue().reduce("", (r2, m) -> //
        (m != Mutable.THIS ? m + "." : "") + e.getKey() + "=" + value.apply(m.resolve(self), e.getKey()), //
                (a, b) -> a + " " + b), (a, b) -> a + " " + b);
    }

    private void hadleTooManyChanges(UniverseTransaction universeTransaction, Mutable mutable, Observer<?> observer, int changes) {
        State result = result();
        init(result);
        ObserverTrace last = result.get(mutable, observer.traces).sorted().findFirst().orElse(null);
        if (last != null && last.done().size() >= (changes > universeTransaction.stats().maxTotalNrOfChanges() ? 1 : universeTransaction.stats().maxNrOfChanges())) {
            getted.init(Observed.OBSERVED_MAP);
            setted.init(Observed.OBSERVED_MAP);
            observer.stopped = true;
            throw new TooManyChangesException(result, last, changes);
        }
    }

    @SuppressWarnings("rawtypes")
    protected boolean countChanges(Observed observed) {
        boolean old = changed;
        changed = true;
        return !old;
    }

    @Override
    public <O, T> T get(O object, Getable<O, T> property) {
        if (property instanceof Observed && Constant.DERIVED.get() != null && ObserverTransaction.OBSERVE.get()) {
            throw new NonDeterministicException(object, property, "Reading observed '" + property + "' while initializing constant '" + Constant.DERIVED.get() + "'");
        }
        observe(object, property, false);
        return super.get(object, property);
    }

    @Override
    public <O, T> T pre(O object, Getable<O, T> property) {
        observe(object, property, false);
        return super.pre(object, property);
    }

    @Override
    public <O, T> T set(O object, Setable<O, T> property, T value) {
        observe(object, property, true);
        return super.set(object, property, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <O, T> void observe(O object, Getable<O, T> property, boolean set) {
        if (object instanceof Mutable && property instanceof Observed && getted.isInitialized() && OBSERVE.get()) {
            (set ? setted : getted).change(o -> o.add(((Observed) property).entry((Mutable) object, mutable()), Set::addAll));
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void handleMergeConflict(Object object, Setable property, Object pre, Object... branches) {
    }

    @Override
    public void runNonObserving(Runnable action) {
        if (getted.isInitialized()) {
            OBSERVE.run(false, action);
        } else {
            super.runNonObserving(action);
        }

    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    protected <O, T> void changed(O object, Setable<O, T> setable, T preValue, T postValue) {
        runNonObserving(() -> super.changed(object, setable, preValue, postValue));
        if (object instanceof Mutable && setable instanceof Observed && getted.isInitialized() && OBSERVE.get() && countChanges((Observed) setable)) {
            trigger(mutable(), (Observer<Mutable>) observer(), Direction.backward);
        }
    }

}
