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

import static org.modelingvalue.dclare.State.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

import org.modelingvalue.collections.*;
import org.modelingvalue.collections.util.*;
import org.modelingvalue.collections.util.ContextThread.*;
import org.modelingvalue.dclare.NonCheckingObserver.*;
import org.modelingvalue.dclare.ex.*;

@SuppressWarnings("unused")
public class UniverseTransaction extends MutableTransaction {

    private static final boolean TRACE_UNIVERSE          = Boolean.getBoolean("TRACE_UNIVERSE");

    public static final int      MAX_IN_IN_QUEUE         = Integer.getInteger("MAX_IN_IN_QUEUE", 100);
    public static final int      MAX_TOTAL_NR_OF_CHANGES = Integer.getInteger("MAX_TOTAL_NR_OF_CHANGES", 10000);
    public static final int      MAX_NR_OF_CHANGES       = Integer.getInteger("MAX_NR_OF_CHANGES", 200);
    public static final int      MAX_NR_OF_OBSERVED      = Integer.getInteger("MAX_NR_OF_OBSERVED", 1000);
    public static final int      MAX_NR_OF_OBSERVERS     = Integer.getInteger("MAX_NR_OF_OBSERVERS", 1000);
    public static final int      MAX_NR_OF_HISTORY       = Integer.getInteger("MAX_NR_OF_HISTORY", 64) + 3;

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
    private final AtomicReference<ConsistencyError>                                                 consistencyError        = new AtomicReference<>(null);
    //
    private              List<Action<Universe>>                                                          timeTravelingActions    = List.of(backward, forward);
    private         List<Action<Universe>> postActions             = List.of();
    private         List<State>            history                 = List.of();
    private         List<State>            future                  = List.of();
    private         State                  preState;
    private         State                  state;
    protected final ConstantState          constantState           = new ConstantState(this::handleException);
    protected       boolean                initialized;
    private         boolean                killed;
    private         boolean                timeTraveling;
    private         Throwable              error;
    private         boolean                handling;
    private         boolean                stopped;

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

    protected void addTimeTravelingAction(Action<Universe> action) {
        timeTravelingActions = timeTravelingActions.add(action);
    }

    protected void mainLoop(State start) {
        state = start != null ? start.clone(this) : emptyState;
        while (!killed) {
            try {
                handling = false;
                Action<Universe> leaf = take();
                handling = true;
                universeStatistics.setDebugging(false);
                preState = state;
                if (TRACE_UNIVERSE) {
                    System.err.println("DCLARE: START UNIVERSE " + this);
                }
                TraceTimer.traceBegin("root");
                try {
                    timeTraveling = timeTravelingActions.contains(leaf);
                    start(leaf);
                    if (leaf == backward) {
                        if (history.size() > 3) {
                            future = future.prepend(state);
                            state = history.last();
                            history = history.removeLast();
                        }
                    } else if (leaf == forward) {
                        if (!future.isEmpty()) {
                            history = history.append(state);
                            state = future.first();
                            future = future.removeFirst();
                        }
                    } else if (leaf != dummy) {
                        history = history.append(state);
                        future = List.of();
                        if (history.size() > universeStatistics.maxNrOfHistory()) {
                            history = history.removeFirst();
                        }
                        state = state.get(() -> run(trigger(pre(state), universe(), leaf, leaf.initDirection())));
                        state = state.get(() -> post(state));
                        if (stats().debugging()) {
                            handleTooManyChanges(state);
                        }
                    }
                    if (!killed) {
                        state = state.get(() -> run(triggerPostActions(state, postActions)));
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
                    end(leaf);
                    universeStatistics.completeRun();
                    TraceTimer.traceEnd("root");
                }
            } catch (Throwable t) {
                handleException(t);
            }
            if (TRACE_UNIVERSE) {
                System.err.println("DCLARE: STOP UNIVERSE " + this);
            }
        }
        stop();
        history = history.append(state);
        constantState.stop();
        end(state);
        stopped = true;
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

    protected void handleException(Throwable t) {
        if (error == null) {
            error = t;
        }
        if (TRACE_UNIVERSE) {
            System.err.println("Exception in Universe:");
            t.printStackTrace();
        }
        kill();
    }

    public void throwIfError() {
        Throwable e = error;
        if (e != null) {
            throw new Error(e);
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
            if (e0.getKey() instanceof Universe || e0.getValue().b().get(Mutable.D_PARENT_CONTAINING) != null) {
                MutableClass dClass = ((Mutable) e0.getKey()).dClass();
                Collection.concat(dClass.dSetables().filter(Setable::checkConsistency), dClass.dObservers().map(Observer::exception)).forEach(s -> {
                    if (!(s instanceof Constant) || constantState.isSet(lt, e0.getKey(), (Constant) s)) {
                        try {
                            ((Setable) s).checkConsistency(post, e0.getKey(), s instanceof Constant ? constantState.get(lt, e0.getKey(), (Constant) s) : e0.getValue().b().get(s));
                        } catch (ConsistencyError e) {
                            consistencyError.updateAndGet(p -> p == null ? e : e.compareTo(p) < 0 ? e : p);
                        }
                    }
                });
            } else {
                checkOrphanState(e0);
            }
        });
        ConsistencyError error = consistencyError.getAndSet(null);
        if (error != null) {
            throw error;
        }
    }

    @SuppressWarnings("rawtypes")
    protected void checkOrphanState(Entry<Object, Pair<DefaultMap<Setable, Object>, DefaultMap<Setable, Object>>> e0) {
        if (!e0.getValue().b().isEmpty()) {
            throw new Error("Orphan '" + e0.getKey() + "' has state '" + e0.getValue().b() + "'");
        }
    }

    public Universe universe() {
        return (Universe) mutable();
    }

    private <O extends Mutable> State triggerPostActions(State state, List<Action<Universe>> actions) {
        for (Action<Universe> action : actions) {
            state = trigger(state, universe(), action, action.initDirection());
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void handleTooManyChanges(State state) {
        ObserverTrace trace = state//
                .filter(o -> o instanceof Mutable, s -> s.id instanceof Pair && ((Pair) s.id).a() instanceof Observer && ((Pair) s.id).b().equals("TRACES"))//
                .flatMap(e1 -> e1.getValue().map(e2 -> ((Set<ObserverTrace>) e2.getValue()).sorted().findFirst().orElseThrow()))//
                .min((a, b) -> Integer.compare(b.done().size(), a.done().size()))//
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

    protected State pre(State state) {
        return state;
    }

    @SuppressWarnings("rawtypes")
    protected void clearOrphans(Universe universe) {
        LeafTransaction tx = LeafTransaction.getCurrent();
        State st = tx.state();
        //TODO: see DCL-150
        Map<Object, Map<Setable, Pair<Object, Object>>> changed //
                = preState()//
                        .diff(st, o -> o instanceof Mutable && !(o instanceof Universe) && st.get((Mutable) o, Mutable.D_PARENT_CONTAINING) == null, ALL_SETTABLES)//
                        .toMap(Function.identity());
        changed.forEachOrdered(e0 -> clear(tx, (Mutable) e0.getKey()));
        changed.forEachOrdered(e0 -> clear(tx, (Mutable) e0.getKey()));
    }

    protected void clear(LeafTransaction tx, Mutable orphan) {
        tx.clear(orphan);
        for (Mutable child : orphan.dChildren()) {
            clear(tx, child);
        }
    }

    protected State post(State pre) {
        if (initialized) {
            pre = run(trigger(pre, universe(), clearOrphans, Direction.backward));
            return run(trigger(pre, universe(), checkConsistency, Direction.backward));
        } else {
            return pre;
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
            if (error != null) {
                throw new Error("Error in engine " + state.get(() -> error.getMessage()), error);
            }
            return state;
        } catch (InterruptedException e) {
            throw new Error(e);
        }
    }

    public void addDiffHandler(String id, TriConsumer<State, State, Boolean> diffHandler) {
        Action<Universe> action = Action.of(id, o -> {
            LeafTransaction tx = ActionTransaction.getCurrent();
            diffHandler.accept(tx.universeTransaction().preState(), tx.state(), true);
        });
        synchronized (this) {
            postActions = postActions.add(action);
        }
    }

    public ImperativeTransaction addImperative(String id, Consumer<State> firstHandler, TriConsumer<State, State, Boolean> diffHandler, Consumer<Runnable> scheduler, boolean keepTransaction) {
        ImperativeTransaction n = ImperativeTransaction.of(Imperative.of(id), preState, this, scheduler, firstHandler, diffHandler, keepTransaction);
        Action<Universe> action = Action.of(id, o -> {
            LeafTransaction tx = ActionTransaction.getCurrent();
            State pre = tx.state();
            boolean timeTraveling = tx.universeTransaction().isTimeTraveling();
            n.schedule(() -> n.commit(pre, timeTraveling));
        });
        synchronized (this) {
            postActions = postActions.add(action);
        }
        return n;
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
