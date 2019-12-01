package org.modelingvalue.dclare.test;

import org.modelingvalue.dclare.*;

import java.util.function.*;

public class DUniverse extends DObject implements Universe {

    public static DUniverse of(Object id, DClass dClass) {
        return new DUniverse(id, u -> {
        }, dClass);
    }

    public static DUniverse of(Object id, Consumer<Universe> init, DClass dClass) {
        return new DUniverse(id, init, dClass);
    }

    private final Consumer<Universe> init;

    protected DUniverse(Object id, Consumer<Universe> init, DClass dClass) {
        super(id, dClass);
        this.init = init;
    }

    @Override
    public void init() {
        Universe.super.init();
        init.accept(this);
    }

}
