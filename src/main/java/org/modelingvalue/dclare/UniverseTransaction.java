//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2023 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import java.util.Iterator;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Concurrent;
import org.modelingvalue.collections.util.ContextThread.ContextPool;
import org.modelingvalue.collections.util.StatusProvider;
import org.modelingvalue.collections.util.StatusProvider.AbstractStatus;
import org.modelingvalue.collections.util.StatusProvider.StatusIterator;
import org.modelingvalue.collections.util.TraceTimer;
import org.modelingvalue.dclare.NonCheckingObserver.NonCheckingTransaction;
import org.modelingvalue.dclare.Priority.MutableStates;
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
    protected final Concurrent<ReusableTransaction<LazyDerivation, LazyDerivationTransaction>>         lazyDerivations         = Concurrent.of(() -> new ReusableTransaction<>(this));
    protected final Concurrent<ReusableTransaction<IdentityDerivation, IdentityDerivationTransaction>> identityDerivations     = Concurrent.of(() -> new ReusableTransaction<>(this));
    protected final Concurrent<ReusableTransaction<NonCheckingObserver<?>, NonCheckingTransaction>>    nonCheckingTransactions = Concurrent.of(() -> new ReusableTransaction<>(this));
    //
    private final Action<Universe>                                                                     init                    = Action.of("$init", o -> universe().init());
    private final Action<Universe>                                                                     stop                    = Action.of("$stop", o -> STOPPED.set(universe(), true));
    private final Action<Universe>                                                                     backward                = Action.of("$backward");
    private final Action<Universe>                                                                     forward                 = Action.of("$forward");
    private final Action<Universe>                                                                     commit                  = Action.of("$commit");
    private final Action<Universe>                                                                     clearOrphans            = Action.of("$clearOrphans", this::clearOrphans);
    private final Action<Universe>                                                                     checkConsistency        = Action.of("$checkConsistency", this::checkConsistency);
    //
    protected final BlockingQueue<Action<Universe>>                                                    inQueue;
    private final BlockingQueue<State>                                                                 resultQueue             = new LinkedBlockingQueue<>(1);                          //TODO wire onto MoodManager
    private final State                                                                                emptyState              = createState(StateMap.EMPTY_STATE_MAP);
    private final State                                                                                startState;
    protected final ReadOnly                                                                           runOnState              = new ReadOnly(this, Priority.one);
    protected final Derivation                                                                         derivation              = new Derivation(this, Priority.one);
    protected final IdentityDerivation                                                                 identityDerivation      = new IdentityDerivation(this, Priority.one);
    protected final LazyDerivation                                                                     lazyDerivation          = new LazyDerivation(this, Priority.one);
    private final UniverseStatistics                                                                   universeStatistics;
    protected final AtomicReference<Set<Throwable>>                                                    errors                  = new AtomicReference<>(Set.of());
    private final AtomicReference<Set<Throwable>>                                                      inconsistencies         = new AtomicReference<>(Set.of());
    private final AtomicReference<Boolean>                                                             orphansDetected         = new AtomicReference<>(null);
    private final ConstantState                                                                        constantState           = new ConstantState("CONST", this::handleException);
    private final StatusProvider<Status>                                                               statusProvider;
    private final Timer                                                                                timer                   = new Timer("UniverseTransactionTimer", true);
    private final MutableStates                                                                        preStartStates;
    private final MutableStates                                                                        startStates;
    private final List<IState>                                                                         states;
    //
    private List<Action<Universe>>                                                                     timeTravelingActions    = List.of(backward, forward);
    private List<Action<Universe>>                                                                     preActions              = List.of();
    private List<Action<Universe>>                                                                     postActions             = List.of();
    private List<ImperativeTransaction>                                                                imperativeTransactions  = List.of();
    private List<State>                                                                                history                 = List.of();
    private List<State>                                                                                future                  = List.of();
    private State                                                                                      preState;
    private State                                                                                      postState;
    private State                                                                                      preOrphansState;
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

    public enum Mood {
        starting(),
        busy(),
        idle(),
        stopped()
    }

    public UniverseTransaction(Universe universe, ContextPool pool) {
        this(universe, pool, new DclareConfig());
    }

    public UniverseTransaction(Universe universe, ContextPool pool, DclareConfig config) {
        this(universe, pool, config, null);
    }

    public UniverseTransaction(Universe universe, ContextPool pool, DclareConfig config, Consumer<Status> startStatusConsumer) {
        this(universe, pool, config, startStatusConsumer, null);
    }

    public UniverseTransaction(Universe universe, ContextPool pool, DclareConfig config, Consumer<Status> startStatusConsumer, StateMap startStateMap) {
        super(null);
        if (universe == null) {
            throw new IllegalArgumentException("UniverseTransaction can not start without a Universe (universe argument is null)");
        }
        State initState = createStartState(universe, startStateMap);
        startState = initState.get(() -> incrementChangeId(universe, initState));
        Status startStatus = new Status(Mood.starting, null, startState, null, Set.of());
        statusProvider = new StatusProvider<>(this, startStatus);
        this.config = Objects.requireNonNull(config);
        inQueue = new LinkedBlockingQueue<>(config.getMaxInInQueue());
        universeStatistics = new UniverseStatistics(this);
        start(universe, null);
        preState = startState;
        preStartStates = new MutableStates(Priority.two, () -> createMutableState(emptyState));
        startStates = new MutableStates(Priority.two, () -> createMutableState(emptyState));
        List<IState> states = List.of();
        for (int i = 0; i < startStates.length(); i++) {
            Priority p = startStates.priority(i);
            states = states.add(preStartState(p));
            states = states.add(startState(p));
        }
        this.states = states;
        pool.execute(this::mainLoop);
        init();
        if (startStatusConsumer != null) {
            startStatusConsumer.accept(startStatus);
        }
    }

    private State createStartState(Universe universe, StateMap stateMap) {
        if (stateMap != null) {
            // take care that the startStateMap does not contain the STOPPED state
            stateMap = stateMap.clear(universe, STOPPED);
        }
        return stateMap == null || stateMap.isEmpty() ? emptyState : createState(stateMap);
    }

    protected State createState(StateMap stateMap) {
        return new State(this, stateMap);
    }

    protected MutableState createMutableState(State state) {
        return new MutableState(state);
    }

    protected void mainLoop() {
        state = startState;
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
        statusProvider.setNext(p -> new Status(Mood.busy, action, p.state, new UniverseStatistics(stats()), p.active));
    }

    private void setIdleMood(State state) {
        statusProvider.setNext(p -> new Status(Mood.idle, p.action, state, new UniverseStatistics(stats()), p.active));
    }

    private void setStoppedMood(State state) {
        statusProvider.setNext(p -> new Status(Mood.stopped, p.action, state, new UniverseStatistics(stats()), p.active));
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
                UniverseStatistics stats = new UniverseStatistics(stats());
                if (!Objects.equals(p.stats, stats)) {
                    return new Status(p.mood, p.action, p.state, stats, p.active);
                }
            }
            return p;
        });
    }

    @Override
    protected State run(State state) {
        Priority priority = Priority.OUTER;
        try {
            preStartState(Priority.OUTER).setState(state);
            state = incrementChangeId(universe(), state);
            for (int i = Priority.OUTER.ordinal() - 1; i >= Priority.INNER.ordinal(); i--) {
                preStartState(Priority.ALL[i]).setState(state);
            }
            do {
                if (priority == Priority.OUTER) {
                    tmpConstants = new ConstantState("TEMP", this::handleException);
                    preOrphansState = state;
                }
                for (int i = priority.ordinal(); i >= Priority.INNER.ordinal(); i--) {
                    startState(Priority.ALL[i]).setState(state);
                }
                state = incrementChangeId(universe(), state);
                state = super.run(state);
                if (postState == null) {
                    postState = state;
                }
                if (!killed && orphansDetected.get() == Boolean.TRUE) {
                    preOrphansState = startState(Priority.INNER).preState();
                    state = trigger(state, universe(), clearOrphans, Priority.INNER);
                    priority = Priority.two;
                } else {
                    priority = killed ? null : hasQueued(state);
                    if (!killed && (priority == null || priority == Priority.OUTER) && orphansDetected.get() == null) {
                        state = trigger(state, universe(), clearOrphans, Priority.four);
                        priority = Priority.four;
                    } else if (priority != null && orphansDetected.get() == Boolean.FALSE) {
                        preOrphansState = state;
                        orphansDetected.set(null);
                    }
                }
                if (priority != null) {
                    for (int i = priority.ordinal(); i >= Priority.INNER.ordinal(); i--) {
                        preStartState(Priority.ALL[i]).setState(startState(Priority.ALL[i]).state());
                    }
                }
                if (priority == null || priority == Priority.OUTER) {
                    universeStatistics.bumpForwardCount();
                    tmpConstants.stop();
                    tmpConstants = null;
                }
            } while (priority != null);
            return state;
        } finally {
            postState = null;
            preStartStates.setState(emptyState);
            startStates.setState(emptyState);
            preOrphansState = null;
            orphansDetected.set(null);
        }
    }

    protected final State incrementChangeId(Universe universe, State state) {
        return state.set(universe, Mutable.D_CHANGE_ID, TransactionId.of(transactionNumber++));
    }

    private Priority hasQueued(State state) {
        for (int i = 0; i < startStates.length(); i++) {
            Priority priority = startStates.priority(i);
            boolean result = hasQueued(state, universe(), priority);
            if (result) {
                if (config.isTraceUniverse()) {
                    System.err.println(DclareTrace.getLineStart("DCLARE", this) + priority.name().toUpperCase() + " " + this);
                }
                return priority;
            }
        }
        return null;
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

    protected void handleExceptions(Set<Throwable> exceptions) {
        errors.updateAndGet(exceptions::addAll);
        if (config.isTraceUniverse()) {
            List<Throwable> list = errors.get().sorted(this::compareThrowable).asList();
            System.err.println(DclareTrace.getLineStart("DCLARE", this) + list.size() + " EXCEPTION(S) " + this);
            list.first().printStackTrace();
        }
        kill();
    }

    public final void handleException(Throwable t) {
        handleExceptions(Set.of(t));
    }

    public void throwIfError() {
        Set<Throwable> es = errors.get();
        if (!es.isEmpty()) {
            List<Throwable> list = es.sorted(this::compareThrowable).asList();
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
            handleExceptions(result);
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
        return trigger(state, universe(), action, Priority.zero);
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantSuppression"})
    private void handleTooManyChanges(State state) {
        if (!killed && stats().debugging() && !errors.get().anyMatch(e -> e instanceof TooManyChangesException)) {
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
        Set<Mutable> orphans = preOrphansState.diff(postState, o -> {
            return o instanceof Mutable && ((Mutable) o).dIsOrphan(postState) && !tx.toBeCleared((Mutable) o).isEmpty();
        }).map(e -> (Mutable) e.getKey()).asSet();
        orphansDetected.set(!orphans.isEmpty());
        orphans.forEach(tx::clearOrphan);
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

    public ImperativeTransaction addImperative(String id, StateDeltaHandler diffHandler, Consumer<Runnable> scheduler, boolean keepTransaction) {
        ImperativeTransaction n = ImperativeTransaction.of(Imperative.of(id), preState, this, scheduler, diffHandler, keepTransaction);
        synchronized (this) {
            imperativeTransactions = imperativeTransactions.add(n);
        }
        return n;
    }

    public List<ImperativeTransaction> getImperativeTransactions() {
        return imperativeTransactions;
    }

    public ImperativeTransaction getImperativeTransaction(String id) {
        for (ImperativeTransaction it : imperativeTransactions) {
            if (it.imperative().id().equals(id)) {
                return it;
            }
        }
        return null;
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

    public State postState() {
        return postState;
    }

    public State currentState() {
        return state;
    }

    public MutableState preStartState(Priority priority) {
        return preStartStates.get(priority);
    }

    public MutableState startState(Priority priority) {
        return startStates.get(priority);
    }

    public State startState() {
        return startState;
    }

    public List<State> history() {
        return history;
    }

    public Collection<IState> longHistory() {
        return Collection.concat(states, history);
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
        TransactionId txid = startState(Priority.OUTER).transactionId();
        for (int i = 0; i < startStates.length() - 1; i++) {
            Priority p = startStates.priority(i);
            preStartState(p).set(object, property, post, txid);
            startState(p).set(object, property, post, txid);
        }
        startState(Priority.OUTER).set(object, property, post, txid);
        return txid;
    }
}
