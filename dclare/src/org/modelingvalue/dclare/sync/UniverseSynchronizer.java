package org.modelingvalue.dclare.sync;

import java.util.function.*;

import org.modelingvalue.dclare.*;

@SuppressWarnings({"rawtypes", "unused"})
public interface UniverseSynchronizer<M extends Mutable> {

    String serializeDelta(State pre, State post);

    void deserializeDelta(String delta);

    /////////////////////////////////
    Predicate<Mutable> mutableFilter();

    Predicate<Setable> setableFilter();

    /////////////////////////////////
    String serializeClass(MutableClass clazz);

    String serializeSetable(Setable<M, ?> setable);

    String serializeMutable(M mutable);

    <V> String serializeValue(Setable<M, V> setable, V value);

    /////////////////////////////////
    MutableClass deserializeClass(String s);

    Setable<M, ?> deserializeSetable(MutableClass clazz, String s);

    M deserializeMutable(MutableClass clazz, String s);

    <V> V deserializeValue(Setable<M, V> setable, String s);
    /////////////////////////////////
}