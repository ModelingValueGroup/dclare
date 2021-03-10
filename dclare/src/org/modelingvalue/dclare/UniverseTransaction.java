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
import org.modelingvalue.collections.util.TraceTimer;
import org.modelingvalue.collections.util.TriConsumer;
import org.modelingvalue.dclare.NonCheckingObserver.NonCheckingTransaction;
import org.modelingvalue.dclare.ex.ConsistencyError;
import org.modelingvalue.dclare.ex.TooManyChangesException;

@SuppressWarnings("unused")
public class UniverseTransaction extends MutableTransaction {

    private static final boolean             CHECK_ORPHAN_STATE      = Boolean.getBoolean("CHECK_ORPHAN_STATE");

    private static final boolean             TRACE_UNIVERSE          = Boolean.getBoolean("TRACE_UNIVERSE");

    public static final int                  MAX_IN_IN_QUEUE         = Integer.getInteger("MAX_IN_IN_QUEUE", 100);
    public static final int                  MAX_TOTAL_NR_OF_CHANGES = Integer.getInteger("MAX_TOTAL_NR_OF_CHANGES", 10000);
    public static final int                  MAX_NR_OF_CHANGES       = Integer.getInteger("MAX_NR_OF_CHANGES", 200);
    public static final int                  MAX_NR_OF_OBSERVED      = Integer.getInteger("MAX_NR_OF_OBSERVED", 1000);
    public static final int                  MAX_NR_OF_OBSERVERS     = Integer.getInteger("MAX_NR_OF_OBSERVERS", 1000);
    public static final int                  MAX_NR_OF_HISTORY       = Integer.getInteger("MAX_NR_OF_HISTORY", 64) + 3;

    private static final UnaryOperator<Byte> INCREMENT               = c -> ++c;

    public static UniverseTransaction of(Universe id, ContextPool pool, int maxInInQueue, int maxTotalNrOfChanges, int maxNrOfChanges, int maxNrOfObserved, int maxNrOfObservers, int maxNrOfHistory) {
        return new UniverseTransaction(id, pool, null, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory, null);
    }

    public static UniverseTransaction of(Universe id, ContextPool pool, int maxInInQueue, int maxTotalNrOfChanges, int maxNrOfChanges, int maxNrOfObserved, int maxNrOfObservers, int maxNrOfHistory, Consumer<UniverseTransaction> cycle) {
        return new UniverseTransaction(id, pool, null, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory, cycle);
    }

    public static UniverseTransaction of(Universe id, ContextPool pool) {
        return new UniverseTransaction(id, pool, null, MAX_IN_IN_QUEUE, MAX_TOTAL_NR_OF_CHANGES, MAX_NR_OF_CHANGES, MAX_NR_OF_OBSERVED, MAX_NR_OF_OBSERVERS, MAX_NR_OF_HISTORY, null);
    }

    public static UniverseTransaction of(Universe id, ContextPool pool, int maxInInQueue, Consumer<UniverseTransaction> cycle) {
        return new UniverseTransaction(id, pool, null, maxInInQueue, MAX_TOTAL_NR_OF_CHANGES, MAX_NR_OF_CHANGES, MAX_NR_OF_OBSERVED, MAX_NR_OF_OBSERVERS, MAX_NR_OF_HISTORY, cycle);
    }

    public static UniverseTransaction of(Universe id, ContextPool pool, int maxInInQueue) {
        return new UniverseTransaction(id, pool, null, maxInInQueue, MAX_TOTAL_NR_OF_CHANGES, MAX_NR_OF_CHANGES, MAX_NR_OF_OBSERVED, MAX_NR_OF_OBSERVERS, MAX_NR_OF_HISTORY, null);
    }

    public static UniverseTransaction of(Universe id, ContextPool pool, State start, int maxInInQueue, int maxTotalNrOfChanges, int maxNrOfChanges, int maxNrOfObserved, int maxNrOfObservers, int maxNrOfHistory) {
        return new UniverseTransaction(id, pool, start, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory, null);
    }

    public static UniverseTransaction of(Universe id, ContextPool pool, State start, int maxInInQueue, int maxTotalNrOfChanges, int maxNrOfChanges, int maxNrOfObserved, int maxNrOfObservers, int maxNrOfHistory, Consumer<UniverseTransaction> cycle) {
        return new UniverseTransaction(id, pool, start, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory, cycle);
    }

    public static UniverseTransaction of(Universe id, ContextPool pool, State start) {
        return new UniverseTransaction(id, pool, start, MAX_IN_IN_QUEUE, MAX_TOTAL_NR_OF_CHANGES, MAX_NR_OF_CHANGES, MAX_NR_OF_OBSERVED, MAX_NR_OF_OBSERVERS, MAX_NR_OF_HISTORY, null);
    }

    public static UniverseTransaction of(Universe id, ContextPool pool, State start, int maxInInQueue, Consumer<UniverseTransaction> cycle) {
        return new UniverseTransaction(id, pool, start, maxInInQueue, MAX_TOTAL_NR_OF_CHANGES, MAX_NR_OF_CHANGES, MAX_NR_OF_OBSERVED, MAX_NR_OF_OBSERVERS, MAX_NR_OF_HISTORY, cycle);
    }

    public static UniverseTransaction of(Universe id, ContextPool pool, State start, int maxInInQueue) {
        return new UniverseTransaction(id, pool, start, maxInInQueue, MAX_TOTAL_NR_OF_CHANGES, MAX_NR_OF_CHANGES, MAX_NR_OF_OBSERVED, MAX_NR_OF_OBSERVERS, MAX_NR_OF_HISTORY, null);
    }

    private static final Setable<Universe, Boolean>                                                 STOPPED                 = Setable.of("stopped", false);
    //
    protected final Concurrent<ReusableTransaction<Action<?>, ActionTransaction>>                   actionTransactions      = Concurrent.of(() -> new ReusableTransaction<>(this));
    protected final Concurrent<ReusableTransaction<Observer<?>, ObserverTransaction>>               observerTransactions    = Concurrent.of(() -> new ReusableTransaction<>(this));
    protected final Concurrent<ReusableTransaction<Mutable, MutableTransaction>>                    mutableTransactions     = Concurrent.of(() -> new ReusableTransaction<>(this));
    protected final Concurrent<ReusableTransaction<ReadOnly, ReadOnlyTransaction>>                  readOnlys               = Concurrent.of(() -> new ReusableTransaction<>(this));
    protected final Concurrent<ReusableTransaction<NonCheckingObserver<?>, NonCheckingTransaction>> nonCheckingTransactions = Concurrent.of(() -> new ReusableTransaction<>(this));
    //
    private final Action<Universe>                                                                  cycle;
    private final Action<Universe>                                                                  dummy                   = Action.of("$dummy");
    private final Action<Universe>                                                                  stop                    = Action.of("$stop", o -> STOPPED.set(universe(), true));
    private final Action<Universe>                                                                  backward                = Action.of("$backward");
    private final Action<Universe>                                                                  forward                 = Action.of("$forward");
    private final Action<Universe>                                                                  clearOrphans            = Action.of("$clearOrphans", this::clearOrphans);
    private final Action<Universe>                                                                  checkConsistency        = Action.of("$checkConsistency", this::checkConsistency);
    protected final BlockingQueue<Action<Universe>>                                                 inQueue;
    private final BlockingQueue<State>                                                              resultQueue             = new LinkedBlockingQueue<>(1);
    private final State                                                                             emptyState              = new State(this, State.EMPTY_OBJECTS_MAP);
    protected final ReadOnly                                                                        runOnState              = new ReadOnly(this, Direction.forward);
    private final UniverseStatistics                                                                universeStatistics;
    private final AtomicReference<Set<Throwable>>                                                   errors                  = new AtomicReference<>(Set.of());
    protected final ConstantState                                                                   constantState           = new ConstantState(this::handleException);

    private List<Action<Universe>>                                                                  timeTravelingActions    = List.of(backward, forward);
    private List<Action<Universe>>                                                                  preActions              = List.of();
    private List<Action<Universe>>                                                                  postActions             = List.of();
    private List<State>                                                                             history                 = List.of();
    private List<State>                                                                             future                  = List.of();
    private State                                                                                   preState;
    private State                                                                                   startState;
    private State                                                                                   state;
    protected boolean                                                                               initialized;
    private boolean                                                                                 killed;
    private boolean                                                                                 timeTraveling;
    private boolean                                                                                 handling;
    private boolean                                                                                 stopped;
    private boolean                                                                                 orphansDetected;

    protected UniverseTransaction(Universe universe, ContextPool pool, State start, int maxInInQueue, int maxTotalNrOfChanges, int maxNrOfChanges, int maxNrOfObserved, int maxNrOfObservers, int maxNrOfHistory, Consumer<UniverseTransaction> cycle) {
        super(null);
        this.cycle = cycle != null ? Action.of("$cycle", o -> cycle.accept(this)) : null;
        this.inQueue = new LinkedBlockingQueue<>(maxInInQueue);
        this.universeStatistics = new UniverseStatistics(this, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
        start(universe, null);
        preState = emptyState;
        pool.execute(() -> mainLoop(start));
        init();
    }

    protected void mainLoop(State start) {
        state = start != null ? start.clone(this) : emptyState;
        if (TRACE_UNIVERSE) {
            System.err.println("DCLARE: START UNIVERSE " + this);
        }
        while (!killed) {
            try {
                handling = false;
                Action<Universe> action = take();
                preState = state;
                universeStatistics.setDebugging(false);
                handling = true;
                if (TRACE_UNIVERSE) {
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
                    if (!killed && inQueue.isEmpty()) {
                        if (isStopped(state)) {
                            break;
                        }
                        if (this.cycle != null) {
                            put(this.cycle);
                        }
                    }
                } catch (Throwable t) {
                    handleException(t);
                } finally {
                    if (TRACE_UNIVERSE) {
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
        if (TRACE_UNIVERSE) {
            System.err.println("DCLARE: STOP UNIVERSE " + this);
        }
        stop();
        history = history.append(state);
        constantState.stop();
        end(state);
        stopped = true;
    }

    @Override
    protected State run(State state) {
        do {
            startState = state;
            state = state.set(universe(), Mutable.D_CHANGE_NR, INCREMENT);
            state = super.run(state);
            state = clearOrphans(state);
        } while (!killed && hasBackwardActionsQueued(state));
        return state;
    }

    private boolean hasBackwardActionsQueued(State state) {
        boolean result = hasQueued(state, universe(), Direction.backward);
        if (TRACE_UNIVERSE && result) {
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

    public boolean isHandling() {
        return handling;
    }

    public boolean isStopped() {
        return stopped;
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
        if (TRACE_UNIVERSE) {
            List<Throwable> list = errors.sorted(this::compareThrowable).toList();
            System.err.println("DCLARE: EXCEPTION " + this);
            list.first().printStackTrace();
        }
        kill();
    }

    protected final void handleException(Throwable t) {
        handleExceptions(errors.updateAndGet(e -> e.add(t)));
    }

    public Set<Throwable> errors() {
        return errors.get();
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
        put("$init", () -> universe().init());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void checkConsistency(Universe universe) {
        LeafTransaction lt = LeafTransaction.getCurrent();
        State post = lt.state();
        preState.diff(post, o -> o instanceof Mutable).forEach(e0 -> {
            Mutable mutable = (Mutable) e0.getKey();
            DefaultMap<Setable, Object> values = e0.getValue().b();
            if (mutable instanceof Universe || values.get(Mutable.D_PARENT_CONTAINING) != null) {
                MutableClass dClass = mutable.dClass();
                Collection.concat(values.map(Entry::getKey), dClass.dSetables(), dClass.dObservers().map(Observer::exception)).distinct().filter(Setable::checkConsistency).forEach(s -> {
                    if (!(s instanceof Constant) || constantState.isSet(lt, mutable, (Constant) s)) {
                        Set<Throwable> es = ((Setable) s).checkConsistency(post, mutable, s instanceof Constant ? constantState.get(lt, mutable, (Constant) s) : values.get(s));
                        errors.updateAndGet(es::addAll);
                    }
                });
            } else {
                checkOrphanState(e0);
            }
        });
        Set<Throwable> es = errors.get();
        if (!es.isEmpty()) {
            handleExceptions(es);
        }
    }

    @SuppressWarnings("rawtypes")
    protected void checkOrphanState(Entry<Object, Pair<DefaultMap<Setable, Object>, DefaultMap<Setable, Object>>> e0) {
        if (CHECK_ORPHAN_STATE && !e0.getValue().b().isEmpty()) {
            throw new Error("Orphan '" + e0.getKey() + "' has state '" + e0.getValue().b() + "'");
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
        return trigger(state, universe(), action, Direction.scheduled);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
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

    @SuppressWarnings("rawtypes")
    protected void clearOrphans(Universe universe) {
        LeafTransaction tx = LeafTransaction.getCurrent();
        Map<Object, Map<Setable, Pair<Object, Object>>> orphans = preState()//
                .diff(tx.state(), o -> {
                    if (o instanceof Mutable && !(o instanceof Universe)) {
                        return tx.get((Mutable) o, Mutable.D_PARENT_CONTAINING) == null && !tx.toBeCleared((Mutable) o).isEmpty();
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

    protected void put(Action<Universe> action) {
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

    protected void end(State state) {
        try {
            resultQueue.put(state);
        } catch (InterruptedException e) {
            throw new Error(e);
        }
    }

    public State waitForEnd() {
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

    protected State startState() {
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

}
