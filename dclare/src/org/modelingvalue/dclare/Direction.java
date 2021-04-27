package org.modelingvalue.dclare;

import org.modelingvalue.collections.util.Internable;

public interface Direction extends Internable {

    static Direction of(Object id) {
        return new DirectionImpl(id);
    }

    static final class DirectionImpl implements Direction {
        private final Object id;

        private DirectionImpl(Object id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Direction.DirectionImpl && ((Direction.DirectionImpl) obj).id.equals(id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return id.toString();
        }
    }

}
