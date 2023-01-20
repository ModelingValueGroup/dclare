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

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.QualifiedSet;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.Construction.Reason;

public class MatchInfo {

    private final Newable                         newable;
    private final ObserverTransaction             otx;
    private final Mutable                         object;
    @SuppressWarnings("rawtypes")
    private final Observed                        observed;

    private Object                                identity;
    private Set<Construction>                     directConstructions;
    private Newable                               replacing;
    private Set<Direction>                        directions;
    private QualifiedSet<Direction, Construction> derivedConstructions;

    @SuppressWarnings("rawtypes")
    public static MatchInfo of(Newable newable, ObserverTransaction tx, Mutable object, Observed observed) {
        return new MatchInfo(newable, tx, object, observed);
    }

    @SuppressWarnings("rawtypes")
    private MatchInfo(Newable newable, ObserverTransaction otx, Mutable object, Observed observed) {
        this.newable = newable;
        this.otx = otx;
        this.object = object;
        this.observed = observed;
    }

    public boolean mustReplace(MatchInfo replaced) {
        if (!newable.equals(replaced.newable) && haveEqualType(replaced)) {
            if (newable.equals(replaced.replacing())) {
                return true;
            } else if (replaced.replacing() != null || directions().anyMatch(replaced.directions()::contains)) {
                return false;
            } else if (Objects.equals(identity(), replaced.identity())) {
                return true;
            } else if (otx.universeTransaction().getConfig().isTraceMatching()) {
                otx.runNonObserving(() -> System.err.println(DclareTrace.getLineStart("MATCH", otx) + otx.mutable() + "." + otx.observer() + " (" + this + "|" + identity() + "!=" + replaced + "|" + replaced.identity() + ")"));
            }
        }
        return false;
    }

    public void replace(MatchInfo replaced) {
        replaced.replacing = newable;
        directions = directions().addAll(replaced.directions());
        replaced.directions = directions.clear();
        derivedConstructions = derivedConstructions().addAll(replaced.derivedConstructions());
        replaced.derivedConstructions = derivedConstructions.clear();
    }

    public boolean canBeReplacing() {
        return replacing() == null;
    }

    public boolean canBeReplaced() {
        return !isDirect();
    }

    public boolean isDerived() {
        return !derivedConstructions().isEmpty();
    }

    public boolean isDirect() {
        return !directConstructions().isEmpty();
    }

    public QualifiedSet<Direction, Construction> derivedConstructions() {
        if (derivedConstructions == null) {
            derivedConstructions = newable.dDerivedConstructions();
        }
        return derivedConstructions;
    }

    public boolean haveEqualType(MatchInfo other) {
        return newable.dNewableType().equals(other.newable.dNewableType());
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
        if (identity == null) {
            identity = otx.current().deriveIdentity(() -> newable.dIdentity(), otx.depth(), //
                    observed.containment() ? newable : null, observed.containment() ? Pair.of(object, observed) : null, //
                    otx.universeTransaction().tmpConstants());
            if (identity == null) {
                identity = ConstantState.NULL;
            }
        }
        return identity == ConstantState.NULL ? null : identity;
    }

    public Set<Construction> directConstructions() {
        if (directConstructions == null) {
            Construction cons = newable.dDirectConstruction();
            directConstructions = cons != null ? Set.of(cons) : Set.of();
        }
        return directConstructions;
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

    public Newable replacing() {
        if (replacing == null) {
            replacing = Newable.D_REPLACING.current(newable);
            replacing = replacing == null ? newable : replacing;
        }
        return replacing == newable ? null : replacing;
    }

    public Set<Direction> directions() {
        if (directions == null) {
            return Collection.concat(directConstructions(), derivedConstructions()).map(Construction::reason).map(Reason::direction).toSet();
        }
        return directions;
    }

}
