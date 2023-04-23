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

import java.util.Objects;

import org.modelingvalue.collections.QualifiedSet;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.Construction.Reason;

public class MatchInfo {

    private final Newable                         newable;
    private final Construction                    initialConstruction;
    private final boolean                         removed;
    private final Object                          identity;

    private QualifiedSet<Direction, Construction> allDerivations;

    @SuppressWarnings("rawtypes")
    public static MatchInfo of(Newable newable, ObserverTransaction otx, Mutable object, Observed observed) {
        return new MatchInfo(newable, otx, object, observed);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private MatchInfo(Newable newable, ObserverTransaction otx, Mutable object, Observed observed) {
        this.newable = newable;
        ConstantState constants = otx.universeTransaction().tmpConstants();
        removed = otx.midStartState().get(newable, Mutable.D_PARENT_CONTAINING) == null && //
                otx.preOuterStartState().get(newable, Mutable.D_PARENT_CONTAINING) != null;
        initialConstruction = newable.dInitialConstruction();
        allDerivations = newable.dAllDerivations();
        identity = constants.get(otx, newable, Newable.D_IDENTITY, n -> {
            if (!removed && (isDirect() || isDerived())) {
                State state = otx.current();
                if (observed.containment()) {
                    state = state.set(newable, Mutable.D_PARENT_CONTAINING, Pair.of(object, observed));
                }
                return state.deriveIdentity(() -> n.dIdentity(), otx.depth(), otx.mutable(), constants);
            } else {
                return null;
            }
        });
    }

    public boolean mustReplace(MatchInfo replaced) {
        return canBeReplacing() && replaced.canBeReplaced() && Objects.equals(identity(), replaced.identity()) && //
                !replaced.allDerivations.anyMatch(c -> (isDerived() && initialConstruction.reason().equals(c.reason())) || c.hasSource(newable));
    }

    protected void setAllDerivations(MatchInfo other) {
        allDerivations = other.allDerivations;
    }

    private boolean canBeReplacing() {
        return !removed && (isDirect() || isDerived());
    }

    private boolean canBeReplaced() {
        return !removed && isDerived();
    }

    private boolean isDerived() {
        return initialConstruction.isDerived();
    }

    private boolean isDirect() {
        return initialConstruction.isDirect();
    }

    public QualifiedSet<Direction, Construction> allDerivations() {
        return allDerivations;
    }

    @SuppressWarnings("unchecked")
    public <S> Comparable<S> sortKey() {
        return newable.dSortKey();
    }

    public Newable newable() {
        return newable;
    }

    @SuppressWarnings("unchecked")
    public Object identity() {
        return identity;
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
        return newable.toString() + directions().toString().substring(3) + identity();
    }

    private Set<Direction> directions() {
        Set<Direction> result = allDerivations.map(Construction::reason).map(Reason::direction).toSet();
        result = result.add(initialConstruction.reason().direction());
        return result;
    }

}
