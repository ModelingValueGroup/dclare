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

import static org.modelingvalue.dclare.SetableModifier.durable;

import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.IdentifiedByArray;

@SuppressWarnings("rawtypes")
public class Construction extends IdentifiedByArray {

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
        return (Mutable) (isDerived() ? super.get(0) : null);
    }

    public Observer observer() {
        return (Observer) (isDerived() ? super.get(1) : null);
    }

    public Reason reason() {
        return (Reason) (isDerived() ? super.get(2) : super.get(0));
    }

    public Set<Mutable> sources() {
        return isDerived() ? reason().sources(object()) : Set.of();
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

        private Set<Mutable> sources(Mutable thiz) {
            Set<Mutable> result = Set.of(thiz);
            for (int i = 0; i < size(); i++) {
                Object v = get(thiz, i);
                if (v instanceof Mutable) {
                    result = result.add((Mutable) v);
                }
            }
            return result;
        }

        protected abstract Reason clone(Mutable thiz, Object[] identity);

        public Reason actualize() {
            boolean changed = false;
            Object[] changedId = new Object[size()];
            for (int i = 0; i < size(); i++) {
                Object v = get(Mutable.THIS, i);
                if (v instanceof Newable) {
                    Newable replacing = ((Newable) v).dReplacing();
                    if (replacing != null) {
                        changed = true;
                        v = replacing;
                    }
                }
                changedId[i] = v;
            }
            return changed ? clone(Mutable.THIS, changedId) : this;
        }

        public boolean isActualized() {
            for (int i = 0; i < size(); i++) {
                Object v = get(Mutable.THIS, i);
                if (v instanceof Newable && ((Newable) v).dReplacing() != null) {
                    return true;
                }
            }
            return false;
        }

    }

}
