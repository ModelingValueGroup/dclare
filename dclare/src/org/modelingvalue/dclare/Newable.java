package org.modelingvalue.dclare;

import org.modelingvalue.collections.Set;

public interface Newable extends Mutable {

    Observed<Newable, Set<Construction>> CONSTRUCTIONS = Observed.of("D_CONSTRUCTIONS", Set.of());

    Object dIdentity();

    Object dNewableType();

    default Set<Construction> dConstructions() {
        return CONSTRUCTIONS.current(this);
    }

}
