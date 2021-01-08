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
import java.util.function.Supplier;

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

    public static Optional<Newable> notObservedSource(Map<Newable, Set<Construction>> sources) {
        return sources.filter(e -> e.getValue().anyMatch(Construction::isNotObserved)).map(Entry::getKey).findFirst();
    }

    public static Map<Newable, Set<Construction>> sources(Set<Construction> cons, Map<Newable, Set<Construction>> sources) {
        for (Construction c : cons) {
            sources = sources.addAll(c.sources(sources));
        }
        return sources;
    }

    private Map<Newable, Set<Construction>> sources(Map<Newable, Set<Construction>> sources) {
        if (object() instanceof Newable && !sources.containsKey((Newable) object())) {
            Set<Construction> cons = ((Newable) object()).dConstructions();
            sources = sources.put((Newable) object(), cons);
            sources = sources.addAll(sources(cons, sources));
        }
        Object[] array = reason().array();
        for (int i = 0; i < array.length; i++) {
            if (array[i] instanceof Newable && !sources.containsKey((Newable) array[i])) {
                Set<Construction> cons = ((Newable) array[i]).dConstructions();
                sources = sources.put((Newable) array[i], cons);
                sources = sources.addAll(sources(cons, sources));
            }
        }
        return sources;
    }

    public static Set<Object> reasonTypes(Set<Construction> sources) {
        return sources.map(Construction::reason).map(Reason::type).toSet();
    }

    public boolean completelyIdentified() {
        return object().isIdentified() && reason().completelyIdentified();
    }

    public abstract static class Reason extends IdentifiedByArray {

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

        public abstract Object type();

        public boolean completelyIdentified() {
            Object[] array = array();
            for (int i = 0; i < array.length; i++) {
                if (array[i] instanceof Newable && !((Newable) array[i]).isIdentified()) {
                    return false;
                }
            }
            return true;
        }

    }

}
