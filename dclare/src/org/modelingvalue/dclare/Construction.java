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

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Map;
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

    public boolean isObserved() {
        return super.size() == 3;
    }

    public boolean isNotObserved() {
        return super.size() != 3;
    }

    private static Map<Mutable, Set<Construction>> sources(Set<Construction> cons, Map<Mutable, Set<Construction>> sources) {
        for (Construction c : cons) {
            sources = sources.addAll(c.sources(sources));
        }
        return sources;
    }

    private Map<Mutable, Set<Construction>> sources(Map<Mutable, Set<Construction>> sources) {
        if (isObserved()) {
            sources = sources(object(), sources);
            for (int i = 0; i < super.size(); i++) {
                if (super.get(i) instanceof Mutable) {
                    sources = sources((Mutable) super.get(i), sources);
                }
            }
        }
        return sources;
    }

    private static Map<Mutable, Set<Construction>> sources(Mutable mutable, Map<Mutable, Set<Construction>> sources) {
        if (!sources.containsKey(mutable)) {
            if (mutable instanceof Newable) {
                Set<Construction> cons = ((Newable) mutable).dConstructions();
                sources = sources.put(mutable, cons);
                return sources.putAll(sources(cons, sources));
            } else {
                return sources.put(mutable, Set.of());
            }
        } else {
            return sources;
        }
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

        public abstract Object type();

    }

    public static final class MatchInfo {

        private final Newable                   newable;
        private final Object                    identity;
        private final Set<Construction>         constructions;

        private Map<Mutable, Set<Construction>> sources;
        private Set<Object>                     types;
        private Set<Newable>                    notObservedSources;
        private Set<Newable>                    sourcesAndAncestors;

        public static MatchInfo of(Newable newable) {
            return new MatchInfo(newable);
        }

        private MatchInfo(Newable newable) {
            this.newable = newable;
            this.identity = newable.dMatchingIdentity();
            this.constructions = newable.dConstructions();
        }

        public boolean hasSameType(MatchInfo other) {
            return newable().dNewableType().equals(other.newable().dNewableType());
        }

        public boolean areTheSame(MatchInfo other) {
            if (!other.types().isEmpty() && !types().anyMatch(other.types()::contains) && (identity() != null ? identity().equals(other.identity()) : other.hasUnidentifiedSource())) {
                types = types().addAll(other.types());
                other.types = Set.of();
                return true;
            } else {
                return other.sourcesAndAncestors().contains(newable());
            }
        }

        private Set<Newable> sourcesAndAncestors() {
            if (sourcesAndAncestors == null) {
                sourcesAndAncestors = sources().flatMap(s -> s.getKey().dAncestors(Newable.class)).toSet();
            }
            return sourcesAndAncestors;
        }

        public boolean areUnidentified(MatchInfo other) {
            return identity() == null && other.hasUnidentifiedSource();
        }

        public boolean hasDirectReasonToExist() {
            return constructions().anyMatch(Construction::isNotObserved);
        }

        public boolean hasIndirectReasonToExist() {
            return constructions().anyMatch(Construction::isObserved);
        }

        public boolean hasUnidentifiedSource() {
            return notObservedSources().anyMatch(n -> n.dMatchingIdentity() == null);
        }

        public Collection<Comparable> sourcesSortKeys() {
            return notObservedSources().map(Newable::dSortKey).sorted();
        }

        public Newable newable() {
            return newable;
        }

        public Object identity() {
            return identity;
        }

        public Set<Construction> constructions() {
            return constructions;
        }

        public Map<Mutable, Set<Construction>> sources() {
            if (sources == null) {
                sources = Construction.sources(constructions, Map.of());
            }
            return sources;
        }

        private Set<Object> types() {
            if (types == null) {
                types = constructions.map(Construction::reason).map(Reason::type).toSet();
            }
            return types;
        }

        private Set<Newable> notObservedSources() {
            if (notObservedSources == null) {
                Set<Newable> set = sources().filter(e -> e.getValue().anyMatch(Construction::isNotObserved)).map(Entry::getKey).filter(Newable.class).toSet();
                notObservedSources = set.exclude(p -> set.anyMatch(c -> c.dHasAncestor(p))).toSet();
            }
            return notObservedSources;
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
