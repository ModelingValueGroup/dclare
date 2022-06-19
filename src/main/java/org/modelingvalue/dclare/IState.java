package org.modelingvalue.dclare;

public interface IState {

    <O, T> T get(O object, Getable<O, T> property);

    State state();

}
