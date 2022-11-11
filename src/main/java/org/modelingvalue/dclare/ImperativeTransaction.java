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

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.NamedIdentity;
import org.modelingvalue.dclare.Priority.Queued;

public class ImperativeTransaction extends LeafTransaction {

    @SuppressWarnings("rawtypes")
    protected static final DefaultMap<Object, Set<Setable>> SETTED_MAP = DefaultMap.of(k -> Set.of());

    @SuppressWarnings("rawtypes")
    public static ImperativeTransaction of(Imperative cls, State init, UniverseTransaction universeTransaction, Consumer<Runnable> scheduler, StateDeltaHandler diffHandler, boolean keepTransaction) {
        return new ImperativeTransaction(cls, init, universeTransaction, scheduler, diffHandler, keepTransaction);
    }

    private final static Setable<ImperativeTransaction, Long> CHANGE_NR = Setable.of("$CHANGE_NR", 0L);

    private final Consumer<Runnable>                          scheduler;
    @SuppressWarnings("rawtypes")
    private final StateDeltaHandler                           diffHandler;
    private final NamedIdentity                               actionId;
    private final Direction                                   direction;
    private final MutableState                                state;

    private State                                             pre;
    private boolean                                           active;
    private boolean                                           commiting;
    @SuppressWarnings("rawtypes")
    private DefaultMap<Object, Set<Setable>>                  setted;
    @SuppressWarnings("rawtypes")
    private DefaultMap<Object, Set<Setable>>                  allSetted;

    @SuppressWarnings("rawtypes")
    protected ImperativeTransaction(Imperative cls, State init, UniverseTransaction universeTransaction, Consumer<Runnable> scheduler, StateDeltaHandler diffHandler, boolean keepTransaction) {
        super(universeTransaction);
        this.pre = init;
        this.state = universeTransaction.createMutableState(init);
        this.setted = SETTED_MAP;
        this.allSetted = SETTED_MAP;
        this.diffHandler = diffHandler;
        this.direction = Direction.of(cls.id());
        this.actionId = NamedIdentity.of(this, cls.id().toString());
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

    public final Imperative imperative() {
        return (Imperative) leaf();
    }

    public void schedule(Runnable action) {
        scheduler.accept(action);
    }

    @Override
    public State state() {
        return state.state();
    }

    public MutableState mutableState() {
        return state;
    }

    public final boolean commit(State dclare, boolean timeTraveling) {
        commiting = true;
        boolean insync = setted.isEmpty() && dclare.get(this, CHANGE_NR).equals(state.get(this, CHANGE_NR));
        if (pre != dclare) {
            dclare2imper(dclare, timeTraveling, insync);
        }
        if (!setted.isEmpty()) {
            insync = false;
            imper2dclare();
        } else if (insync && active) {
            active = false;
            universeTransaction().removeActive(this);
        }
        pre = state();
        commiting = false;
        return insync;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void dclare2imper(State dclare, boolean timeTraveling, boolean insync) {
        DefaultMap<Object, Set<Setable>> finalAllSetted = allSetted;
        State imper = state();
        if (insync) {
            allSetted = SETTED_MAP;
        } else {
            for (Entry<Object, Set<Setable>> e : finalAllSetted) {
                Object object = e.getKey();
                DefaultMap<Setable, Object> dclareProps = dclare.getProperties(object);
                DefaultMap<Setable, Object> imperProps = imper.getProperties(object);
                for (Setable setable : e.getValue()) {
                    dclareProps = State.setProperties(dclareProps, setable, imperProps.get(setable));
                }
                dclare = dclare.set(object, dclareProps);
            }
        }
        imper = state.setState(dclare);
        diffHandler.handleDelta(imper, dclare, insync, finalAllSetted);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void imper2dclare() {
        State imper = state();
        DefaultMap<Object, Set<Setable>> finalSetted = setted;
        setted = SETTED_MAP;
        universeTransaction().put(Action.of(actionId, u -> {
            try {
                finalSetted.forEachOrdered(e -> {
                    DefaultMap<Setable, Object> props = imper.getProperties(e.getKey());
                    for (Setable p : e.getValue()) {
                        if (p instanceof Queued && !((Queued) p).children()) {
                            for (Action action : (Set<Action>) props.get(p)) {
                                action.trigger((Mutable) e.getKey(), ((Queued) p).priority());
                            }
                        } else {
                            p.set(e.getKey(), props.get(p));
                        }
                    }
                });
            } catch (Throwable t) {
                CHANGE_NR.set(ImperativeTransaction.this, imper.get(ImperativeTransaction.this, CHANGE_NR));
                universeTransaction().handleException(t);
            }
        }, direction, LeafModifier.preserved));
    }

    @Override
    protected <O extends Mutable> void trigger(O target, Action<O> action, Priority priority) {
        set(target, priority.actions, Set::add, action);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <O, T> T set(O object, Setable<O, T> property, T post) {
        T[] oldNew = (T[]) new Object[1];
        state.set(object, property, post, oldNew);
        change(object, property, oldNew[0], post);
        return oldNew[0];
    }

    @SuppressWarnings("unchecked")
    @Override
    public <O, T, E> T set(O object, Setable<O, T> property, BiFunction<T, E, T> function, E element) {
        T[] oldNew = (T[]) new Object[2];
        state.set(object, property, function, element, oldNew);
        change(object, property, oldNew[0], oldNew[1]);
        return oldNew[0];
    }

    @SuppressWarnings("unchecked")
    @Override
    public <O, T> T set(O object, Setable<O, T> property, UnaryOperator<T> oper) {
        T[] oldNew = (T[]) new Object[2];
        state.set(object, property, oper, oldNew);
        change(object, property, oldNew[0], oldNew[1]);
        return oldNew[0];
    }

    @SuppressWarnings("rawtypes")
    private <O, T> void change(O object, Setable<O, T> property, T preValue, T postValue) {
        if (!Objects.equals(preValue, postValue)) {
            boolean first = setted.isEmpty();
            Set<Setable> set = Set.of(property);
            allSetted = allSetted.add(object, set, Set::addAll);
            setted = setted.add(object, set, Set::addAll);
            if (first) {
                set(this, CHANGE_NR, (BiFunction<Long, Long, Long>) Long::sum, 1l);
                if (!commiting) {
                    if (!active) {
                        active = true;
                        universeTransaction().addActive(this);
                    }
                    universeTransaction().commit();
                }
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

    @Override
    public Direction direction() {
        return direction;
    }

    @Override
    protected String getCurrentTypeForTrace() {
        return "IM";
    }
}
