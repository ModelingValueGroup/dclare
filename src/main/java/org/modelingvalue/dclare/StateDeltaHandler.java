package org.modelingvalue.dclare;

import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Set;

@SuppressWarnings("rawtypes")
@FunctionalInterface
public interface StateDeltaHandler {

    void handleDelta(State pre, State post, boolean inSync, DefaultMap<Object, Set<Setable>> setted);

}
