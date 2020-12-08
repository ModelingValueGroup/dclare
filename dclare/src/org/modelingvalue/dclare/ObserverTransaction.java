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
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Concurrent;
import org.modelingvalue.collections.util.Context;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.Observer.Observerds;
import org.modelingvalue.dclare.ex.ConsistencyError;
import org.modelingvalue.dclare.ex.NonDeterministicException;
import org.modelingvalue.dclare.ex.TooManyChangesException;
import org.modelingvalue.dclare.ex.TooManyObservedException;

public class ObserverTransaction extends ActionTransaction {

    private static final Set<Boolean>                            FALSE           = Set.of();
    private static final Set<Boolean>                            TRUE            = Set.of(true);

    private static final boolean                                 TRACE_OBSERVERS = Boolean.getBoolean("TRACE_OBSERVERS");

    public static final Context<Boolean>                         OBSERVE         = Context.of(true);

    @SuppressWarnings("rawtypes")
    private final Concurrent<DefaultMap<Observed, Set<Mutable>>> getted          = Concurrent.of();
    @SuppressWarnings("rawtypes")
    private final Concurrent<DefaultMap<Observed, Set<Mutable>>> setted          = Concurrent.of();

    private final Concurrent<Set<Boolean>>                       emptyMandatory  = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       hasChanged      = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       isDestructive   = Concurrent.of();

    protected ObserverTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    public Observer<?> observer() {
        return (Observer<?>) action();
    }

    @Override
    protected String traceId() {
        return "observer";
    }

    @SuppressWarnings("unchecked")
    @Override
    protected final void run(State pre, UniverseTransaction universeTransaction) {
        Observer<?> observer = observer();
        Pair<Instant, Throwable> throwable = null;
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
            emptyMandatory.init(FALSE);
            hasChanged.init(FALSE);
            isDestructive.init(FALSE);
            try {
                doRun(pre, universeTransaction);
            } catch (Throwable t) {
                do {
                    if (t instanceof ConsistencyError) {
                        throw (ConsistencyError) t;
                    } else if (t instanceof NullPointerException) {
                        if (emptyMandatory.result().equals(FALSE)) {
                            throwable = Pair.of(Instant.now(), t);
                        }
                        return;
                    } else if (t.getCause() != null) {
                        t = t.getCause();
                    } else {
                        throwable = Pair.of(Instant.now(), t);
                        return;
                    }
                } while (true);
            } finally {
                observe(pre, observer, setted.result(), getted.result());
                observer.exception.set(mutable(), throwable);
                emptyMandatory.clear();
                hasChanged.clear();
                isDestructive.clear();
            }
        }
    }

    protected void doRun(State pre, UniverseTransaction universeTransaction) {
        super.run(pre, universeTransaction);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void observe(State pre, Observer<?> observer, DefaultMap<Observed, Set<Mutable>> sets, DefaultMap<Observed, Set<Mutable>> gets) {
        gets = gets.removeAll(sets, Set::removeAll);
        Mutable mutable = mutable();
        Observerds[] observeds = observer.observeds();
        boolean firstTime = pre.get(mutable, observeds[Direction.forward.nr]).isEmpty() && pre.get(mutable, observeds[Direction.backward.nr]).isEmpty();
        boolean lastTime = sets.isEmpty() && gets.isEmpty();
        if (firstTime && !lastTime && isDestructive.result().equals(TRUE)) {
            clearState();
            trigger(mutable(), (Observer<Mutable>) observer, Direction.backward);
        } else if (hasChanged.result().equals(TRUE)) {
            checkTooManyChanges(pre, sets, gets);
            trigger(mutable(), (Observer<Mutable>) observer, Direction.backward);
        }
        super.set(mutable, observeds[Direction.forward.nr], gets);
        super.set(mutable, observeds[Direction.backward.nr], sets);
        if (firstTime && !lastTime) {
            observer.instances++;
        } else if (!firstTime && lastTime) {
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
            State result = resultState();
            System.err.println("DCLARE: " + parent().indent("    ") + mutable + "." + observer() + " ("//
                    + toString(gets, mutable, (m, o) -> pre.get(m, o)) + " " + toString(sets, mutable, (m, o) -> pre.get(m, o) + "->" + result.get(m, o)) + ")");
        }
        if (universeTransaction.stats().debugging()) {
            State result = resultState();
            Set<ObserverTrace> traces = observer.traces.get(mutable);
            ObserverTrace trace = new ObserverTrace(mutable, observer, traces.sorted().findFirst().orElse(null), observer.changesPerInstance(), //
                    gets.addAll(sets, Set::addAll).filter(e -> !(e.getKey() instanceof NonCheckingObserved)).flatMap(e -> e.getValue().map(m -> {
                        m = m.resolve(mutable);
                        return Entry.of(ObservedInstance.of(m, e.getKey()), pre.get(m, e.getKey()));
                    })).toMap(e -> e), //
                    sets.filter(e -> !(e.getKey() instanceof NonCheckingObserved)).flatMap(e -> e.getValue().map(m -> {
                        m = m.resolve(mutable);
                        return Entry.of(ObservedInstance.of(m, e.getKey()), result.get(m, e.getKey()));
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
        State result = resultState();
        ObserverTrace last = result.get(mutable, observer.traces).sorted().findFirst().orElse(null);
        if (last != null && last.done().size() >= (changes > universeTransaction.stats().maxTotalNrOfChanges() ? 1 : universeTransaction.stats().maxNrOfChanges())) {
            observer.stopped = true;
            throw new TooManyChangesException(result, last, changes);
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <O, T> T get(O object, Getable<O, T> property) {
        if (property instanceof Observed && Constant.DERIVED.get() != null && ObserverTransaction.OBSERVE.get()) {
            throw new NonDeterministicException(Constant.DERIVED.get().a(), Constant.DERIVED.get().b(), "Reading observed '" + object + "." + property + //
                    "' while initializing constant '" + Constant.DERIVED.get().a() + "." + Constant.DERIVED.get().b() + "'");
        }
        observe(object, property, false);
        T result = super.get(object, property);
        if (result == null && property instanceof Observed && ((Observed) property).mandatory() && !((Observed) property).checkConsistency) {
            emptyMandatory.set(TRUE);
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <O, T> T pre(O object, Getable<O, T> property) {
        observe(object, property, false);
        T result = super.pre(object, property);
        if (result == null && property instanceof Observed && ((Observed) property).mandatory()) {
            result = super.get(object, property);
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <O, T> T current(O object, Getable<O, T> property) {
        observe(object, property, false);
        T result = super.current(object, property);
        if (result == null && property instanceof Observed && ((Observed) property).mandatory() && !((Observed) property).checkConsistency) {
            emptyMandatory.set(TRUE);
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <O, T> T set(O object, Setable<O, T> property, T value) {
        observe(object, property, true);
        if (value == null && property instanceof Observed && ((Observed) property).mandatory() && ((Observed) property).checkConsistency) {
            throw new NullPointerException();
        }
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

    @Override
    public <T> T getNonObserving(Supplier<T> action) {
        if (getted.isInitialized()) {
            return OBSERVE.get(false, action);
        } else {
            return super.getNonObserving(action);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    protected <O, T> void changed(O object, Setable<O, T> setable, T preValue, T postValue) {
        if (object instanceof Mutable && setable instanceof Observed && getted.isInitialized() && OBSERVE.get()) {
            hasChanged.set(TRUE);
            if (setable.isDestructiveChange(preValue, postValue)) {
                isDestructive.set(TRUE);
            }
        }
        runNonObserving(() -> super.changed(object, setable, preValue, postValue));
    }

}
