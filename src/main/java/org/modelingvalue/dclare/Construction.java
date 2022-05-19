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

import static org.modelingvalue.dclare.CoreSetableModifier.durable;

import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.IdentifiedByArray;
import org.modelingvalue.collections.util.Mergeable;

@SuppressWarnings("rawtypes")
public class Construction extends IdentifiedByArray implements Mergeable<Construction> {

    protected static final Constant<Construction.Reason, Newable> CONSTRUCTED = //
            Constant.of("D_CONSTRUCTED", (Newable) null, durable);

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

    protected static Set<Newable> addDeriver(Object object, Set<Newable> derivers) {
        if (object != Mutable.THIS && object instanceof Newable && !derivers.contains(object)) {
            derivers = derivers.add((Newable) object);
            Set<Newable> der = derivers;
            derivers = derivers.addAll(((Newable) object).dDerivedConstructions().flatMap(dd -> dd.derivers(der)));
        }
        return derivers;
    }

    private Set<Newable> derivers(Set<Newable> derivers) {
        derivers = addDeriver(object(), derivers);
        for (int i = 0; i < super.size(); i++) {
            derivers = addDeriver(super.get(i), derivers);
        }
        return derivers;
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

        public abstract Direction direction();

    }

    private final static Construction MERGER = Construction.of(null);;

    @Override
    public Construction merge(Construction[] branches, int length) {
        for (int i = length - 1; i >= 0; i--) {
            if (branches[i] != null) {
                return branches[i];
            }
        }
        return null;
    }

    @Override
    public Construction getMerger() {
        return MERGER;
    }

    @Override
    public Class<?> getMeetClass() {
        return Construction.class;
    }

}
