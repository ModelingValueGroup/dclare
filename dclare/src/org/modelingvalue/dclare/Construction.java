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

import java.util.Optional;

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
                Object object = super.get(i);
                if (object instanceof Mutable && object != Mutable.THIS) {
                    sources = sources((Mutable) object, sources);
                }
            }
        }
        return sources;
    }

    private static Map<Mutable, Set<Construction>> sources(Mutable mutable, Map<Mutable, Set<Construction>> sources) {
        if (!sources.containsKey(mutable)) {
            if (mutable instanceof Newable && LeafTransaction.getCurrent().universeTransaction().preState().get(mutable, Mutable.D_PARENT_CONTAINING) == null) {
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
        private final Map<Reason, Newable>      constructed;

        private Object                          identity;
        private Set<Construction>               derivedConstructions;
        private Boolean                         isOld;
        private Map<Mutable, Set<Construction>> sources;
        private Set<Object>                     derivedReasonTypes;
        private Set<Newable>                    notObservedSources;
        private Set<Newable>                    sourcesAndAncestors;

        public static MatchInfo of(Newable newable, Map<Reason, Newable> constructed) {
            return new MatchInfo(newable, constructed);
        }

        private MatchInfo(Newable newable, Map<Reason, Newable> constructed) {
            this.newable = newable;
            this.constructed = constructed;
        }

        public boolean haveSameType(MatchInfo other) {
            return newable().dNewableType().equals(other.newable().dNewableType());
        }

        public boolean shouldBeTheSame(MatchInfo from) {
            return from.sourcesAndAncestors().contains(newable()) || //
                    (derivedReasonTypes().isEmpty() && (identity() != null ? identity().equals(from.identity()) : from.hasUnidentifiedSource()));
        }

        public void mergeIn(MatchInfo from) {
            derivedConstructions = derivedConstructions().addAll(from.derivedConstructions());
            derivedReasonTypes = derivedReasonTypes().addAll(from.derivedReasonTypes());
            sources = sources().addAll(from.sources());
            notObservedSources = notObservedSources().addAll(from.notObservedSources());
            sourcesAndAncestors = sourcesAndAncestors().addAll(from.sourcesAndAncestors());
        }

        public boolean isCarvedInStone() {
            return isOld() || hasDirectConstruction();
        }

        private boolean hasDirectConstruction() {
            return newable.dDirectConstruction() != null;
        }

        public boolean hasUnidentifiedSource() {
            return notObservedSources().anyMatch(n -> n.dMatchingIdentity() == null);
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

        public Set<Construction> derivedConstructions() {
            if (derivedConstructions == null) {
                Set<Construction> cons = newable.dDerivedConstructions();
                Optional<Entry<Reason, Newable>> opt = constructed.filter(e -> e.getValue().equals(newable)).findAny();
                if (opt.isPresent()) {
                    ObserverTransaction tx = (ObserverTransaction) LeafTransaction.getCurrent();
                    cons = cons.add(Construction.of(tx.mutable(), tx.observer(), opt.get().getKey()));
                }
                derivedConstructions = cons;
            }
            return derivedConstructions;
        }

        public Set<Newable> sourcesAndAncestors() {
            if (sourcesAndAncestors == null) {
                sourcesAndAncestors = sources().flatMap(s -> s.getKey().dAncestors(Newable.class)).toSet();
            }
            return sourcesAndAncestors;
        }

        private Map<Mutable, Set<Construction>> sources() {
            if (sources == null) {
                Construction direct = newable.dDirectConstruction();
                Set<Construction> derived = derivedConstructions();
                sources = Construction.sources(direct != null ? derived.add(direct) : derived, Map.of());
            }
            return sources;
        }

        private Set<Object> derivedReasonTypes() {
            if (derivedReasonTypes == null) {
                derivedReasonTypes = derivedConstructions().map(Construction::reason).map(Reason::type).toSet();
            }
            return derivedReasonTypes;
        }

        private Set<Newable> notObservedSources() {
            if (notObservedSources == null) {
                Set<Newable> set = sources().filter(e -> e.getValue().anyMatch(Construction::isNotObserved)).map(Entry::getKey).filter(Newable.class).toSet();
                notObservedSources = set.exclude(p -> set.anyMatch(c -> c.dHasAncestor(p))).toSet();
            }
            return notObservedSources;
        }

        private boolean isOld() {
            if (isOld == null) {
                UniverseTransaction utx = LeafTransaction.getCurrent().universeTransaction();
                isOld = utx.preState().get(newable, Mutable.D_PARENT_CONTAINING) != null;
            }
            return isOld;
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
