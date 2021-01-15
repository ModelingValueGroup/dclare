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

import java.util.Arrays;
import java.util.function.Supplier;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.IdentifiedByArray;
import org.modelingvalue.collections.util.Quadruple;

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
        return (Mutable) (array().length == 3 ? array()[0] : null);
    }

    public Observer observer() {
        return (Observer) (array().length == 3 ? array()[1] : null);
    }

    public Reason reason() {
        return (Reason) (array().length == 3 ? array()[2] : array()[0]);
    }

    public boolean isObserved() {
        return array().length == 3;
    }

    public boolean isNotObserved() {
        return array().length != 3;
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
            Object[] array = reason().array();
            for (int i = 0; i < array.length; i++) {
                if (array[i] instanceof Mutable) {
                    sources = sources((Mutable) array[i], sources);
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

    public static class Reason extends IdentifiedByArray {

        public static Reason of(Object... identity) {
            return new Reason(identity);
        }

        protected Reason(Object[] identity) {
            super(identity);
        }

        public <O extends Newable> O construct(Supplier<O> supplier) {
            return currentLeaf().construct(this, supplier);
        }

        private LeafTransaction currentLeaf() {
            LeafTransaction current = LeafTransaction.getCurrent();
            if (current == null) {
                throw new NullPointerException("No current transaction in " + Thread.currentThread() + " , while accessing " + toString());
            }
            return current;
        }

        @Override
        public String toString() {
            return Arrays.toString(array());
        }

    }

    public static final class MatchInfo extends Quadruple<Newable, Object, Set<Construction>, Map<Mutable, Set<Construction>>> {

        private static final long serialVersionUID = 4565551522857366810L;

        public static MatchInfo of(Newable newable) {
            return new MatchInfo(newable, newable.dConstructions());
        }

        private MatchInfo(Newable newable, Set<Construction> cons) {
            super(newable, newable.dIdentity(), cons, Construction.sources(cons, Map.of()));
        }

        public boolean hasSameType(MatchInfo other) {
            return newable().dNewableType().equals(other.newable().dNewableType());
        }

        public boolean areTheSame(MatchInfo other) {
            return identity() != null && other.identity() != null && identity().equals(other.identity()) || //
                    other.sources().containsKey(newable()); // || //
            // identity() == null && other.hasUnidentifiedSource();
        }

        public boolean hasDirectReasonToExist() {
            return constructions().anyMatch(Construction::isNotObserved);
        }

        public boolean hasIndirectReasonToExist() {
            return sources().anyMatch(e -> !(e.getKey() instanceof Newable) || e.getValue().anyMatch(Construction::isNotObserved));
        }

        public boolean hasUnidentifiedSource() {
            return notObservedSources().anyMatch(n -> n.dIdentity() == null);
        }

        public Collection<Comparable> sourcesSortKeys() {
            return notObservedSources().map(Newable::dSortKey).sorted();
        }

        private Collection<Newable> notObservedSources() {
            Set<Newable> set = sources().filter(e -> e.getValue().anyMatch(Construction::isNotObserved)).map(Entry::getKey).filter(Newable.class).toSet();
            return set.exclude(p -> set.anyMatch(c -> c.dHasAncestor(p)));
        }

        public Newable newable() {
            return a();
        }

        public Object identity() {
            return b();
        }

        public Set<Construction> constructions() {
            return c();
        }

        private Map<Mutable, Set<Construction>> sources() {
            return d();
        }

    }

}