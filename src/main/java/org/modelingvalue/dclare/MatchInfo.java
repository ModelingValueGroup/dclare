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

import java.util.Objects;

import org.modelingvalue.collections.Set;
import org.modelingvalue.dclare.Construction.Reason;

public class MatchInfo {

    private final Newable             newable;
    private final ObserverTransaction tx;

    private Object                    identity;
    private Boolean                   isCarvedInStone;
    private Set<Direction>            directions;

    public static MatchInfo of(Newable newable, ObserverTransaction tx) {
        return new MatchInfo(newable, tx);
    }

    private MatchInfo(Newable newable, ObserverTransaction tx) {
        this.newable = newable;
        this.tx = tx;
    }

    public boolean mustBeTheSame(MatchInfo other) {
        return newable().equals(other.newable().dReplacing()) || other.newable().equals(newable().dReplacing()) || //
                (newable().dNewableType().equals(other.newable().dNewableType()) && //
                        other.directions().noneMatch(directions()::contains) && //
                        Objects.equals(identity(), other.identity()));
    }

    @SuppressWarnings("rawtypes")
    public Comparable sortKey() {
        return newable.dSortKey();
    }

    public void mergeIn(MatchInfo from) {
        directions = directions().addAll(from.directions());
    }

    public Set<Direction> directions() {
        if (directions == null) {
            directions = newable.dConstructions().map(Construction::reason).map(Reason::direction).toSet();
        }
        return directions;
    }

    public Newable newable() {
        return newable;
    }

    public Object identity() {
        if (identity == null) {
            identity = tx.state().derive(() -> newable.dMatchingIdentity(), tx, tx.universeTransaction().tmpConstants());
            if (identity == null) {
                identity = ConstantState.NULL;
            }
        }
        return identity == ConstantState.NULL ? null : identity;
    }

    public boolean isCarvedInStone() {
        if (isCarvedInStone == null) {
            isCarvedInStone = newable.dDirectConstruction() != null;
        }
        return isCarvedInStone;
    }

    @Override
    public int hashCode() {
        return newable.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MatchInfo && newable.equals(((MatchInfo) other).newable);
    }

    @Override
    public String toString() {
        return newable.toString();
    }

    public String asString() {
        return newable() + ":" + directions().toString().substring(3);
    }

}
