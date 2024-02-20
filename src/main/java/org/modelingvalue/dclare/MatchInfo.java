//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2024 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
//                                                                                                                       ~
//  Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in       ~
//  compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0   ~
//  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on  ~
//  an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the   ~
//  specific language governing permissions and limitations under the License.                                           ~
//                                                                                                                       ~
//  Maintainers:                                                                                                         ~
//      Wim Bast, Tom Brus                                                                                               ~
//                                                                                                                       ~
//  Contributors:                                                                                                        ~
//      Ronald Krijgsheld ✝, Arjan Kok, Carel Bast                                                                       ~
// --------------------------------------------------------------------------------------------------------------------- ~
//  In Memory of Ronald Krijgsheld, 1972 - 2023                                                                          ~
//      Ronald was suddenly and unexpectedly taken from us. He was not only our long-term colleague and team member      ~
//      but also our friend. "He will live on in many of the lines of code you see below."                               ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

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
    private final boolean                         containment;
    private final boolean                         many;
    private final boolean                         match;

    private QualifiedSet<Direction, Construction> allDerivations;

    @SuppressWarnings("rawtypes")
    public static MatchInfo of(Newable newable, ObserverTransaction otx, Mutable object, Observed observed, boolean many) {
        return new MatchInfo(newable, otx, object, observed, many);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private MatchInfo(Newable newable, ObserverTransaction otx, Mutable object, Observed observed, boolean many) {
        this.newable = newable;
        this.containment = observed.containment();
        this.match = observed.match();
        this.many = many;
        ConstantState constants = otx.universeTransaction().tmpConstants();
        removed = otx.startState(Priority.three).get(newable, Mutable.D_PARENT_CONTAINING) == null && //
                otx.preStartState(Priority.OUTER).getRaw(newable, Mutable.D_PARENT_CONTAINING) != null;
        initialConstruction = newable.dInitialConstruction();
        allDerivations = newable.dAllDerivations();
        identity = constants.get(otx, newable, Newable.D_IDENTITY, n -> {
            if (!removed && (isDirect() || isDerived())) {
                State state = otx.current();
                if (containment) {
                    state = state.set(newable, Mutable.D_PARENT_CONTAINING, Pair.of(object, observed));
                }
                return state.deriveIdentity(() -> n.dIdentity(), otx.depth(), otx.mutable(), constants);
            } else {
                return null;
            }
        });
    }

    public boolean mustReplace(MatchInfo replaced) {
        if (!many || containment || match) {
            return canBeReplacing() && replaced.canBeReplaced() && Objects.equals(identity(), replaced.identity()) && //
                    !replaced.allDerivations.anyMatch(c -> (isDerived() && initialConstruction.reason().equals(c.reason())) || c.hasSource(newable));
        } else {
            return newable.equals(replaced.newable().dReplacing());
        }
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
        Set<Direction> result = allDerivations.map(Construction::reason).map(Reason::direction).asSet();
        result = result.add(initialConstruction.reason().direction());
        return result;
    }

}
