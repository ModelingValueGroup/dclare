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

import static org.modelingvalue.dclare.State.ALL_SETTABLES;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

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
import org.modelingvalue.collections.util.TriConsumer;
import org.modelingvalue.dclare.NonCheckingObserver.NonCheckingTransaction;
import org.modelingvalue.dclare.ex.ConsistencyError;
import org.modelingvalue.dclare.ex.TooManyChangesException;

@SuppressWarnings("unused")
public class UniverseTransaction extends MutableTransaction {
    private static final UnaryOperator<Byte>                                                        INCREMENT               = c -> ++c;
    private static final Setable<Universe, Boolean>                                                 STOPPED                 = Setable.of("stopped", false);
    private static final boolean                                                                    TRACE_MOOD              = Boolean.getBoolean("TRACE_MOOD");
    //
    private final DclareConfig                                                                      config;
    protected final Concurrent<ReusableTransaction<Action<?>, ActionTransaction>>                   actionTransactions      = Concurrent.of(() -> new ReusableTransaction<>(this));
    protected final Concurrent<ReusableTransaction<Observer<?>, ObserverTransaction>>               observerTransactions    = Concurrent.of(() -> new ReusableTransaction<>(this));
    protected final Concurrent<ReusableTransaction<Mutable, MutableTransaction>>                    mutableTransactions     = Concurrent.of(() -> new ReusableTransaction<>(this));
    protected final Concurrent<ReusableTransaction<ReadOnly, ReadOnlyTransaction>>                  readOnlys               = Concurrent.of(() -> new ReusableTransaction<>(this));
    protected final Concurrent<ReusableTransaction<Derivation, DerivationTransaction>>              derivations             = Concurrent.of(() -> new ReusableTransaction<>(this));
    protected final Concurrent<ReusableTransaction<NonCheckingObserver<?>, NonCheckingTransaction>> nonCheckingTransactions = Concurrent.of(() -> new ReusableTransaction<>(this));
    //
    private final Action<Universe>                                                                  init                    = Action.of("$init", o -> universe().init());
    private final Action<Universe>                                                                  dummy                   = Action.of("$dummy");
    private final Action<Universe>                                                                  stop                    = Action.of("$stop", o -> STOPPED.set(universe(), true));
    private final Action<Universe>                                                                  backward                = Action.of("$backward");
    private final Action<Universe>                                                                  forward                 = Action.of("$forward");
    private final Action<Universe>                                                                  clearOrphans            = Action.of("$clearOrphans", this::clearOrphans);
    private final Action<Universe>                                                                  checkConsistency        = Action.of("$checkConsistency", this::checkConsistency);
    //
    protected final BlockingQueue<Action<Universe>>                                                 inQueue;
    private final BlockingQueue<State>                                                              resultQueue             = new LinkedBlockingQueue<>(1);                                           //TODO wire onto MoodManager
    private final State                                                                             emptyState              = new State(this, State.EMPTY_OBJECTS_MAP);
    protected final ReadOnly                                                                        runOnState              = new ReadOnly(this, Priority.forward);
    protected final Derivation                                                                      derivation              = new Derivation(this, Priority.forward);
    private final UniverseStatistics                                                                universeStatistics;
    private final AtomicReference<Set<Throwable>>                                                   errors                  = new AtomicReference<>(Set.of());
    private final ConstantState                                                                     constantState           = new ConstantState(this::handleException);
    private final StatusProvider<Status>                                                            statusProvider          = new StatusProvider<>(new Status(Mood.starting, null, emptyState, null));
    private final Timer                                                                             timer                   = new Timer("UniverseTransactionTimer");
    private final TimerTask                                                                         timerTask               = new TimerTask() {
                                                                                                                                @Override
                                                                                                                                public void run() {
                                                                                                                                    UniverseTransaction.this.timerTask();
                                                                                                                                }
                                                                                                                            };

    private List<Action<Universe>>                                                                  timeTravelingActions    = List.of(backward, forward);
    private List<Action<Universe>>                                                                  preActions              = List.of();
    private List<Action<Universe>>                                                                  postActions             = List.of();
    private List<State>                                                                             history                 = List.of();
    private List<State>                                                                             future                  = List.of();
    private State                                                                                   preState;
    private State                                                                                   startState;
    private State                                                                                   state;
    private boolean                                                                                 initialized;
    private boolean                                                                                 killed;
    private boolean                                                                                 timeTraveling;
    private boolean                                                                                 handling;                                                                                         //TODO wire onto MoodManager
    private boolean                                                                                 stopped;                                                                                          //TODO wire onto MoodManager
    private boolean                                                                                 orphansDetected;

    public class Status extends AbstractStatus {

        public final Mood               mood;
        public final Action<Universe>   action;
        public final State              state;
        public final UniverseStatistics statistics;

        public Status(Mood mood, Action<Universe> action, State state, UniverseStatistics statistics) {
            super();
            this.mood = mood;
            this.action = action;
            this.state = state;
            this.statistics = statistics;
        }

        @Override
        public boolean isStopped() {
            return mood == Mood.stopped;
        }

        @Override
        protected void handleException(Exception e) {
            UniverseTransaction.this.handleException(e);
        }

        @Override
        public String toString() {
            return "Status:" + mood + "(" + action + ")";
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
        super(null);
        this.config = Objects.requireNonNull(config);
        this.inQueue = new LinkedBlockingQueue<>(config.getMaxInInQueue());
        this.universeStatistics = new UniverseStatistics(this);
        start(universe, null);
        preState = emptyState;
        pool.execute(() -> mainLoop(config.getStart()));
        init();
    }

    protected void mainLoop(State start) {
        state = start != null ? start.clone(this) : emptyState;
        if (config.isTraceUniverse()) {
            System.err.println("DCLARE: START UNIVERSE " + this);
        }
        timer.schedule(timerTask, 300, 300);
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
                    System.err.println("DCLARE: BEGIN TRANSACTION " + this);
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
                    } else if (action != dummy) {
                        history = history.append(state);
                        future = List.of();
                        if (history.size() > universeStatistics.maxNrOfHistory()) {
                            history = history.removeFirst();
                        }
                        if (!preActions.isEmpty()) {
                            state = state.get(() -> run(triggerActions(state, preActions)));
                        }
                        if (!killed) {
                            state = state.get(() -> run(triggerAction(state, action)));
                        }
                        if (!killed && initialized) {
                            state = state.get(() -> run(triggerAction(state, checkConsistency)));
                        }
                        if (!killed && stats().debugging()) {
                            handleTooManyChanges(state);
                        }
                    }
                    if (!killed && !postActions.isEmpty()) {
                        state = state.get(() -> run(triggerActions(state, postActions)));
                    }
                    if (!killed && inQueue.isEmpty() && isStopped(state)) {
                        break;
                    }
                } catch (Throwable t) {
                    handleException(t);
                } finally {
                    if (config.isTraceUniverse()) {
                        System.err.println("DCLARE: END TRANSACTION " + this);
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
            System.err.println("DCLARE: STOP UNIVERSE " + this);
        }
        stop();
        history = history.append(state);
        constantState.stop();
        end(state); //TODO wire onto MoodManager
        stopped = true; //TODO wire onto MoodManager
        setStoppedMood(state);
    }

    private void setBusyMood(Action<Universe> action) {
        statusProvider.setNext(p -> new Status(Mood.busy, action, p.state, p.statistics));
    }

    private void setIdleMood(State state) {
        statusProvider.setNext(p -> new Status(Mood.idle, p.action, state, p.statistics));
    }

    private void setStoppedMood(State state) {
        statusProvider.setNext(p -> new Status(Mood.stopped, p.action, state, p.statistics));
    }

    public Action<Universe> waitForBusy() {
        return getStatusIterator().getFirst(s -> s.mood == Mood.busy).action;
    }

    public State waitForIdle() {
        return getStatusIterator().getFirst(s -> s.mood == Mood.idle).state;
    }

    public State waitForStopped() {
        return getStatusIterator().getFirst(s -> s.mood == Mood.stopped).state;
    }

    public State putAndWaitForIdle(Object id, Runnable action) {
        return putAndWaitForIdle(Action.of(id, o -> action.run()));
    }

    public State putAndWaitForIdle(Action<Universe> action) {
        StatusIterator<Status> iterator = getStatusIterator();
        put(action);
        return iterator.getFirst(s -> s.mood == Mood.idle && s.action == action).state;
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
            if (!Objects.equals(p.statistics, stats())) {
                return new Status(p.mood, p.action, p.state, new UniverseStatistics(stats()));
            } else {
                return p;
            }
        });
    }

    @Override
    protected State run(State state) {
        do {
            startState = state;
            state = state.set(universe(), Mutable.D_CHANGE_NR, INCREMENT);
            state = super.run(state);
            universeStatistics.completeForward();
            state = clearOrphans(state);
        } while (!killed && hasBackwardActionsQueued(state));
        return state;
    }

    private boolean hasBackwardActionsQueued(State state) {
        boolean result = hasQueued(state, universe(), Priority.backward);
        if (config.isTraceUniverse() && result) {
            System.err.println("DCLARE: BACKWARD UNIVERSE " + this);
        }
        return result;
    }

    private State clearOrphans(State state) {
        do {
            state = super.run(triggerAction(state, clearOrphans));
        } while (!killed && orphansDetected);
        return state;
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

    protected void handleExceptions(Set<Throwable> errors) {
        if (config.isTraceUniverse()) {
            List<Throwable> list = errors.sorted(this::compareThrowable).toList();
            System.err.println("DCLARE: EXCEPTION " + this);
            list.first().printStackTrace();
        }
        kill();
    }

    public final void handleException(Throwable t) {
        handleExceptions(errors.updateAndGet(e -> e.add(t)));
    }

    public Set<Throwable> errors() {
        return errors.get();
    }

    protected void clearErrors() {
        errors.set(Set.of());
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
                        errors.updateAndGet(es::addAll);
                    }
                });
            } else {
                checkOrphanState(mutable, values);
            }
        });
        Set<Throwable> es = errors.get();
        if (!es.isEmpty()) {
            handleExceptions(es);
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

    private <O extends Mutable> State triggerActions(State state, List<Action<Universe>> actions) {
        for (Action<Universe> action : actions) {
            state = triggerAction(state, action);
        }
        return state;
    }

    private State triggerAction(State state, Action<Universe> action) {
        return trigger(state, universe(), action, Priority.scheduled);
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantSuppression"})
    protected void handleTooManyChanges(State state) {
        ObserverTrace trace = state//
                .filter(o -> o instanceof Mutable, s -> s instanceof Observer.Traces) //
                .flatMap(e1 -> e1.getValue().map(e2 -> ((Set<ObserverTrace>) e2.getValue()).sorted().findFirst().orElseThrow())) //
                .min((a, b) -> Integer.compare(b.done().size(), a.done().size())) //
                .orElseThrow();
        throw new TooManyChangesException(state, trace, trace.done().size());
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
        Map<Object, Map<Setable, Pair<Object, Object>>> orphans = preState()//
                .diff(postState, o -> {
                    if (o instanceof Mutable && ((Mutable) o).dIsOrphan(postState)) {
                        return !tx.toBeCleared((Mutable) o).isEmpty();
                    } else {
                        return false;
                    }
                }, ALL_SETTABLES)//
                .toMap(Function.identity());
        orphansDetected = !orphans.isEmpty();
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

    public void addDiffHandler(String id, TriConsumer<State, State, Boolean> diffHandler) {
        addPostAction(Action.of(id, o -> {
            LeafTransaction tx = ActionTransaction.getCurrent();
            diffHandler.accept(tx.universeTransaction().preState(), tx.state(), true);
        }));
    }

    public ImperativeTransaction addImperative(String id, Consumer<State> firstHandler, TriConsumer<State, State, Boolean> diffHandler, Consumer<Runnable> scheduler, boolean keepTransaction) {
        ImperativeTransaction n = ImperativeTransaction.of(Imperative.of(id), preState, this, scheduler, firstHandler, diffHandler, keepTransaction);
        addPostAction(Action.of(id, o -> {
            LeafTransaction tx = ActionTransaction.getCurrent();
            State pre = tx.state();
            boolean timeTraveling = tx.universeTransaction().isTimeTraveling();
            n.schedule(() -> n.commit(pre, timeTraveling));
        }));
        return n;
    }

    public void addPostAction(Action<Universe> action) {
        synchronized (this) {
            postActions = postActions.add(action);
        }
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

    public void dummy() {
        put(dummy);
    }

    public State preState() {
        return preState;
    }

    public State currentState() {
        return state;
    }

    public State startState() {
        return startState;
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
            inQueue.put(dummy);
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

}
