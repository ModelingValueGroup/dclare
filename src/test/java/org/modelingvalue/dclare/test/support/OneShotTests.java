package org.modelingvalue.dclare.test.support;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.modelingvalue.collections.Collection;
import org.modelingvalue.dclare.Mutable;
import org.modelingvalue.dclare.MutableClass;
import org.modelingvalue.dclare.Observer;
import org.modelingvalue.dclare.OneShot;
import org.modelingvalue.dclare.Setable;
import org.modelingvalue.dclare.Universe;

public class OneShotTests {
    private static class MyMutableClass implements MutableClass {
        @Override
        public Collection<? extends Observer<?>> dObservers() {
            return null;
        }

        @Override
        public Collection<? extends Setable<? extends Mutable, ?>> dSetables() {
            return null;
        }
    }

    private static class MyUniverse implements Universe {
        @Override
        public MutableClass dClass() {
            return null;
        }
    }

    @Disabled
    @Test
    public void simple() {
        String              jsonIn  = "{}";
        OneShot<MyUniverse> oneShot = new OneShot<>(new MyUniverse(), jsonIn);
        String              jsonOut = oneShot.fromJsonToJson();

        Assertions.assertEquals("xxx", jsonOut);
    }
}
