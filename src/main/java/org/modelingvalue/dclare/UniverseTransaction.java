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

import static org.modelingvalue.dclare.State.ALL_SETTABLES;

import java.util.Iterator;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Concurrent;
import org.modelingvalue.collections.util.ContextThread.ContextPool;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.collections.util.StatusProvider;
import org.modelingvalue.collections.util.StatusProvider.AbstractStatus;
import org.modelingvalue.collections.util.StatusProvider.StatusIterator;
import org.modelingvalue.collections.util.TraceTimer;
import org.modelingvalue.dclare.NonCheckingObserver.NonCheckingTransaction;
import org.modelingvalue.dclare.ex.ConsistencyError;
import org.modelingvalue.dclare.ex.TooManyChangesException;

@SuppressWarnings("unused")
public class UniverseTransaction extends MutableTransaction {

    private static final Setable<Universe, Boolean>                                                    STOPPED                 = Setable.of("stopped", false);
    //
    private final DclareConfig                                                                         config;
    protected final Concurrent<ReusableTransaction<Action<?>, ActionTransaction>>                      actionTransactions      = Concurrent.of(() -> new ReusableTransaction<>(this));
    protected final Concurrent<ReusableTransaction<Observer<?>, ObserverTransaction>>                  observerTransactions    = Concurrent.of(() -> new ReusableTransaction<>(this));
    protected final Concurrent<ReusableTransaction<Mutable, MutableTransaction>>                       mutableTransactions     = Concurrent.of(() -> new ReusableTransaction<>(this));
    protected final Concurrent<ReusableTransaction<ReadOnly, ReadOnlyTransaction>>                     readOnlys               = Concurrent.of(() -> new ReusableTransaction<>(this));
    protected final Concurrent<ReusableTransaction<Derivation, DerivationTransaction>>                 derivations             = Concurrent.of(() -> new ReusableTransaction<>(this));
    protected final Concurrent<ReusableTransaction<IdentityDerivation, IdentityDerivationTransaction>> identityDerivations     = Concurrent.of(() -> new ReusableTransaction<>(this));
    protected final Concurrent<ReusableTransaction<NonCheckingObserver<?>, NonCheckingTransaction>>    nonCheckingTransactions = Concurrent.of(() -> new ReusableTransaction<>(this));
    //
    private final Action<Universe>                                                                     init                    = Action.of("$init", o -> universe().init());
    private final Action<Universe>                                                                     stop                    = Action.of("$stop", o -> {
                                                                                                                                   STOPPED.set(universe(), true);
                                                                                                                               });
    private final Action<Universe>                                                                     backward                = Action.of("$backward");
    private final Action<Universe>                                                                     forward                 = Action.of("$forward");
    private final Action<Universe>                                                                     commit                  = Action.of("$commit");
    private final Action<Universe>                                                                     clearOrphans            = Action.of("$clearOrphans", this::clearOrphans);
    private final Action<Universe>                                                                     checkConsistency        = Action.of("$checkConsistency", this::checkConsistency);
    //
    protected final BlockingQueue<Action<Universe>>                                                    inQueue;
    private final BlockingQueue<State>                                                                 resultQueue             = new LinkedBlockingQueue<>(1);                          //TODO wire onto MoodManager
    private final State                                                                                emptyState              = createState(State.EMPTY_OBJECTS_MAP);
    protected final ReadOnly                                                                           runOnState              = new ReadOnly(this, Priority.immediate);
    protected final Derivation                                                                         derivation              = new Derivation(this, Priority.immediate);
    protected final IdentityDerivation                                                                 identityDerivation      = new IdentityDerivation(this, Priority.immediate);
    private final UniverseStatistics                                                                   universeStatistics;
    protected final AtomicReference<Set<Throwable>>                                                    errors                  = new AtomicReference<>(Set.of());
    private final AtomicReference<Set<Throwable>>                                                      inconsistencies         = new AtomicReference<>(Set.of());
    private final AtomicReference<Boolean>                                                             orphansDetected         = new AtomicReference<>(null);
    private final ConstantState                                                                        constantState           = new ConstantState("CONST", this::handleException);
    private final StatusProvider<Status>                                                               statusProvider;
    private final Timer                                                                                timer                   = new Timer("UniverseTransactionTimer", true);
    private final MutableState                                                                         preInnerStartState      = createMutableState(emptyState);
    private final MutableState                                                                         innerStartState         = createMutableState(emptyState);
    private final MutableState                                                                         midStartState           = createMutableState(emptyState);
    private final MutableState                                                                         outerStartState         = createMutableState(emptyState);

    private List<Action<Universe>>                                                                     timeTravelingActions    = List.of(backward, forward);
    private List<Action<Universe>>                                                                     preActions              = List.of();
    private List<Action<Universe>>                                                                     postActions             = List.of();
    private List<ImperativeTransaction>                                                                imperativeTransactions  = List.of();
    private List<State>                                                                                history                 = List.of();
    private List<State>                                                                                future                  = List.of();
    private State                                                                                      preState;
    private State                                                                                      preOrphansState;
    private State                                                                                      preOuterStartState;
    private ConstantState                                                                              tmpConstants;
    private State                                                                                      state;
    private boolean                                                                                    initialized;
    private boolean                                                                                    killed;
    private boolean                                                                                    timeTraveling;
    private boolean                                                                                    handling;                                                                        //TODO wire onto MoodManager
    private boolean                                                                                    stopped;                                                                         //TODO wire onto MoodManager
    private long                                                                                       transactionNumber;

    public class Status extends AbstractStatus {

        public final Mood               mood;
        public final Action<Universe>   action;
        public final State              state;
        public final UniverseStatistics stats;
        public final Set<Object>        active;

        public Status(Mood mood, Action<Universe> action, State state, UniverseStatistics stats, Set<Object> active) {
            super();
            this.mood = mood;
            this.action = action;
            this.state = state;
            this.stats = stats;
            this.active = active;
        }

        @Override
        public boolean isStopped() {
            return mood == Mood.stopped;
        }

        public boolean isIdle() {
            return action != null && mood == Mood.idle && active.isEmpty();
        }

        public boolean isBusy() {
            return mood == Mood.busy || !active.isEmpty();
        }

        @Override
        protected void handleException(Exception e) {
            UniverseTransaction.this.handleException(e);
        }

        @Override
        public String toString() {
            return String.format("%-10s #act=%d %-70s %s", mood, active.size(), (stats != null ? stats.shortString() : ""), action);
        }
    }

    public static enum Mood {
        starting(),
        busy(),
        idle(),
        stopped();
    }

    public UniverseTransaction(Universe universe, ContextPool pool) {
        this(universe, pool, new DclareConfig());
    }

    public UniverseTransaction(Universe universe, ContextPool pool, DclareConfig config) {
        this(universe, pool, config, new Status[1]);
    }

    public UniverseTransaction(Universe universe, ContextPool pool, DclareConfig config, Status[] startStatus) {
        super(null);
        startStatus[0] = new Status(Mood.starting, null, emptyState, null, Set.of());
        this.statusProvider = new StatusProvider<>(this, startStatus[0]);
        this.config = Objects.requireNonNull(config);
        this.inQueue = new LinkedBlockingQueue<>(config.getMaxInInQueue());
        this.universeStatistics = new UniverseStatistics(this);
        start(universe, null);
        preState = emptyState;
        pool.execute(this::mainLoop);
        init();
    }

    @SuppressWarnings("rawtypes")
    protected State createState(DefaultMap<Object, DefaultMap<Setable, Object>> map) {
        return new State(this, map);
    }

    protected MutableState createMutableState(State state) {
        return new MutableState(state);
    }

    protected void mainLoop() {
        state = emptyState;
        state = state.get(() -> incrementChangeId(state));
        if (config.isTraceUniverse()) {
            System.err.println(DclareTrace.getLineStart("DCLARE", this) + "START UNIVERSE " + this);
        }
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                UniverseTransaction.this.timerTask();
            }
        }, 0, 300);
        while (!killed) {
            try {
                handling = false; //TODO wire onto MoodManager
                setIdleMood(state);
                //==========================================================================
                Action<Universe> action = take();
                //==========================================================================
                setBusyMood(action);
                preState = state;
                universeStatistics.setDebugging(false);
                handling = true; //TODO wire onto MoodManager
                if (config.isTraceUniverse()) {
                    System.err.println(DclareTrace.getLineStart("DCLARE", this) + "BEGIN TRANSACTION " + this);
                }
                TraceTimer.traceBegin("root");
                try {
                    timeTraveling = timeTravelingActions.contains(action);
                    start(action);
                    if (action == backward) {
                        if (history.size() > 3) {
                            future = future.prepend(state);
                            state = history.last();
                            history = history.removeLast();
                        }
                    } else if (action == forward) {
                        if (!future.isEmpty()) {
                            history = history.append(state);
                            state = future.first();
                            future = future.removeFirst();
                        }
                    } else if (action != commit) {
                        history = history.append(state);
                        future = List.of();
                        if (history.size() > universeStatistics.maxNrOfHistory()) {
                            history = history.removeFirst();
                        }
                        runActions(preActions);
                        runAction(action);
                        if (initialized) {
                            runAction(checkConsistency);
                        }
                        handleTooManyChanges(state);
                        runActions(postActions);
                    }
                    commit(state, timeTraveling, imperativeTransactions.iterator());
                    if (!killed && inQueue.isEmpty() && isStopped(state)) {
                        break;
                    }
                } catch (Throwable t) {
                    handleException(t);
                } finally {
                    if (config.isTraceUniverse()) {
                        System.err.println(DclareTrace.getLineStart("DCLARE", this) + "END TRANSACTION " + this);
                    }
                    end(action);
                    universeStatistics.completeRun();
                    TraceTimer.traceEnd("root");
                }
            } catch (Throwable t) {
                handleException(t);
            }
        }
        if (config.isTraceUniverse()) {
            System.err.println(DclareTrace.getLineStart("DCLARE", this) + "STOP UNIVERSE " + this);
        }
        timer.cancel();
        state.run(() -> UniverseTransaction.this.universe().exit());
        stop();
        history = history.append(state);
        constantState.stop();
        end(state); //TODO wire onto MoodManager
        stopped = true; //TODO wire onto MoodManager
        setStoppedMood(state);
    }

    private void runAction(Action<Universe> action) {
        if (!killed) {
            state = state.get(() -> run(triggerAction(state, action)));
        }
    }

    private void runActions(List<Action<Universe>> actions) {
        if (!killed && !actions.isEmpty()) {
            state = state.get(() -> run(triggerActions(state, actions)));
        }
    }

    private <O extends Mutable> State triggerActions(State state, List<Action<Universe>> actions) {
        for (Action<Universe> action : actions) {
            state = triggerAction(state, action);
        }
        return state;
    }

    public void addActive(Object activity) {
        statusProvider.setNext(p -> {
            Set<Object> newSet = p.active.add(activity);
            assert p.active != newSet;
            return new Status(p.mood, p.action, p.state, p.stats, newSet);
        });
    }

    public void removeActive(Object activity) {
        statusProvider.setNext(p -> {
            Set<Object> newSet = p.active.remove(activity);
            assert p.active != newSet;
            return new Status(p.mood, p.action, p.state, p.stats, newSet);
        });
    }

    private void setBusyMood(Action<Universe> action) {
        statusProvider.setNext(p -> new Status(Mood.busy, action, p.state, stats().clone(), p.active));
    }

    private void setIdleMood(State state) {
        statusProvider.setNext(p -> new Status(Mood.idle, p.action, state, stats().clone(), p.active));
    }

    private void setStoppedMood(State state) {
        statusProvider.setNext(p -> new Status(Mood.stopped, p.action, state, stats().clone(), p.active));
    }

    public Action<Universe> waitForBusy() {
        return waitForStatus(Status::isBusy).action;
    }

    public State waitForIdle() {
        return waitForStatus(Status::isIdle).state;
    }

    public State waitForStopped() {
        return waitForStatus(Status::isStopped).state;
    }

    public Status waitForStatus(Predicate<Status> pred) {
        return getStatusIterator().waitForStoppedOr(pred);
    }

    public State putAndWaitForIdle(Object id, Runnable action) {
        return putAndWaitForIdle(Action.of(id, o -> action.run()));
    }

    public State putAndWaitForIdle(Action<Universe> action) {
        StatusIterator<Status> iterator = getStatusIterator();
        put(action);
        return iterator.waitForStoppedOr(s -> s.isIdle() && s.action == action).state;
    }

    public Mood getMood() {
        return getStatus().mood;
    }

    public StatusIterator<Status> getStatusIterator() {
        return statusProvider.iterator();
    }

    public Status getStatus() {
        return statusProvider.getStatus();
    }

    public DclareConfig getConfig() {
        return config;
    }

    protected void timerTask() {
        statusProvider.setNext(p -> {
            if (p.mood == Mood.busy) {
                UniverseStatistics stats = stats().clone();
                if (!Objects.equals(p.stats, stats)) {
                    return new Status(p.mood, p.action, p.state, stats, p.active);
                }
            }
            return p;
        });
    }

    @Override
    protected State run(State state) {
        boolean again;
        tmpConstants = new ConstantState("TEMP", this::handleException);
        try {
            do {
                preOrphansState = state;
                orphansDetected.set(null);
                preOuterStartState = state;
                state = incrementChangeId(state);
                preInnerStartState.setState(state);
                outerStartState.setState(state);
                do {
                    midStartState.setState(state);
                    do {
                        innerStartState.setState(state);
                        state = incrementChangeId(state);
                        state = super.run(state);
                        preInnerStartState.setState(innerStartState.state());
                        again = false;
                        if (!killed) {
                            if (orphansDetected.get() == Boolean.TRUE) {
                                preOrphansState = innerStartState.preState();
                                state = trigger(state, universe(), clearOrphans, Priority.inner);
                                again = true;
                            } else if (hasInnerQueued(state)) {
                                again = true;
                                if (orphansDetected.get() == Boolean.FALSE) {
                                    preOrphansState = state;
                                    orphansDetected.set(null);
                                }
                            }
                        }
                    } while (again);
                    if (!killed) {
                        if (hasMidQueued(state)) {
                            again = true;
                            if (orphansDetected.get() == Boolean.FALSE) {
                                preOrphansState = state;
                                orphansDetected.set(null);
                            }
                        } else if (orphansDetected.get() == null) {
                            state = trigger(state, universe(), clearOrphans, Priority.mid);
                            again = true;
                        }
                    }
                } while (again);
                universeStatistics.completeForward();
            } while (!killed && hasOuterQueued(state));
            return state;
        } finally {
            preInnerStartState.setState(emptyState);
            innerStartState.setState(emptyState);
            midStartState.setState(emptyState);
            outerStartState.setState(emptyState);
            preOuterStartState = null;
            preOrphansState = null;
            tmpConstants.stop();
        }
    }

    protected final State incrementChangeId(State state) {
        return state.set(universe(), Mutable.D_CHANGE_ID, TransactionId.of(transactionNumber++));
    }

    private boolean hasInnerQueued(State state) {
        boolean result = hasQueued(state, universe(), Priority.inner);
        if (config.isTraceUniverse() && result) {
            System.err.println(DclareTrace.getLineStart("DCLARE", this) + "INNER UNIVERSE " + this);
        }
        return result;
    }

    private boolean hasMidQueued(State state) {
        boolean result = hasQueued(state, universe(), Priority.mid);
        if (config.isTraceUniverse() && result) {
            System.err.println(DclareTrace.getLineStart("DCLARE", this) + "MID UNIVERSE " + this);
        }
        return result;
    }

    private boolean hasOuterQueued(State state) {
        boolean result = hasQueued(state, universe(), Priority.outer);
        if (config.isTraceUniverse() && result) {
            System.err.println(DclareTrace.getLineStart("DCLARE", this) + "OUTER UNIVERSE " + this);
        }
        return result;
    }

    public int numInQueue() {
        return inQueue.size();
    }

    public boolean isHandling() { //TODO wire onto MoodManager
        return handling;
    }

    public boolean isStopped() { //TODO wire onto MoodManager
        return stopped;
    }

    public boolean isInitialized() {
        return initialized;
    }

    protected void setInitialized() {
        initialized = true;
    }

    @Override
    public State lastState() {
        State result = super.lastState();
        if (result == null) {
            result = state;
        }
        return result;
    }

    protected void handleInconsistencies(Set<Throwable> inconsistencies) {
        errors.updateAndGet(inconsistencies::addAll);
        handleExceptions();
    }

    protected void handleExceptions() {
        if (config.isTraceUniverse()) {
            List<Throwable> list = errors.get().sorted(this::compareThrowable).toList();
            System.err.println(DclareTrace.getLineStart("DCLARE", this) + list.size() + " EXCEPTION(S) " + this);
            list.first().printStackTrace();
        }
        kill();
    }

    public final void handleException(Throwable t) {
        errors.updateAndGet(Set.of(t)::addAll);
        handleExceptions();
    }

    public void throwIfError() {
        Set<Throwable> es = errors.get();
        if (!es.isEmpty()) {
            List<Throwable> list = es.sorted(this::compareThrowable).toList();
            throw new Error("Error in engine " + state.get(() -> list.first().getMessage()), list.first());
        }
    }

    public int compareThrowable(Throwable a, Throwable b) {
        if (a instanceof ConsistencyError) {
            if (b instanceof ConsistencyError) {
                return ((ConsistencyError) a).compareTo((ConsistencyError) b);
            } else {
                return 1;
            }
        } else if (b instanceof ConsistencyError) {
            return -1;
        } else {
            return Integer.compare(a.hashCode(), b.hashCode());
        }
    }

    protected void init() {
        put(init);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void checkConsistency(Universe universe) {
        LeafTransaction lt = LeafTransaction.getCurrent();
        State post = lt.state();
        preState.diff(post, o -> o instanceof Mutable && ((Mutable) o).dCheckConsistency()).forEach(e0 -> {
            Mutable mutable = (Mutable) e0.getKey();
            DefaultMap<Setable, Object> values = e0.getValue().b();
            if (mutable.equals(universe()) || mutable.dHasAncestor(universe())) {
                MutableClass dClass = mutable.dClass();
                Collection.concat(values.map(Entry::getKey), dClass.dSetables(), dClass.dObservers().map(Observer::exception)).distinct().filter(Setable::checkConsistency).forEach(s -> {
                    if (!(s instanceof Constant) || constantState.isSet(lt, mutable, (Constant) s)) {
                        Set<Throwable> es = s.checkConsistency(post, mutable, s instanceof Constant ? constantState.get(lt, mutable, (Constant) s) : values.get(s));
                        inconsistencies.updateAndGet(es::addAll);
                    }
                });
            } else {
                checkOrphanState(mutable, values);
            }
        });
        Set<Throwable> result = inconsistencies.getAndSet(Set.of());
        if (!result.isEmpty()) {
            handleInconsistencies(result);
        }
    }

    @SuppressWarnings("rawtypes")
    protected void checkOrphanState(Mutable mutable, DefaultMap<Setable, Object> values) {
        if (config.isCheckOrphanState() && !values.isEmpty()) {
            throw new Error("Orphan '" + mutable + "' has state '" + values + "'");
        }
    }

    public Universe universe() {
        return (Universe) mutable();
    }

    private State triggerAction(State state, Action<Universe> action) {
        return trigger(state, universe(), action, Priority.scheduled);
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantSuppression"})
    private void handleTooManyChanges(State state) {
        if (!killed && stats().debugging()) {
            ObserverTrace trace = state//
                    .filter(o -> o instanceof Mutable, s -> s instanceof Observer.Traces) //
                    .flatMap(e1 -> e1.getValue().map(e2 -> ((Set<ObserverTrace>) e2.getValue()).sorted().findFirst().orElseThrow())) //
                    .min((a, b) -> Integer.compare(b.done().size(), a.done().size())) //
                    .orElseThrow();
            throw new TooManyChangesException(state, trace, trace.done().size());
        }
    }

    @Override
    public UniverseTransaction universeTransaction() {
        return this;
    }

    public State emptyState() {
        return emptyState;
    }

    public Action<Universe> initAction() {
        return init;
    }

    @SuppressWarnings("rawtypes")
    protected void clearOrphans(Universe universe) {
        LeafTransaction tx = LeafTransaction.getCurrent();
        State postState = tx.state();
        Map<Object, Map<Setable, Pair<Object, Object>>> orphans = preOrphansState//
                .diff(postState, o -> {
                    if (o instanceof Mutable && ((Mutable) o).dIsOrphan(postState)) {
                        return !tx.toBeCleared((Mutable) o).isEmpty();
                    } else {
                        return false;
                    }
                }, ALL_SETTABLES)//
                .toMap(Function.identity());
        orphansDetected.set(!orphans.isEmpty());
        orphans.forEachOrdered(e0 -> clear(tx, (Mutable) e0.getKey()));
    }

    private void clear(LeafTransaction tx, Mutable orphan) {
        orphan.dDeactivate();
        tx.clear(orphan);
        for (Mutable child : orphan.dChildren()) {
            clear(tx, child);
        }
    }

    public boolean isStopped(State state) {
        return state.get(universe(), STOPPED);
    }

    public void put(Object id, Runnable action) {
        put(Action.of(id, o -> action.run()));
    }

    public void put(Action<Universe> action) {
        if (!killed) {
            try {
                inQueue.put(action);
            } catch (InterruptedException e) {
                throw new Error(e);
            }
        }
    }

    private Action<Universe> take() {
        try {
            return inQueue.take();
        } catch (InterruptedException e) {
            throw new Error(e);
        }
    }

    protected void end(State state) { //TODO wire onto MoodManager
        try {
            resultQueue.put(state);
        } catch (InterruptedException e) {
            throw new Error(e);
        }
    }

    public State waitForEnd() { //TODO wire onto MoodManager
        try {
            State state = resultQueue.take();
            resultQueue.put(state);
            throwIfError();
            return state;
        } catch (InterruptedException e) {
            throw new Error(e);
        }
    }

    public void addTimeTravelingAction(Action<Universe> action) {
        synchronized (this) {
            timeTravelingActions = timeTravelingActions.add(action);
        }
    }

    public void addPreAction(Action<Universe> action) {
        synchronized (this) {
            preActions = preActions.add(action);
        }
    }

    public Action<Universe> addDiffHandler(String id, StateDeltaHandler diffHandler) {
        Action<Universe> action = Action.of(id, o -> {
            LeafTransaction tx = ActionTransaction.getCurrent();
            diffHandler.handleDelta(tx.universeTransaction().preState(), tx.state(), true, ImperativeTransaction.SETTED_MAP);
        });
        addPostAction(action);
        return action;
    }

    public void addPostAction(Action<Universe> action) {
        synchronized (this) {
            postActions = postActions.add(action);
        }
    }

    @SuppressWarnings("rawtypes")
    public ImperativeTransaction addImperative(String id, StateDeltaHandler diffHandler, Consumer<Runnable> scheduler, boolean keepTransaction) {
        ImperativeTransaction n = ImperativeTransaction.of(Imperative.of(id), preState, this, scheduler, diffHandler, keepTransaction);
        synchronized (this) {
            imperativeTransactions = imperativeTransactions.add(n);
        }
        return n;
    }

    private void commit(State state, boolean timeTraveling, Iterator<ImperativeTransaction> it) {
        if (!killed && it.hasNext()) {
            ImperativeTransaction itx = it.next();
            itx.schedule(() -> {
                if (itx.commit(state, timeTraveling)) {
                    commit(itx.state(), timeTraveling, it);
                }
            });
        }
    }

    public void commit() {
        put(commit);
    }

    public void backward() {
        put(backward);
    }

    @Override
    public void stop() {
        put(stop);
    }

    public void forward() {
        put(forward);
    }

    public State preState() {
        return preState;
    }

    public State currentState() {
        return state;
    }

    public IState preInnerStartState() {
        return preInnerStartState;
    }

    public IState innerStartState() {
        return innerStartState;
    }

    public IState midStartState() {
        return midStartState;
    }

    public IState outerStartState() {
        return outerStartState;
    }

    public State preOuterStartState() {
        return preOuterStartState;
    }

    public List<State> history() {
        return history;
    }

    public Collection<IState> detailedHistory() {
        return Collection.concat(history, Collection.of(preOuterStartState(), outerStartState(), midStartState(), preInnerStartState(), innerStartState()));
    }

    public ConstantState tmpConstants() {
        return tmpConstants;
    }

    protected boolean isTimeTraveling() {
        return timeTraveling;
    }

    public boolean isKilled() {
        return killed;
    }

    public void kill() {
        killed = true;
        try {
            inQueue.put(stop);
        } catch (InterruptedException e) {
            throw new Error(e);
        }
    }

    public UniverseStatistics stats() {
        return universeStatistics;
    }

    public void start(Action<Universe> action) {
    }

    public void end(Action<Universe> action) {
    }

    public ConstantState constantState() {
        return constantState;
    }

    public <T, O> TransactionId setPreserved(O object, Setable<O, T> property, T post, Action<?> action) {
        TransactionId txid = outerStartState.transactionId();
        preInnerStartState.set(object, property, post, txid);
        innerStartState.set(object, property, post, txid);
        midStartState.set(object, property, post, txid);
        outerStartState.set(object, property, post, txid);
        return txid;
    }

}
