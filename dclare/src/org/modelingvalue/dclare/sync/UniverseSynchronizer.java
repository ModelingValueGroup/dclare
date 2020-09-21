package org.modelingvalue.dclare.sync;

import java.util.function.*;

import org.modelingvalue.dclare.*;

@SuppressWarnings({"rawtypes", "unused"})
public interface UniverseSynchronizer<M extends Mutable, TRANSFER> {

    TRANSFER serializeDelta(State pre, State post);

    void deserializeDelta(TRANSFER delta);

    /////////////////////////////////
    Predicate<Mutable> mutableFilter();

    Predicate<Setable> setableFilter();

    /////////////////////////////////
    TRANSFER serializeClass(MutableClass clazz);

    TRANSFER serializeSetable(Setable<M, ?> setable);

    TRANSFER serializeMutable(M mutable);

    <V> TRANSFER serializeValue(Setable<M, V> setable, V value);

    /////////////////////////////////
    MutableClass deserializeClass(TRANSFER s);

    Setable<M, ?> deserializeSetable(MutableClass clazz, TRANSFER s);

    M deserializeMutable(MutableClass clazz, TRANSFER s);

    <V> V deserializeValue(Setable<M, V> setable, TRANSFER s);
    /////////////////////////////////
}