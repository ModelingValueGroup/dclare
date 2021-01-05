package org.modelingvalue.dclare;

import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Mergeable;

public interface Newable extends Mutable, Mergeable<Newable> {

    Newable                              MERGER        = new Newable() {
                                                           @Override
                                                           public MutableClass dClass() {
                                                               throw new UnsupportedOperationException();
                                                           }

                                                           @Override
                                                           public Object dIdentity() {
                                                               throw new UnsupportedOperationException();
                                                           }

                                                           @Override
                                                           public Object dNewableType() {
                                                               throw new UnsupportedOperationException();
                                                           }

                                                           @Override
                                                           public Comparable<?> dSortKey() {
                                                               throw new UnsupportedOperationException();
                                                           }
                                                       };

    Observed<Newable, Set<Construction>> CONSTRUCTIONS = Observed.of("D_CONSTRUCTIONS", Set.of());

    Object dIdentity();

    Object dNewableType();

    Comparable<?> dSortKey();

    default Set<Construction> dConstructions() {
        return CONSTRUCTIONS.current(this);
    }

    @Override
    default Newable merge(Newable[] branches, int length) {
        return branches[length - 1];
    }

    @Override
    default Newable getMerger() {
        return MERGER;
    }

}
