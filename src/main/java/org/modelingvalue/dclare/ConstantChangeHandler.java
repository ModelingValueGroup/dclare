package org.modelingvalue.dclare;

public interface ConstantChangeHandler {

    <O, T> void changed(O object, Setable<O, T> setable, T preValue, T rawPreValue, T postValue);

    <O, T> T set(O object, Setable<O, T> property, T post);

    State state();

}
