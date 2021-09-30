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

import static org.modelingvalue.dclare.CoreSetableModifier.plumbing;

import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.QualifiedSet;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.Construction.Reason;

public interface Newable extends Mutable {

    Observed<Newable, Construction>                          D_DIRECT_CONSTRUCTION   = Observed.of("D_DIRECT_CONSTRUCTION", null, plumbing);
    Observed<Newable, QualifiedSet<Direction, Construction>> D_DERIVED_CONSTRUCTIONS = Observed.of("D_DERIVED_CONSTRUCTIONS", QualifiedSet.of(c -> c.reason().direction()), (t, o, b, a) -> {
                                                                                         Setable.<QualifiedSet<Direction, Construction>, Construction> diff(b, a,                                                                                                  //
                                                                                                 add -> add.observer().constructed().set(add.object(), Map::put, Entry.of(add.reason(), o)),                                                                       //
                                                                                                 rem -> rem.observer().constructed().set(rem.object(), (m, e) -> e.getValue().equals(m.get(e.getKey())) ? m.removeKey(e.getKey()) : m, Entry.of(rem.reason(), o)));
                                                                                         if (a.isEmpty() && o.dDirectConstruction() == null) {
                                                                                             for (Observer<?> obs : Mutable.D_OBSERVERS.get(o)) {
                                                                                                 obs.constructed().setDefault(o);
                                                                                             }
                                                                                         }
                                                                                     }, plumbing);
    Observed<Newable, Set<Newable>>                          D_SOURCES               = Observed.of("D_SOURCES", Set.of(), plumbing);
    Observer<Newable>                                        D_SOURCES_RULE          = Observer.of(D_SOURCES, n -> {
                                                                                         Set<Newable> sources = n.dDerivedConstructions().flatMap(Construction::derivers).toSet();
                                                                                         Pair<Mutable, Setable<Mutable, ?>> pair = D_PARENT_CONTAINING.get(n);
                                                                                         if (pair.a() instanceof Newable && n.equals(pair.b().get(pair.a()))) {
                                                                                             sources = sources.add((Newable) pair.a());
                                                                                         }
                                                                                         sources = sources.flatMap(s -> s.dDirectConstruction() != null ? Set.of(s) : D_SOURCES.get(s).remove(n)).toSet();
                                                                                         D_SOURCES.set(n, sources);
                                                                                     });
    Observed<Newable, Set<Direction>>                        D_DIRECTIONS            = Observed.of("D_DIRECTIONS", Set.of(), plumbing);
    Observer<Newable>                                        D_DIRECTIONS_RULE       = NonCheckingObserver.of(D_DIRECTIONS, n -> {
                                                                                         Construction direct = n.dDirectConstruction();
                                                                                         if (direct != null) {
                                                                                             D_DIRECTIONS.set(n, Set.of());
                                                                                         } else {
                                                                                             QualifiedSet<Direction, Construction> cons = n.dDerivedConstructions();
                                                                                             D_DIRECTIONS.set(n, cons.toKeys().toSet().addAll(cons.flatMap(Construction::derivers).flatMap(Newable::dDirections)));
                                                                                         }
                                                                                     });
    Observed<Newable, Set<Direction>>                        D_SUPER_POSITION        = Observed.of("D_SUPER_POSITION", Set.of(), plumbing);
    Observer<Newable>                                        D_SUPER_POSITION_RULE   = NonCheckingObserver.of("D_SUPER_POSITION_RULE", n -> {
                                                                                         D_SUPER_POSITION.set(n, Set::retainAll, D_DERIVED_CONSTRUCTIONS.get(n).map(Construction::reason).map(Reason::direction).toSet());
                                                                                     });

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
        return D_DERIVED_CONSTRUCTIONS.get(this);
    }

    default QualifiedSet<Direction, Construction> dConstructions() {
        QualifiedSet<Direction, Construction> derived = dDerivedConstructions();
        Construction direct = dDirectConstruction();
        return direct != null ? derived.add(direct) : derived;
    }

    default Set<Newable> dSources() {
        return D_SOURCES.get(this);
    }

    default Set<Direction> dDirections() {
        return D_DIRECTIONS.get(this);
    }

    @Override
    default void dActivate() {
        Mutable.super.dActivate();
        D_SOURCES_RULE.trigger(this);
        D_DIRECTIONS_RULE.trigger(this);
        D_SUPER_POSITION_RULE.trigger(this);
    }

    @Override
    default void dDeactivate() {
        Mutable.super.dDeactivate();
        D_SOURCES_RULE.deObserve(this);
        D_DIRECTIONS_RULE.deObserve(this);
        D_SUPER_POSITION_RULE.deObserve(this);
    }

    @Override
    @SuppressWarnings("rawtypes")
    default boolean dToBeCleared(Setable setable) {
        return Mutable.super.dToBeCleared(setable) && setable != D_DERIVED_CONSTRUCTIONS;
    }

    @Override
    @SuppressWarnings("rawtypes")
    default void dHandleRemoved(Mutable parent) {
        Mutable.super.dHandleRemoved(parent);

    }

}
