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

import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Concurrent;
import org.modelingvalue.collections.util.NotMergeableException;
import org.modelingvalue.collections.util.StringUtil;
import org.modelingvalue.collections.util.TraceTimer;
import org.modelingvalue.dclare.Observed.Observers;
import org.modelingvalue.dclare.Priority.Queued;
import org.modelingvalue.dclare.ex.TransactionException;

import java.util.Objects;

public class MutableTransaction extends Transaction implements StateMergeHandler {
    @SuppressWarnings("rawtypes")
    private final Concurrent<Map<Observer, Set<Mutable>>> triggeredActions;
    private final Concurrent<Set<Mutable>>[]              triggeredChildren;
    @SuppressWarnings("unchecked")
    private final Set<Action<?>>[]                        actions  = new Set[1];
    @SuppressWarnings("unchecked")
    private final Set<Mutable>[]                          children = new Set[1];
    private final State[]                                 state    = new State[1];

    private boolean                                       urgent;
    private Set<State>                                    states;

    @SuppressWarnings("unchecked")

    protected MutableTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
        triggeredActions = Concurrent.of();
        triggeredChildren = new Concurrent[]{Concurrent.of(), Concurrent.of(), Concurrent.of()};
    }

    @Override
    public final Mutable mutable() {
        return (Mutable) cls();
    }

    protected boolean hasQueued(State state, Mutable object, Priority dir) {
        return !state.get(object, dir.actions).isEmpty() || !state.get(object, dir.children).isEmpty();
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

    @Override
    protected State run(State pre) {
        urgent = parent() != null && parent().urgent;
        TraceTimer.traceBegin("compound");
        state[0] = pre;
        states = Set.of(pre);
        try {
            if (parent() == null) {
                move(mutable(), Priority.backward, Priority.scheduled);
            }
            while (!universeTransaction().isKilled()) {
                state[0] = state[0].set(mutable(), Priority.scheduled.actions, Set.of(), actions);
                if (!actions[0].isEmpty()) {
                    run(actions[0], Priority.scheduled.actions, states);
                } else {
                    state[0] = state[0].set(mutable(), Priority.scheduled.children, Set.of(), children);
                    if (!children[0].isEmpty()) {
                        run(children[0], Priority.scheduled.children, states);
                    } else {
                        if (parent() != null && hasQueued(state[0], mutable(), Priority.backward)) {
                            state[0] = state[0].set(parent().mutable(), Priority.backward.children, Set::add, mutable());
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
            states = null;
            urgent = false;
            state[0] = null;
            actions[0] = null;
            children[0] = null;
            TraceTimer.traceEnd("compound");
        }
    }

    private <T extends TransactionClass> void run(Set<T> todo, Queued<T> queued, Set<State> states) {
        if (universeTransaction().getConfig().isTraceMutable()) {
            System.err.println("DCLARE: " + indent("    ") + mutable() + " " + (queued.actions() ? "actions" : "children") + " " + todo.toString().substring(3));
        }
        if (universeTransaction().getConfig().isRunSequential()) {
            runSequential(todo);
        } else {
            try {
                runParallel(todo);
            } catch (NotMergeableException nme) {
                runSequential(todo);
            }
        }
        if (!universeTransaction().isKilled()) {
            this.states = states.add(state[0]);
            if (hasQueued(state[0], mutable(), Priority.urgent)) {
                urgent = true;
                move(mutable(), Priority.urgent, Priority.scheduled);
            } else if (parent() == null || (!parent().urgent && this.states != states)) {
                urgent = false;
                move(mutable(), Priority.forward, Priority.scheduled);
            } else if (hasQueued(state[0], mutable(), Priority.forward)) {
                state[0] = state[0].set(parent().mutable(), Priority.forward.children, Set::add, mutable());
            }
        }
    }

    private <T extends TransactionClass> void runParallel(Set<T> todo) {
        state[0] = state[0].get(() -> merge(state[0], todo.random().reduce(state, //
                (s, t) -> new State[]{t.run(s[0], this)}, //
                (a, b) -> {
                    State[] r = new State[a.length + b.length];
                    System.arraycopy(a, 0, r, 0, a.length);
                    System.arraycopy(b, 0, r, a.length, b.length);
                    return r;
                })));
    }

    private <T extends TransactionClass> void runSequential(Set<T> todo) {
        for (TransactionClass t : todo.random()) {
            State result = t.run(state[0], this);
            if (universeTransaction().isKilled()) {
                return;
            }
            state[0] = result;
        }
    }

    private State merge(State base, State[] branches) {
        if (universeTransaction().isKilled()) {
            return base;
        } else {
            TraceTimer.traceBegin("merge");
            triggeredActions.init(Map.of());
            triggeredChildren[0].init(Set.of());
            triggeredChildren[1].init(Set.of());
            triggeredChildren[2].init(Set.of());
            try {
                State state = base.merge(this, branches, branches.length);
                state = trigger(state, triggeredActions.result(), Priority.forward);
                state = triggerMutables(state, triggeredChildren[0].result(), Priority.urgent);
                state = triggerMutables(state, triggeredChildren[1].result(), Priority.forward);
                state = triggerMutables(state, triggeredChildren[2].result(), Priority.backward);
                return state;
            } finally {
                triggeredActions.clear();
                triggeredChildren[0].clear();
                triggeredChildren[1].clear();
                triggeredChildren[2].clear();
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
    public void handleChange(Object o, DefaultMap<Setable, Object> ps, Entry<Setable, Object> p, DefaultMap<Setable, Object>[] psbs) {
        if (p.getKey() instanceof Observers) {
            Observers<?, ?> os = (Observers) p.getKey();
            DefaultMap<Observer, Set<Mutable>> observers = (DefaultMap) p.getValue();
            os.observed().checkTooManyObservers(universeTransaction(), o, observers);
            observers = observers.removeAll(State.get(ps, os), Set::removeAll);
            if (!observers.isEmpty()) {
                Observed<?, ?> observedProp = os.observed();
                Object baseValue = State.get(ps, observedProp);
                for (DefaultMap<Setable, Object> psb : psbs) {
                    Object branchValue = State.get(psb, observedProp);
                    if (!Objects.equals(branchValue, baseValue)) {
                        Map<Observer, Set<Mutable>> addedObservers = observers.removeAll(State.get(psb, os), Set::removeAll).//
                                toMap(e -> Entry.of(e.getKey(), e.getValue().map(m -> m.dResolve((Mutable) o)).toSet()));
                        triggeredActions.change(ts -> ts.addAll(addedObservers, Set::addAll));
                    }
                }
            }
        } else if (p.getKey() instanceof Queued) {
            Queued<Mutable> ds = (Queued) p.getKey();
            if (ds.children() && ds.priority() != Priority.scheduled) {
                Set<Mutable> depth = (Set<Mutable>) p.getValue();
                depth = depth.removeAll(State.get(ps, ds));
                if (!depth.isEmpty()) {
                    Mutable baseParent = State.getA(ps, Mutable.D_PARENT_CONTAINING);
                    for (DefaultMap<Setable, Object> psb : psbs) {
                        Mutable branchParent = State.getA(psb, Mutable.D_PARENT_CONTAINING);
                        if (!Objects.equals(branchParent, baseParent)) {
                            Set<Mutable> addedDepth = depth.removeAll(State.get(psb, ds));
                            if (!addedDepth.isEmpty()) {
                                triggeredChildren[ds.priority().nr].change(ts -> ts.addAll(addedDepth));
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked", "SameParameterValue"})
    private State trigger(State state, Map<Observer, Set<Mutable>> leafs, Priority priority) {
        for (Entry<Observer, Set<Mutable>> e : leafs) {
            for (Mutable m : e.getValue()) {
                state = trigger(state, m, e.getKey(), priority);
            }
        }
        return state;
    }

    private State triggerMutables(State state, Set<Mutable> mutables, Priority priority) {
        for (Mutable mutable : mutables) {
            state = trigger(state, mutable, null, priority);
        }
        return state;
    }

    protected <O extends Mutable> State trigger(State state, O target, Action<O> action, Priority priority) {
        Mutable object = target;
        if (action != null) {
            state = state.set(object, priority.actions, Set::add, action);
        }
        Mutable parent = state.getA(object, Mutable.D_PARENT_CONTAINING);
        while (parent != null && !mutable().equals(object)) {
            state = state.set(parent, priority.children, Set::add, object);
            object = parent;
            parent = state.getA(object, Mutable.D_PARENT_CONTAINING);
        }
        return state;
    }

    protected State lastState() {
        return state[0];
    }

}
