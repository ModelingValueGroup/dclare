package org.modelingvalue.dclare.test.support;

import java.util.function.*;

import org.modelingvalue.collections.*;
import org.modelingvalue.collections.util.*;
import org.modelingvalue.dclare.*;

public class TestObserved<O, T> extends Observed<O, T> {
    private static Map<Object, TestObserved<?, ?>> staticObservedMap = Map.of();

    @SuppressWarnings("unchecked")
    public static <C, V> TestObserved<C, V> existing(Object id) {
        return (TestObserved<C, V>) staticObservedMap.get(id);
    }

    public static <C, V> Observed<C, V> of(Object id, V def) {
        return new TestObserved<>(id, false, def, false, null, null, null, true);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, boolean containment) {
        return new TestObserved<>(id, false, def, containment, null, null, null, true);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, QuadConsumer<LeafTransaction, C, V, V> changed) {
        return new TestObserved<>(id, false, def, false, null, null, changed, true);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, boolean containment, boolean checkConsistency) {
        return new TestObserved<>(id, false, def, containment, null, null, null, checkConsistency);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, Supplier<Setable<?, ?>> opposite) {
        return new TestObserved<>(id, false, def, false, opposite, null, null, true);
    }

    public static <C, V> Observed<C, V> of(Object id, V def, Supplier<Setable<?, ?>> opposite, Supplier<Setable<C, Set<?>>> scope, boolean checkConsistency) {
        return new TestObserved<>(id, false, def, false, opposite, scope, null, checkConsistency);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def) {
        return new TestObserved<>(id, mandatory, def, false, null, null, null, true);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def, boolean containment) {
        return new TestObserved<>(id, mandatory, def, containment, null, null, null, true);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def, boolean containment, boolean checkConsistency) {
        return new TestObserved<>(id, mandatory, def, containment, null, null, null, checkConsistency);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def, QuadConsumer<LeafTransaction, C, V, V> changed) {
        return new TestObserved<>(id, mandatory, def, false, null, null, changed, true);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def, Supplier<Setable<?, ?>> opposite) {
        return new TestObserved<>(id, mandatory, def, false, opposite, null, null, true);
    }

    public static <C, V> Observed<C, V> of(Object id, boolean mandatory, V def, Supplier<Setable<?, ?>> opposite, Supplier<Setable<C, Set<?>>> scope, boolean checkConsistency) {
        return new TestObserved<>(id, mandatory, def, false, opposite, scope, null, checkConsistency);
    }

    protected TestObserved(Object id, boolean mandatory, T def, boolean containment, Supplier<Setable<?, ?>> opposite, Supplier<Setable<O, Set<?>>> scope, QuadConsumer<LeafTransaction, O, T, T> changed, boolean checkConsistency) {
        super(id, mandatory, def, containment, opposite, scope, changed, checkConsistency);
        synchronized (TestObserved.class) {
            staticObservedMap = staticObservedMap.put(id, this);
        }
    }
}
