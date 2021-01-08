//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2021 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

    @Override
    default boolean isIdentified() {
        return dConstructions().anyMatch(Construction::isNotObserved);
    }

}
