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

import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import org.modelingvalue.collections.*;
import org.modelingvalue.collections.util.*;
import org.modelingvalue.dclare.Construction.Reason;
import org.modelingvalue.dclare.ex.TransactionException;

public class ActionTransaction extends LeafTransaction implements StateMergeHandler {
    private final CurrentState currentSate = new CurrentState();
    private State              preState;

    protected ActionTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    public final Action<?> action() {
        return (Action<?>) cls();
    }

    @SuppressWarnings("unchecked")
    protected void run(State pre, UniverseTransaction universeTransaction) {
        ((Action<Mutable>) action()).run(mutable());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    protected final State run(State state) {
        TraceTimer.traceBegin(traceId());
        preState = state;
        currentSate.init(state);
        try {
            LeafTransaction.getContext().run(this, () -> run(state, universeTransaction()));
            State result = currentSate.result();
            if (universeTransaction().getConfig().isTraceActions()) {
                Map<Object, Map<Setable, Pair<Object, Object>>> diff = preState.diff(result, o -> o instanceof Mutable, s -> s instanceof Observed && !s.isPlumbing()).toMap(e -> e);
                if (!diff.isEmpty()) {
                    preState.run(() -> {
                        System.err.println("DCLARE: " + parent().indent("    ") + ((Action<Mutable>) action()).direction(mutable()) + "::" + mutable() + "." + action() + " (" + result.shortDiffString(diff, mutable()) + ")");
                    });
                }
            }
            return result;
        } catch (Throwable t) {
            universeTransaction().handleException(new TransactionException(mutable(), new TransactionException(action(), t)));
            return state;
        } finally {
            currentSate.clear();
            preState = null;
            TraceTimer.traceEnd(traceId());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    protected <O> void trigger(Observed<O, ?> observed, O o) {
        if (o instanceof Mutable && !observed.isPlumbing()) {
            setChanged((Mutable) o);
        }
        Mutable source = mutable();
        for (Entry<Observer, Set<Mutable>> e : get(o, observed.observers())) {
            Observer observer = e.getKey();
            for (Mutable m : e.getValue()) {
                //noinspection ConstantConditions
                Mutable target = m.dResolve((Mutable) o);
                if (!cls().equals(observer) || !source.equals(target)) {
                    trigger(target, observer, Priority.forward);
                    // runNonObserving(() -> System.err.println("!!! TRIGGER !!!! " + target + "." + observer));
                    if (!observed.isPlumbing()) {
                        for (Entry<Reason, Newable> rn : get(target, observer.constructed())) {
                            set(rn.getValue(), Newable.D_SUPER_POSITION, Set::add, rn.getKey().direction());
                        }
                    }
                }
            }
        }
    }

    protected String traceId() {
        return "leaf";
    }

    @Override
    public State state() {
        if (preState == null) {
            throw new ConcurrentModificationException();
        }
        return preState;
    }

    @Override
    public State current() {
        return currentSate.get();
    }

    public State merge() {
        return currentSate.merge();
    }

    @Override
    public <O, T, E> T set(O object, Setable<O, T> property, BiFunction<T, E, T> function, E element) {
        return set(object, property, function.apply(currentSate.get().get(object, property), element));
    }

    @Override
    public <O, T> T set(O object, Setable<O, T> property, UnaryOperator<T> oper) {
        return set(object, property, oper.apply(currentSate.get().get(object, property)));
    }

    @Override
    public <O, T> T set(O object, Setable<O, T> property, T post) {
        property.init(post);
        T pre = state().get(object, property);
        set(object, property, pre, post);
        return pre;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected <T, O> void set(O object, Setable<O, T> property, T pre, T post) {
        T[] oldNew = (T[]) new Object[2];
        if (currentSate.change(s -> s.set(object, property, (br, po) -> {
            if (Objects.equals(br, po)) {
                po = br;
            } else if (!Objects.equals(br, pre)) {
                if (pre instanceof Mergeable) {
                    po = (T) ((Mergeable) pre).merge(br, po);
                } else if (br != null && po != null) {
                    handleMergeConflict(object, property, pre, br, po);
                }
            }
            return po;
        }, post, oldNew))) {
            changed(object, property, oldNew[0], oldNew[1]);
        }
    }

    private final class CurrentState extends Concurrent<State> {
        @Override
        protected State merge(State base, State[] branches, int length) {
            return base.merge(ActionTransaction.this, branches, length);
        }
    }

    protected void setChanged(Mutable changed) {
        Universe universe = universeTransaction().universe();
        byte cnr = get(universe, Mutable.D_CHANGE_NR);
        while (changed != null && changed != universe && set(changed, Mutable.D_CHANGE_NR, cnr) != cnr) {
            changed = dParent(changed);
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void handleMergeConflict(Object object, Setable property, Object pre, Object... branches) {
        if (property != Mutable.D_CHANGE_NR) {
            throw new NotMergeableException(object + "." + property + "= " + pre + " -> " + StringUtil.toString(branches));
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void handleChange(Object o, DefaultMap<Setable, Object> ps, Entry<Setable, Object> p, DefaultMap<Setable, Object>[] psbs) {
    }

    @Override
    protected Mutable dParent(Mutable object) {
        return currentSate.get().getA(object, Mutable.D_PARENT_CONTAINING);
    }

    @Override
    public ActionInstance actionInstance() {
        return ActionInstance.of(mutable(), action());
    }

}
