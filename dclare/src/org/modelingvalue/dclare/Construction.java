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
import org.modelingvalue.collections.util.IdentifiedByArray;

@SuppressWarnings("rawtypes")
public class Construction extends IdentifiedByArray {

    protected static final Constant<Construction.Reason, Newable> CONSTRUCTED = //
            Constant.of("D_CONSTRUCTED", (Newable) null);

    public static Construction of(Reason reason) {
        return new Construction(reason);
    }

    public static Construction of(Mutable object, Observer observer, Reason reason) {
        return new Construction(object, observer, reason);
    }

    private Construction(Reason reason) {
        super(new Object[]{reason});
    }

    private Construction(Mutable object, Observer observer, Reason reason) {
        super(new Object[]{object, observer, reason});
    }

    public Mutable object() {
        return (Mutable) (super.size() == 3 ? super.get(0) : null);
    }

    public Observer observer() {
        return (Observer) (super.size() == 3 ? super.get(1) : null);
    }

    public Reason reason() {
        return (Reason) (super.size() == 3 ? super.get(2) : super.get(0));
    }

    @Override
    public Object get(int i) {
        return reason().get(object(), i);
    }

    @Override
    public int size() {
        return reason().size();
    }

    public boolean isDerived() {
        return super.size() == 3;
    }

    public boolean isNotDerived() {
        return super.size() != 3;
    }

    public Set<Newable> derivers() {
        Set<Newable> result = Set.of();
        if (isDerived()) {
            if (object() instanceof Newable) {
                result = result.add((Newable) object());
            }
            for (int i = 0; i < super.size(); i++) {
                Object object = super.get(i);
                if (object instanceof Newable && object != Mutable.THIS) {
                    result = result.add((Newable) object);
                }
            }
        }
        return result;
    }

    public static abstract class Reason extends IdentifiedByArray {

        protected Reason(Mutable thiz, Object[] identity) {
            super(thiz(thiz, identity));
        }

        private static Object[] thiz(Mutable thiz, Object[] identity) {
            if (thiz != null) {
                for (int i = 0; i < identity.length; i++) {
                    if (thiz.equals(identity[i])) {
                        identity[i] = Mutable.THIS;
                    }
                }
            }
            return identity;
        }

        @Override
        public Object get(int i) {
            throw new UnsupportedOperationException();
        }

        public Object get(Mutable thiz, int i) {
            Object e = super.get(i);
            return e == Mutable.THIS ? thiz : e;
        }

        public abstract Object direction();

    }

    public static final class MatchInfo {

        private final Newable newable;

        private Object        identity;
        private Boolean       isCarvedInStone;
        private Set<Newable>  notDerivedSources;
        private Comparable    sortKey;
        private Set<Object>   directions;

        public static MatchInfo of(Newable newable) {
            return new MatchInfo(newable);
        }

        private MatchInfo(Newable newable) {
            this.newable = newable;
        }

        public boolean mustBeTheSame(MatchInfo from) {
            return newable().dNewableType().equals(from.newable().dNewableType()) && //
                    !from.directions().anyMatch(directions()::contains) && //
                    (identity() != null ? identity().equals(from.identity()) : from.hasUnidentifiedSource());
        }

        public void mergeIn(MatchInfo from) {
            directions = directions().addAll(from.directions());
        }

        public Set<Object> directions() {
            if (directions == null) {
                directions = newable.dConstructions().map(Construction::reason).map(Reason::direction).toSet();
            }
            return directions;
        }

        public boolean hasUnidentifiedSource() {
            return notDerivedSources().anyMatch(n -> n.dMatchingIdentity() == null);
        }

        public Newable newable() {
            return newable;
        }

        private Object identity() {
            if (identity == null) {
                identity = newable.dMatchingIdentity();
                if (identity == null) {
                    identity = ConstantState.NULL;
                }
            }
            return identity == ConstantState.NULL ? null : identity;
        }

        public Comparable sortKey() {
            if (sortKey == null) {
                sortKey = notDerivedSources().map(Newable::dSortKey).sorted().findFirst().orElse(newable().dSortKey());
            }
            return sortKey;
        }

        private Set<Newable> notDerivedSources() {
            if (notDerivedSources == null) {
                notDerivedSources = newable.dNonDerivedSources();
            }
            return notDerivedSources;
        }

        public boolean isCarvedInStone() {
            if (isCarvedInStone == null) {
                isCarvedInStone = newable.dDirectConstruction() != null || //
                        LeafTransaction.getCurrent().universeTransaction().preState().get(newable, Mutable.D_PARENT_CONTAINING) != null;
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

    }

}
