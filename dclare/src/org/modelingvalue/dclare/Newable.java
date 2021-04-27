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

import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.QualifiedSet;
import org.modelingvalue.collections.Set;
import org.modelingvalue.dclare.Construction.Reason;

public interface Newable extends Mutable {

    Observed<Newable, QualifiedSet<Direction, Construction>> D_DERIVED_CONSTRUCTIONS = Observed.of("D_DERIVED_CONSTRUCTIONS", QualifiedSet.<Direction, Construction> of(c -> c.reason().direction()), (t, o, b, a) -> {
                                                                                         Setable.<QualifiedSet<Direction, Construction>, Construction> diff(b, a,                                                                                                                                //
                                                                                                 add -> {
                                                                                                     if (ObserverTransaction.TRACE_MATCHING) {
                                                                                                         t.runNonObserving(() -> System.err.println("MATCH:  " + t.parent().indent("    ") + t.direction() + "::" + add.object() + "." + add.observer() + " (" + add.reason() + "=>" + o + ")"));
                                                                                                     }
                                                                                                     add.observer().constructed().set(add.object(), Map::put, Entry.of(add.reason(), o));
                                                                                                 },                                                                                                                                                                                              //
                                                                                                 rem -> {
                                                                                                     if (ObserverTransaction.TRACE_MATCHING) {
                                                                                                         t.runNonObserving(() -> System.err.println("MATCH:  " + t.parent().indent("    ") + t.direction() + "::" + rem.object() + "." + rem.observer() + " (" + rem.reason() + "=<" + o + ")"));
                                                                                                     }
                                                                                                     rem.observer().constructed().set(rem.object(), (m, e) -> e.getValue().equals(m.get(e.getKey())) ? m.removeKey(e.getKey()) : m, Entry.of(rem.reason(), o));
                                                                                                 });
                                                                                     }, SetableModifier.doNotCheckConsistency);

    Observed<Newable, Set<Newable>>                          D_SOURCES               = Observed.of("D_SOURCES", Set.of(), SetableModifier.doNotCheckConsistency);

    Observer<Newable>                                        D_SOURCES_RULE          = Observer.of(D_SOURCES, n -> {
                                                                                         Construction direct = n.dDirectConstruction();
                                                                                         if (direct != null) {
                                                                                             D_SOURCES.set(n, Set.of(n));
                                                                                         } else {
                                                                                             Set<Newable> set = n.dDerivedConstructions().flatMap(c -> c.derivers()).flatMap(d -> Newable.D_SOURCES.get(d)).toSet();
                                                                                             D_SOURCES.set(n, set.exclude(p -> set.anyMatch(c -> c.dHasAncestor(p))).toSet());
                                                                                         }
                                                                                     });

    Constant<Newable, Construction>                          D_DIRECT_CONSTRUCTION   = Constant.of("D_DIRECT_CONSTRUCTION", null, SetableModifier.doNotCheckConsistency);

    @SuppressWarnings("rawtypes")
    Observed<Newable, Boolean>                               D_OBSOLETE              = Observed.of("D_OBSOLETE", Boolean.FALSE, (t, o, b, a) -> {
                                                                                         if (a) {
                                                                                             for (Observer r : D_OBSERVERS.get(o)) {
                                                                                                 for (Entry<Reason, Newable> e : r.constructed().get(o)) {
                                                                                                     Newable.D_OBSOLETE.set(e.getValue(), Boolean.TRUE);
                                                                                                 }
                                                                                                 r.constructed().setDefault(o);
                                                                                             }
                                                                                         }
                                                                                     }, SetableModifier.doNotCheckConsistency);

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

    default QualifiedSet<Direction, Construction> dDerivedConstructions() {
        return D_DERIVED_CONSTRUCTIONS.get(this);
    }

    default Construction dDirectConstruction() {
        return D_DIRECT_CONSTRUCTION.isSet(this) ? D_DIRECT_CONSTRUCTION.get(this) : null;
    }

    default QualifiedSet<Direction, Construction> dConstructions() {
        QualifiedSet<Direction, Construction> derived = dDerivedConstructions();
        Construction direct = dDirectConstruction();
        return direct != null ? derived.add(direct) : derived;
    }

    default Set<Newable> dNonDerivedSources() {
        return D_SOURCES.get(this);
    }

    @Override
    default boolean dIsObsolete() {
        return D_OBSOLETE.get(this);
    }

    @Override
    default void dActivate() {
        Mutable.super.dActivate();
        D_SOURCES_RULE.trigger(this);
    }

    @Override
    default void dDeactivate() {
        Mutable.super.dDeactivate();
        D_SOURCES_RULE.deObserve(this);
    }

    @Override
    @SuppressWarnings("rawtypes")
    default boolean dToBeCleared(Setable setable) {
        return Mutable.super.dToBeCleared(setable) && setable != D_DERIVED_CONSTRUCTIONS;
    }

}
