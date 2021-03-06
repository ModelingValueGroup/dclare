//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2020 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import static org.modelingvalue.dclare.State.ALL_OBJECTS;
import static org.modelingvalue.dclare.State.ALL_SETTABLES;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.collections.util.TriConsumer;

public class ImperativeTransaction extends LeafTransaction {

    public static ImperativeTransaction of(Leaf cls, State init, UniverseTransaction universeTransaction, Consumer<Runnable> scheduler, Consumer<State> firstHandler, TriConsumer<State, State, Boolean> diffHandler, boolean keepTransaction) {
        return new ImperativeTransaction(cls, init, universeTransaction, scheduler, firstHandler, diffHandler, keepTransaction);
    }

    private final static Setable<ImperativeTransaction, Long> CHANGE_NR = Setable.of("$CHANGE_NR", 0L);

    private final Consumer<Runnable>                          scheduler;
    //
    @SuppressWarnings("rawtypes")
    private Set<Pair<Object, Setable>>                        setted;
    private State                                             pre;
    private State                                             state;
    private final TriConsumer<State, State, Boolean>          diffHandler;
    private final Consumer<State>                             firstHandler;

    protected ImperativeTransaction(Leaf cls, State init, UniverseTransaction universeTransaction, Consumer<Runnable> scheduler, Consumer<State> firstHandler, TriConsumer<State, State, Boolean> diffHandler, boolean keepTransaction) {
        super(universeTransaction);
        this.pre = init;
        this.state = init;
        this.setted = Set.of();
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
            State finalPre = pre;
            CHANGE_NR.set(this, (BiFunction<Long, Integer, Long>) Long::sum, 1);
            State finalState = state;
            pre = finalState;
            universeTransaction().put(Pair.of(this, "$toDClare"), () -> {
                try {
                    finalPre.diff(finalState, ALL_OBJECTS, ALL_SETTABLES).forEachOrdered(s -> {
                        Object o = s.getKey();
                        for (Entry<Setable, Pair<Object, Object>> d : s.getValue()) {
                            d.getKey().set(o, d.getValue().b());
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
            boolean last = post.get(this, CHANGE_NR).equals(finalState.get(this, CHANGE_NR));
            if (last) {
                setted = Set.of();
            } else {
                for (Pair<Object, Setable> slot : setted) {
                    post = post.set(slot.a(), slot.b(), finalState.get(slot.a(), slot.b()));
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

    private <O, T> void changed(O object, Setable<O, T> property, T preValue, T postValue, boolean first) {
        if (!Objects.equals(preValue, postValue)) {
            setted = setted.add(Pair.of(object, property));
            if (first) {
                if (firstHandler != null) {
                    firstHandler.accept(pre);
                }
                universeTransaction().dummy();
            }
            changed(object, property, preValue, postValue);
        }
    }

    @Override
    protected void setChanged(Mutable changed) {
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
