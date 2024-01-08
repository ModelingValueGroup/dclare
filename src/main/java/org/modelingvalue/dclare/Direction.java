//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2024 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
//                                                                                                                     ~
// Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in      ~
// compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0  ~
// Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on ~
// an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the  ~
// specific language governing permissions and limitations under the License.                                          ~
//                                                                                                                     ~
// Maintainers:                                                                                                        ~
//     Wim Bast, Tom Brus, Ronald Krijgsheld                                                                           ~
// Contributors:                                                                                                       ~
//     Arjan Kok, Carel Bast                                                                                           ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.dclare;

import java.util.function.Supplier;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Internable;

public interface Direction extends LeafModifier<Direction>, SetableModifier<Direction>, Internable {

    static Direction of(Object id, Direction... opposites) {
        return new DirectionImpl(id, false, FixpointGroup.DEFAULT, opposites);
    }

    static Direction of(Object id, Supplier<Set<Direction>> oppositeSupplier) {
        return new DirectionImpl(id, false, FixpointGroup.DEFAULT, oppositeSupplier);
    }

    static Direction of(Object id, boolean lazy, Direction... opposites) {
        return new DirectionImpl(id, lazy, FixpointGroup.DEFAULT, opposites);
    }

    static Direction of(Object id, boolean lazy, Supplier<Set<Direction>> oppositeSupplier) {
        return new DirectionImpl(id, lazy, FixpointGroup.DEFAULT, oppositeSupplier);
    }

    static Direction of(Object id, FixpointGroup fixpointGroup, Direction... opposites) {
        return new DirectionImpl(id, false, fixpointGroup, opposites);
    }

    static Direction of(Object id, FixpointGroup fixpointGroup, Supplier<Set<Direction>> oppositeSupplier) {
        return new DirectionImpl(id, false, fixpointGroup, oppositeSupplier);
    }

    static Direction of(Object id, boolean lazy, FixpointGroup fixpointGroup, Direction... opposites) {
        return new DirectionImpl(id, lazy, fixpointGroup, opposites);
    }

    static Direction of(Object id, boolean lazy, FixpointGroup fixpointGroup, Supplier<Set<Direction>> oppositeSupplier) {
        return new DirectionImpl(id, lazy, fixpointGroup, oppositeSupplier);
    }

    Direction DEFAULT = new Direction() {
        @Override
        public String toString() {
            return "<DEF>";
        }

        @Override
        public Set<Direction> opposites() {
            return Set.of();
        }

        @Override
        public boolean isLazy() {
            return false;
        }

        @Override
        public FixpointGroup fixpointGroup() {
            return FixpointGroup.DEFAULT;
        }
    };

    Set<Direction> opposites();

    boolean isLazy();

    FixpointGroup fixpointGroup();

    static final class DirectionImpl implements Direction {

        private final Object                   id;
        private final boolean                  lazy;
        private final Supplier<Set<Direction>> oppositeSupplier;
        private final FixpointGroup            fixpointGroup;

        private Set<Direction>                 opposites = null;

        private DirectionImpl(Object id, boolean lazy, FixpointGroup fixpointGroup, Direction... opposites) {
            this(id, lazy, fixpointGroup, () -> Collection.of(opposites).asSet());
        }

        private DirectionImpl(Object id, boolean lazy, FixpointGroup fixpointGroup, Supplier<Set<Direction>> oppositeSupplier) {
            this.id = id;
            this.lazy = lazy;
            this.oppositeSupplier = oppositeSupplier;
            this.fixpointGroup = fixpointGroup;
        }

        @Override
        public boolean isLazy() {
            return lazy;
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

        @Override
        public Set<Direction> opposites() {
            if (opposites == null) {
                synchronized (DEFAULT) {
                    opposites = Set.of();
                    for (Direction oppopsite : oppositeSupplier.get()) {
                        opposites = opposites.add(oppopsite);
                        ((DirectionImpl) oppopsite).opposites = ((DirectionImpl) oppopsite).opposites().add(this);
                    }
                }
            }
            return opposites;
        }

        @Override
        public final FixpointGroup fixpointGroup() {
            return fixpointGroup;
        }

    }

}
