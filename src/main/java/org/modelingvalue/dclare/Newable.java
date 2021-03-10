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
import org.modelingvalue.collections.Set;

public interface Newable extends Mutable {

    Observed<Newable, Set<Construction>> D_DERIVED_CONSTRUCTIONS = Observed.of("D_DERIVED_CONSTRUCTIONS", Set.of(), (t, o, b, a) ->
                                                                     Setable.<Set<Construction>, Construction> diff(b, a,                                                                                                                             //
                                                                             add -> {
                                                                                 if (ObserverTransaction.TRACE_MATCHING) {
                                                                                     t.runNonObserving(() -> System.err.println("MATCH:  " + t.parent().indent("    ") + add.object() + "." + add.observer() + " (" + add.reason() + "=>" + o + ")"));
                                                                                 }
                                                                                 add.observer().constructed().set(add.object(), Map::put, Entry.of(add.reason(), o));
                                                                             },                                                                                                                                                                       //
                                                                             rem -> {
                                                                                 if (ObserverTransaction.TRACE_MATCHING) {
                                                                                     t.runNonObserving(() -> System.err.println("MATCH:  " + t.parent().indent("    ") + rem.object() + "." + rem.observer() + " (" + rem.reason() + "=<" + o + ")"));
                                                                                 }
                                                                                 rem.observer().constructed().set(rem.object(), (m, e) -> e.getValue().equals(m.get(e.getKey())) ? m.removeKey(e.getKey()) : m, Entry.of(rem.reason(), o));
                                                                             }), SetableModifier.doNotCheckConsistency);

    Constant<Newable, Construction>      D_DIRECT_CONSTRUCTION   = Constant.of("D_DIRECT_CONSTRUCTION", null, SetableModifier.doNotCheckConsistency);

    Observed<Newable, Newable>           D_MATCHED               = Observed.of("D_MATCHED", null, SetableModifier.doNotCheckConsistency);

    Observer<Newable>                    D_CONSTRUCTIONS_RULE    = Observer.of("D_CONSTRUCTIONS_RULE", n -> D_DERIVED_CONSTRUCTIONS.set(n, cs -> {
                                                                     for (Construction c : cs) {
                                                                         if (c.object().dIsObsolete()) {
                                                                             cs = cs.remove(c);
                                                                         }
                                                                     }
                                                                     return cs;
                                                                 }));

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

    default Set<Construction> dDerivedConstructions() {
        return D_DERIVED_CONSTRUCTIONS.get(this);
    }

    default Construction dDirectConstruction() {
        return D_DIRECT_CONSTRUCTION.isSet(this) ? D_DIRECT_CONSTRUCTION.get(this) : null;
    }

    default Set<Construction> dConstructions() {
        Set<Construction> derived = dDerivedConstructions();
        Construction direct = dDirectConstruction();
        return direct != null ? derived.add(direct) : derived;
    }

    default Newable dMatched() {
        return D_MATCHED.get(this);
    }

    @Override
    default boolean dIsObsolete() {
        return dMatched() != null;
    }

    @Override
    default void dActivate() {
        Mutable.super.dActivate();
        D_CONSTRUCTIONS_RULE.trigger(this);
    }

    @Override
    default void dDeactivate() {
        Mutable.super.dDeactivate();
        D_CONSTRUCTIONS_RULE.deObserve(this);
    }

    @Override
    @SuppressWarnings("rawtypes")
    default boolean dToBeCleared(Setable setable) {
        return Mutable.super.dToBeCleared(setable); // && setable != D_DERIVED_CONSTRUCTIONS;
    }

}
