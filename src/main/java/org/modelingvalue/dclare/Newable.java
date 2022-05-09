//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2022 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import static org.modelingvalue.dclare.CoreSetableModifier.doNotMerge;
import static org.modelingvalue.dclare.CoreSetableModifier.plumbing;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.QualifiedSet;
import org.modelingvalue.collections.Set;

public interface Newable extends Mutable {

    Observed<Newable, Construction>                          D_DIRECT_CONSTRUCTION   = Observed.of("D_DIRECT_CONSTRUCTION", null, plumbing);
    @SuppressWarnings({"unchecked", "rawtypes"})
    Observed<Newable, QualifiedSet<Direction, Construction>> D_DERIVED_CONSTRUCTIONS = Observed.of("D_DERIVED_CONSTRUCTIONS", QualifiedSet.of(c -> c.reason().direction()), (t, o, b, a) -> {
                                                                                         Setable.<QualifiedSet<Direction, Construction>, Construction> diff(b, a,                                                       //
                                                                                                 add -> {
                                                                                                     add.observer().constructed().set(add.object(), Map::put, Entry.of(add.reason(), o));
                                                                                                 },                                                                                                                     //
                                                                                                 rem -> {
                                                                                                     if (o.equals(rem.observer().constructed().current(rem.object()).get(rem.reason()))) {
                                                                                                         rem.observer().constructed().set(rem.object(), Map::removeKey, rem.reason());
                                                                                                         for (Observer obs : Mutable.D_OBSERVERS.get(o).filter(ob -> ob.direction().equals(rem.reason().direction()))) {
                                                                                                             obs.deObserve(o);
                                                                                                         }
                                                                                                     }
                                                                                                 });
                                                                                     }, plumbing, doNotMerge);

    Setable<Newable, Newable>                                D_REPLACING             = Constant.of("D_REPLACING", null, plumbing);

    @SuppressWarnings("rawtypes")
    Object dIdentity();

    default Object dMatchingIdentity() {
        try {
            return dIdentity();
        } catch (Throwable e) {
            return null;
        }
    }

    Object dNewableType();

    @SuppressWarnings("rawtypes")
    Comparable dSortKey();

    default Construction dDirectConstruction() {
        return D_DIRECT_CONSTRUCTION.current(this);
    }

    default QualifiedSet<Direction, Construction> dDerivedConstructions() {
        return D_DERIVED_CONSTRUCTIONS.current(this);
    }

    default Newable dReplacing() {
        return D_REPLACING.current(this);
    }

    default QualifiedSet<Direction, Construction> dConstructions() {
        QualifiedSet<Direction, Construction> derived = dDerivedConstructions();
        Construction direct = dDirectConstruction();
        return direct != null ? derived.add(direct) : derived;
    }

    @Override
    default void dActivate() {
        Mutable.super.dActivate();
    }

    @Override
    default void dDeactivate() {
        Mutable.super.dDeactivate();
    }

    default Collection<Direction> dDirections() {
        Construction direct = dDirectConstruction();
        Collection<Direction> derived = dDerivedConstructions().toKeys();
        return Collection.concat(derived, direct != null ? Set.of(direct.reason().direction()) : Set.of());
    }

}
