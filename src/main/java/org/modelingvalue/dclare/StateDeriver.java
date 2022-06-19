package org.modelingvalue.dclare;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public abstract class StateDeriver implements IState {

    private final AtomicReference<State> state;

    public StateDeriver(State state) {
        this.state = new AtomicReference<>(state);
    }

    @Override
    public <O, T> T get(O object, Getable<O, T> property) {
        if (property instanceof Setable) {
            T val = state.get().get(object, property);
            if (Objects.equals(val, property.getDefault())) {
                T derived = derive(object, (Setable<O, T>) property);
                if (!Objects.equals(val, derived)) {
                    state.updateAndGet(s -> s.set(object, (Setable<O, T>) property, derived));
                    return derived;
                }
            }
            return val;
        } else {
            return property.get(object);
        }
    }

    @Override
    public State state() {
        return state.get();
    }

    protected abstract <O, T> T derive(O object, Setable<O, T> property);

}
