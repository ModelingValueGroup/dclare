//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2023 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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
        return new DirectionImpl(id, opposites);
    }

    static Direction of(Object id, Supplier<Set<Direction>> oppositeSupplier) {
        return new DirectionImpl(id, oppositeSupplier);
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
    };

    Set<Direction> opposites();

    static final class DirectionImpl implements Direction {

        private final Object                   id;
        private final Supplier<Set<Direction>> oppositeSupplier;

        private Set<Direction>                 opposites = null;

        private DirectionImpl(Object id, Direction... opposites) {
            this(id, () -> Collection.of(opposites).asSet());
        }

        private DirectionImpl(Object id, Supplier<Set<Direction>> oppositeSupplier) {
            this.id = id;
            this.oppositeSupplier = oppositeSupplier;
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

    }

}
