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

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.collections.util.TriConsumer;

public class ImperativeTransaction extends LeafTransaction {

    @SuppressWarnings("rawtypes")
    private static final DefaultMap<Object, Set<Setable>> SETTED_MAP = DefaultMap.of(k -> Set.of());

    public static ImperativeTransaction of(Leaf cls, State init, UniverseTransaction universeTransaction, Consumer<Runnable> scheduler, Consumer<State> firstHandler, TriConsumer<State, State, Boolean> diffHandler, boolean keepTransaction) {
        return new ImperativeTransaction(cls, init, universeTransaction, scheduler, firstHandler, diffHandler, keepTransaction);
    }

    private final static Setable<ImperativeTransaction, Long> CHANGE_NR    = Setable.of("$CHANGE_NR", 0L);

    private final Consumer<Runnable>                          scheduler;
    private final TriConsumer<State, State, Boolean>          diffHandler;
    private final Consumer<State>                             firstHandler;
    private final Pair<ImperativeTransaction, String>         actionId     = Pair.of(this, "$toDClare");

    private State                                             pre;
    private State                                             state;
    @SuppressWarnings("rawtypes")
    private DefaultMap<Object, Set<Setable>>                  setted;
    @SuppressWarnings("rawtypes")
    private DefaultMap<Object, Set<Setable>>                  allSetted;
    private Long                                              lastChangeNr = CHANGE_NR.getDefault();

    protected ImperativeTransaction(Leaf cls, State init, UniverseTransaction universeTransaction, Consumer<Runnable> scheduler, Consumer<State> firstHandler, TriConsumer<State, State, Boolean> diffHandler, boolean keepTransaction) {
        super(universeTransaction);
        this.pre = init;
        this.state = init;
        this.setted = SETTED_MAP;
        this.allSetted = SETTED_MAP;
        this.firstHandler = firstHandler;
        this.diffHandler = diffHandler;
        super.start(cls, universeTransaction);
        this.scheduler = keepTransaction ? r -> scheduler.accept(() -> {
            LeafTransaction.getContext().setOnThread(this);
            try {
                r.run();
            } catch (Throwable t) {
                universeTransaction.handleException(t);
            }
        }) : r -> scheduler.accept(() -> {
            try {
                LeafTransaction.getContext().run(this, r);
            } catch (Throwable t) {
                universeTransaction.handleException(t);
            }
        });
    }

    @Override
    public void stop() {
        if (isOpen()) {
            super.stop();
        }
    }

    public void schedule(Runnable action) {
        scheduler.accept(action);
    }

    public void commit(State post, boolean timeTraveling) {
        extern2intern();
        intern2extern(post, timeTraveling);
    }

    @Override
    public State state() {
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void extern2intern() {
        if (pre != state) {
            CHANGE_NR.set(this, (BiFunction<Long, Long, Long>) Long::sum, 1l);
            State finalState = state;
            DefaultMap<Object, Set<Setable>> finalSetted = setted;
            pre = state;
            setted = SETTED_MAP;
            universeTransaction().put(actionId, () -> {
                try {
                    finalSetted.forEachOrdered(e -> {
                        DefaultMap<Setable, Object> props = finalState.getProperties(e.getKey());
                        for (Setable p : e.getValue()) {
                            p.set(e.getKey(), props.get(p));
                        }
                    });
                } catch (Throwable t) {
                    CHANGE_NR.set(ImperativeTransaction.this, finalState.get(ImperativeTransaction.this, CHANGE_NR));
                    universeTransaction().handleException(t);
                }
            });
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void intern2extern(State post, boolean timeTraveling) {
        if (pre != post) {
            State finalState = state;
            Long postChangeNr = post.get(this, CHANGE_NR);
            Long stateChangeNr = finalState.get(this, CHANGE_NR);
            boolean last = postChangeNr.equals(stateChangeNr) && !postChangeNr.equals(lastChangeNr);
            if (last) {
                lastChangeNr = postChangeNr;
                allSetted = SETTED_MAP;
            } else {
                for (Entry<Object, Set<Setable>> e : allSetted) {
                    Object object = e.getKey();
                    DefaultMap<Setable, Object> postProps = post.getProperties(object);
                    DefaultMap<Setable, Object> stateProps = finalState.getProperties(object);
                    for (Setable setable : e.getValue()) {
                        postProps = State.setProperties(postProps, setable, stateProps.get(setable));
                    }
                    post = post.set(object, postProps);
                }
            }
            state = post;
            if (!timeTraveling) {
                pre = state;
            }
            diffHandler.accept(finalState, post, last);
            if (timeTraveling) {
                pre = state;
            }
        }
    }

    public State waitForEnd() {
        universeTransaction().waitForEnd();
        BlockingQueue<Boolean> waitQueue = new LinkedBlockingQueue<>(1);
        scheduler.accept(() -> {
            try {
                waitQueue.put(Boolean.TRUE);
            } catch (InterruptedException e) {
                throw new Error(e);
            }
        });
        try {
            waitQueue.take();
        } catch (InterruptedException e) {
            throw new Error(e);
        }
        return state;
    }

    @Override
    public boolean isChanged() {
        return pre != state;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <O, T> T set(O object, Setable<O, T> property, T post) {
        T[] old = (T[]) new Object[1];
        boolean first = pre == state;
        state = state.set(object, property, post, old);
        changed(object, property, old[0], post, first);
        return old[0];
    }

    @SuppressWarnings("unchecked")
    @Override
    public <O, T, E> T set(O object, Setable<O, T> property, BiFunction<T, E, T> function, E element) {
        T[] oldNew = (T[]) new Object[2];
        boolean first = pre == state;
        state = state.set(object, property, function, element, oldNew);
        changed(object, property, oldNew[0], oldNew[1], first);
        return oldNew[0];
    }

    @SuppressWarnings("unchecked")
    @Override
    public <O, T> T set(O object, Setable<O, T> property, UnaryOperator<T> oper) {
        T[] oldNew = (T[]) new Object[2];
        boolean first = pre == state;
        state = state.set(object, property, oper, oldNew);
        changed(object, property, oldNew[0], oldNew[1], first);
        return oldNew[0];
    }

    @SuppressWarnings("rawtypes")
    private <O, T> void changed(O object, Setable<O, T> property, T preValue, T postValue, boolean first) {
        if (!Objects.equals(preValue, postValue)) {
            Set<Setable> set = Set.of(property);
            allSetted = allSetted.add(object, set, Set::addAll);
            setted = setted.add(object, set, Set::addAll);
            if (first) {
                if (firstHandler != null) {
                    firstHandler.accept(pre);
                }
                universeTransaction().dummy();
            }
        }
    }

    @Override
    protected State run(State state) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ActionInstance actionInstance() {
        throw new UnsupportedOperationException();
    }

    public static State clean(State state) {
        for (ImperativeTransaction itx : state.getObjects(ImperativeTransaction.class)) {
            state = state.set(itx, CHANGE_NR, CHANGE_NR.getDefault());
        }
        return state;
    }
}