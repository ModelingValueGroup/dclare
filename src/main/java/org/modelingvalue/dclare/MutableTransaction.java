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

import static org.modelingvalue.dclare.Mutable.D_PARENT_CONTAINING;
import static org.modelingvalue.dclare.Priority.*;

import java.util.Objects;

import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Concurrent;
import org.modelingvalue.collections.util.NotMergeableException;
import org.modelingvalue.collections.util.StringUtil;
import org.modelingvalue.collections.util.TraceTimer;
import org.modelingvalue.dclare.Observed.Observers;
import org.modelingvalue.dclare.Priority.Queued;
import org.modelingvalue.dclare.ex.TransactionException;

public class MutableTransaction extends Transaction implements StateMergeHandler {

    @SuppressWarnings("rawtypes")
    private final Concurrent<Map<Observer, Set<Mutable>>> triggeredActions;
    private final Concurrent<Set<Mutable>>[]              triggeredMutables;
    @SuppressWarnings("unchecked")
    private final Set<Action<?>>[]                        actions  = new Set[1];
    @SuppressWarnings("unchecked")
    private final Set<Mutable>[]                          children = new Set[1];
    private final State[]                                 state    = new State[1];

    @SuppressWarnings("unchecked")

    protected MutableTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
        triggeredActions = Concurrent.of();
        triggeredMutables = new Concurrent[NON_SCHEDULED.length];
        for (int i = 0; i < NON_SCHEDULED.length; i++) {
            triggeredMutables[i] = Concurrent.of();
        }
    }

    @Override
    public final Mutable mutable() {
        return (Mutable) cls();
    }

    protected boolean hasQueued(State state, Mutable object, Priority prio) {
        return !state.get(object, prio.actions).isEmpty() || !state.get(object, prio.children).isEmpty();
    }

    private void move(Mutable object, Priority from, Priority to) {
        state[0] = state[0].set(object, from.actions, Set.of(), actions);
        state[0] = state[0].set(object, to.actions, Set::addAll, actions[0]);
        state[0] = state[0].set(object, from.children, Set.of(), children);
        state[0] = state[0].set(object, to.children, Set::addAll, children[0]);
        for (Mutable child : children[0].filter(Mutable.class)) {
            move(child, from, to);
        }
    }

    private State remove(State state, Priority prio, TransactionClass tc) {
        if (tc instanceof Mutable) {
            return state.set(mutable(), prio.children, Set::remove, tc);
        } else {
            return state.set(mutable(), prio.actions, Set::remove, tc);
        }
    }

    @Override
    protected State run(State pre) {
        TraceTimer.traceBegin("compound");
        state[0] = pre;
        try {
            move(mutable(), immediate, scheduled);
            if (parent() == null) {
                for (int i = 1; i < NON_SCHEDULED.length && !hasQueued(state[0], mutable(), scheduled); i++) {
                    move(mutable(), NON_SCHEDULED[i], scheduled);
                }
            }
            while (!universeTransaction().isKilled()) {
                state[0] = state[0].set(mutable(), scheduled.actions, Set.of(), actions);
                if (!actions[0].isEmpty()) {
                    run(actions[0], scheduled.actions);
                } else {
                    state[0] = state[0].set(mutable(), scheduled.children, Set.of(), children);
                    if (!children[0].isEmpty()) {
                        run(children[0], scheduled.children);
                    } else {
                        if (parent() != null) {
                            for (int i = 1; i < NON_SCHEDULED.length; i++) {
                                if (hasQueued(state[0], mutable(), NON_SCHEDULED[i])) {
                                    state[0] = state[0].set(parent().mutable(), NON_SCHEDULED[i].children, Set::add, mutable());
                                }
                            }
                        }
                        break;
                    }
                }
            }
            return state[0];
        } catch (Throwable t) {
            universeTransaction().handleException(new TransactionException(mutable(), t));
            return state[0];
        } finally {
            state[0] = null;
            actions[0] = null;
            children[0] = null;
            TraceTimer.traceEnd("compound");
        }
    }

    private <T extends TransactionClass> void run(Set<T> todo, Queued<T> queued) {
        List<T> list = todo.random().toList();
        if (universeTransaction().getConfig().isTraceMutable()) {
            System.err.println(DclareTrace.getLineStart("DCLARE", this) + mutable() + " " + list.toString().substring(4));
        }
        if (list.size() <= 2 || universeTransaction().getConfig().isRunSequential()) {
            runSequential(list);
        } else {
            int half = list.size() >> 1;
            runParallel(list.sublist(0, half));
            if (!universeTransaction().isKilled()) {
                state[0] = state[0].set(mutable(), queued, Set::addAll, list.sublist(half, list.size()));
            }
        }
        if (!universeTransaction().isKilled()) {
            move(mutable(), immediate, scheduled);
        }
    }

    private <T extends TransactionClass> void runParallel(List<T> todo) {
        if (todo.size() > 1) {
            try {
                State[] branches = todo.reduce(state, this::accumulate, MutableTransaction::combine);
                state[0] = merge(state[0], branches);
            } catch (NotMergeableException nme) {
                runSequential(todo);
            }
        } else {
            runSequential(todo);
        }
    }

    private <T extends TransactionClass> State[] accumulate(State[] a, T tc) {
        State[] r = a.clone();
        int lastIndex = r.length - 1;
        r[lastIndex] = tc.run(remove(a[lastIndex], immediate, tc), this);
        return r;
    }

    private static State[] combine(State[] a, State[] b) {
        State[] r = new State[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private <T extends TransactionClass> void runSequential(List<T> todo) {
        State result;
        for (TransactionClass tc : todo) {
            result = tc.run(remove(state[0], immediate, tc), this);
            if (universeTransaction().isKilled()) {
                return;
            }
            state[0] = result;
        }
    }

    private State merge(State base, State[] branches) {
        if (universeTransaction().isKilled()) {
            return base;
        } else if (branches.length == 1) {
            return branches[0];
        } else {
            TraceTimer.traceBegin("merge");
            triggeredActions.init(Map.of());
            for (int i = 0; i < NON_SCHEDULED.length; i++) {
                triggeredMutables[i].init(Set.of());
            }
            try {
                State state = base.merge(this, branches, branches.length);
                state = trigger(state, triggeredActions.result(), immediate);
                for (int i = 0; i < NON_SCHEDULED.length; i++) {
                    state = triggerMutables(state, triggeredMutables[i].result(), NON_SCHEDULED[i]);
                }
                return state;
            } finally {
                triggeredActions.clear();
                for (int i = 0; i < NON_SCHEDULED.length; i++) {
                    triggeredMutables[i].clear();
                }
                TraceTimer.traceEnd("merge");
            }
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void handleMergeConflict(Object object, Setable property, Object pre, Object... branches) {
        throw new NotMergeableException(object + "." + property + "= " + pre + " -> " + StringUtil.toString(branches));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void handleChange(Object object, Setable setable, DefaultMap<Setable, Object> baseValues, DefaultMap<Setable, Object>[] branchesValues, DefaultMap<Setable, Object> resultValues) {
        if (setable instanceof Observers) {
            Observers<?, ?> os = (Observers) setable;
            DefaultMap<Observer, Set<Mutable>> baseObservers = State.get(baseValues, os);
            DefaultMap<Observer, Set<Mutable>> resultObservers = State.get(resultValues, os);
            os.observed().checkTooManyObservers(universeTransaction(), object, resultObservers);
            DefaultMap<Observer, Set<Mutable>> addedResultObservers = resultObservers.removeAll(baseObservers, Set::removeAll);
            if (!addedResultObservers.isEmpty()) {
                Observed<?, ?> observedProp = os.observed();
                Object baseValue = State.get(baseValues, observedProp);
                for (DefaultMap<Setable, Object> branchValues : branchesValues) {
                    Object branchValue = State.get(branchValues, observedProp);
                    if (!Objects.equals(branchValue, baseValue)) {
                        DefaultMap<Observer, Set<Mutable>> branchObservers = State.get(branchValues, os);
                        Map<Observer, Set<Mutable>> missingBranchObservers = addedResultObservers.removeAll(branchObservers, Set::removeAll).//
                                toMap(e -> Entry.of(e.getKey(), e.getValue().map(m -> m.dResolve((Mutable) object)).toSet()));
                        triggeredActions.change(ts -> ts.addAll(missingBranchObservers, Set::addAll));
                    }
                }
            }
        } else if (setable instanceof Queued) {
            Queued<TransactionClass> q = (Queued) setable;
            if (q.priority() != scheduled) {
                Set<TransactionClass> resultTriggered = State.get(resultValues, q);
                Set<TransactionClass> baseTriggered = State.get(baseValues, q);
                if (!resultTriggered.removeAll(baseTriggered).isEmpty()) {
                    Mutable resultParent = State.getA(resultValues, D_PARENT_CONTAINING);
                    if (resultParent != null) {
                        for (DefaultMap<Setable, Object> branchValues : branchesValues) {
                            Mutable branchParent = State.getA(branchValues, D_PARENT_CONTAINING);
                            if (!resultParent.equals(branchParent)) {
                                triggeredMutables[q.priority().ordinal()].change(ts -> ts.add(resultParent));
                                break;
                            }
                        }
                    }
                }
            }
        }

    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private State trigger(State state, Map<Observer, Set<Mutable>> leafs, Priority priority) {
        for (Entry<Observer, Set<Mutable>> e : leafs) {
            for (Mutable m : e.getValue()) {
                state = trigger(state, m, e.getKey(), priority);
                if (universeTransaction().getConfig().isTraceMutable()) {
                    System.err.println(DclareTrace.getLineStart("DCLARE", this) + mutable() + " TRIGGER " + m + "." + e.getKey() + " " + priority);
                }
            }
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private State triggerMutables(State state, Set<Mutable> mutables, Priority priority) {
        for (Mutable mutable : mutables) {
            state = trigger(state, mutable, null, priority);
            if (universeTransaction().getConfig().isTraceMutable()) {
                System.err.println(DclareTrace.getLineStart("DCLARE", this) + mutable() + " TRIGGER " + mutable + " " + priority);
            }
        }
        return state;
    }

    protected final <O extends Mutable> State trigger(State state, O target, Action<O> action, Priority priority) {
        Mutable object = target;
        if (action != null) {
            state = state.set(object, priority.actions, Set::add, action);
        }
        Mutable parent = state.getA(object, D_PARENT_CONTAINING);
        while (parent != null && !mutable().equals(object)) {
            state = state.set(parent, priority.children, Set::add, object);
            object = parent;
            parent = state.getA(object, D_PARENT_CONTAINING);
        }
        return state;
    }

    protected State lastState() {
        return state[0];
    }

    @Override
    protected String getCurrentTypeForTrace() {
        return "MU";
    }

}
