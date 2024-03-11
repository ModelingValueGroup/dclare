//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2024 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
//                                                                                                                       ~
//  Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in       ~
//  compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0   ~
//  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on  ~
//  an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the   ~
//  specific language governing permissions and limitations under the License.                                           ~
//                                                                                                                       ~
//  Maintainers:                                                                                                         ~
//      Wim Bast, Tom Brus                                                                                               ~
//                                                                                                                       ~
//  Contributors:                                                                                                        ~
//      Ronald Krijgsheld ✝, Arjan Kok, Carel Bast                                                                       ~
// --------------------------------------------------------------------------------------------------------------------- ~
//  In Memory of Ronald Krijgsheld, 1972 - 2023                                                                          ~
//      Ronald was suddenly and unexpectedly taken from us. He was not only our long-term colleague and team member      ~
//      but also our friend. "He will live on in many of the lines of code you see below."                               ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.dclare;

import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Concurrent;
import org.modelingvalue.collections.util.Mergeable;
import org.modelingvalue.collections.util.NotMergeableException;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.collections.util.StringUtil;
import org.modelingvalue.collections.util.TraceTimer;
import org.modelingvalue.dclare.ex.TransactionException;

public class ActionTransaction extends LeafTransaction implements StateMergeHandler {
    private final CurrentState          currentState  = new CurrentState();
    private final ConstantChangeHandler changeHandler = new ConstantChangeHandler() {
                                                          @Override
                                                          public <O, T> void changed(O object, Setable<O, T> setable, T preValue, T rawPreValue, T postValue) {
                                                              ActionTransaction.this.set(object, setable, preValue, postValue);
                                                          }

                                                          @Override
                                                          public State state() {
                                                              return ActionTransaction.this.state();
                                                          }

                                                          @Override
                                                          public <O, T> T set(O object, Setable<O, T> property, T post) {
                                                              return ActionTransaction.this.set(object, property, post);
                                                          }

                                                          @Override
                                                          public <O extends Mutable> void trigger(O mutable, Action<O> action, Priority priority) {
                                                              ActionTransaction.this.trigger(mutable, action, priority);
                                                          }
                                                      };
    @SuppressWarnings("unchecked")
    private final Supplier<Object>      supplier      = () -> {
                                                          ((Action<Mutable>) action()).run(mutable());
                                                          return null;
                                                      };

    private State                       preState;
    private State                       postState;

    protected ActionTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    public final Action<?> action() {
        return (Action<?>) cls();
    }

    @SuppressWarnings("unchecked")
    protected void run(State pre, UniverseTransaction universeTransaction) {
        if (push()) {
            ((Action<Mutable>) action()).run(mutable());
        } else {
            state().derive(supplier, pullConstantState(), changeHandler);
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected final State run(State pre) {
        TraceTimer.traceBegin(traceId());
        preState = pre;
        currentState.init(pre);
        try {
            LeafTransaction.getContext().run(this, () -> {
                run(pre, universeTransaction());
                if (universeTransaction().getConfig().isTraceActions()) {
                    postState = currentState.merge();
                    Map<Object, Map<Setable, Pair<Object, Object>>> diff = preState.diff(postState, o -> o instanceof Mutable, s -> s instanceof Observed && !s.isPlumbing()).asMap(e -> e);
                    if (!diff.isEmpty()) {
                        runNonObserving(() -> System.err.println(DclareTrace.getLineStart("DCLARE", this) + mutable() + "." + action() + " (" + postState.shortDiffString(diff, mutable()) + ")"));
                    }
                } else {
                    postState = currentState.result();
                }
                if (postState != preState) {
                    bumpTotalChanges();
                }
            });
            return postState;
        } catch (Throwable t) {
            universeTransaction().handleException(new TransactionException(mutable(), new TransactionException(action(), t)));
            return pre;
        } finally {
            currentState.clear();
            preState = null;
            postState = null;
            TraceTimer.traceEnd(traceId());
        }
    }

    protected void bumpTotalChanges() {
        universeTransaction().stats().bumpAndGetTotalChanges();
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
        return currentState.get();
    }

    protected State merge() {
        return currentState.merge();
    }

    protected void rollback() {
        currentState.clear();
        currentState.init(preState);
    }

    @Override
    public <O, T, E> T set(O object, Setable<O, T> property, BiFunction<T, E, T> function, E element) {
        return set(object, property, function.apply(currentState.get().get(object, property), element));
    }

    @Override
    public <O, T> T set(O object, Setable<O, T> property, UnaryOperator<T> oper) {
        return set(object, property, oper.apply(currentState.get().get(object, property)));
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
        if (currentState.change(s -> s.set(object, property, (br, po) -> {
            if (Objects.equals(br, po)) {
                po = br;
            } else if (!property.doNotMerge() && !Objects.equals(br, pre)) {
                if (pre instanceof Mergeable) {
                    po = (T) ((Mergeable) pre).merge(br, po);
                } else if (br != null && po != null) {
                    handleMergeConflict(object, property, pre, br, po);
                }
            }
            return po;
        }, post, oldNew))) {
            changed(object, property, pre, oldNew[0], oldNew[1]);
        }
    }

    protected void setState(State state) {
        currentState.set(state);
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantSuppression"})
    @Override
    public <O, T> void changed(O object, Setable<O, T> setable, T preValue, T rawPreValue, T postValue) {
        super.changed(object, setable, preValue, rawPreValue, postValue);
        if (push()) {
            if (setable.preserved()) {
                setChanged(object, setable, postValue);
            }
            if (setable instanceof Observed && !Objects.equals(preValue, postValue)) {
                trigger(object, (Observed<O, T>) setable);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <O, T> void trigger(O object, Observed<O, T> observed) {
        Mutable source = mutable();
        for (Entry<Observer, Set<Mutable>> e : get(object, observed.observers())) {
            Observer observer = e.getKey();
            for (Mutable m : e.getValue()) {
                Mutable target = m.dResolve((Mutable) object);
                if (!action().equals(observer) || !source.equals(target)) {
                    Priority priority = observer.fixpointGroup() == fixpointGroup() ? observer.initPriority() : Priority.five;
                    trigger(target, observer, priority);
                    if (universeTransaction().getConfig().isTraceMutable()) {
                        runNonObserving(() -> System.err.println(DclareTrace.getLineStart("DCLARE", this) + mutable() + "." + action() + " (TRIGGER " + target + "." + observer + ")"));
                    }
                }
            }
        }
    }

    private <O, T> void setChanged(O object, Setable<O, T> setable, T postValue) {
        TransactionId txid = action().preserved() ? universeTransaction().setPreserved(object, setable, postValue, action()) : current().transactionId();
        for (Mutable changed = (Mutable) object; changed != null && !(changed instanceof Universe); changed = dParent(changed)) {
            TransactionId old = current(changed, Mutable.D_CHANGE_ID);
            if (old != null && txid.number() <= old.number()) {
                break;
            } else {
                set(changed, Mutable.D_CHANGE_ID, txid);
            }
        }
    }

    private final class CurrentState extends Concurrent<State> {
        @Override
        protected State merge(State base, State[] branches, int length) {
            return base.merge(ActionTransaction.this, branches, length);
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void handleMergeConflict(Object object, Setable property, Object pre, Object... branches) {
        if (property != Mutable.D_CHANGE_ID) {
            throw new NotMergeableException(object + "." + property + "= " + pre + " -> " + StringUtil.toString(branches));
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void handleChange(Object object, Setable setable, DefaultMap<Setable, Object> baseValues, DefaultMap<Setable, Object>[] bracnhesValues, DefaultMap<Setable, Object> resultValues, State base) {
    }

    @Override
    protected Mutable dParent(Mutable object) {
        return current().getA(object, Mutable.D_PARENT_CONTAINING);
    }

    @Override
    public ActionInstance actionInstance() {
        return ActionInstance.of(mutable(), action());
    }

    @Override
    public Direction direction() {
        return action().direction();
    }

    @Override
    protected String getCurrentTypeForTrace() {
        return "AC";
    }

    @SuppressWarnings({"unchecked", "unused"})
    public void retrigger(Priority prio) {
        trigger(mutable(), (Action<Mutable>) action(), prio);
    }
}
