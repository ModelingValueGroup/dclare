package org.modelingvalue.dclare;

import java.util.concurrent.atomic.AtomicReference;

public class MutableState implements IState {

    private final AtomicReference<State> atomic;

    public MutableState(State state) {
        this.atomic = new AtomicReference<>(state);
    }

    @Override
    public <O, T> T get(O object, Getable<O, T> property) {
        return state().get(object, property);
    }

    public State state() {
        return atomic.get();
    }

    public <O, T> State set(O object, Setable<O, T> property, T value) {
        return atomic.updateAndGet(s -> {
            State r = s.set(object, property, value);
            if (r != s && object instanceof Mutable && !property.isPlumbing()) {
                r = setChanged(r, (Mutable) object);
            }
            return r;
        });
    }

    private State setChanged(State state, Mutable changed) {
        TransactionId txid = state.get(state.universeTransaction().universe(), Mutable.D_CHANGE_ID);
        while (changed != null && !(changed instanceof Universe) && state.get(changed, Mutable.D_CHANGE_ID) != txid) {
            state = state.set(changed, Mutable.D_CHANGE_ID, txid);
            changed = state.getA(changed, Mutable.D_PARENT_CONTAINING);
        }
        return state;
    }

}
