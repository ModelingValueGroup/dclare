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
import java.util.function.Supplier;

import org.modelingvalue.collections.util.Pair;

public class IdentityDerivationTransaction extends AbstractDerivationTransaction {

    protected IdentityDerivationTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    private int                                depth;
    private Newable                            child;
    private Pair<Mutable, Setable<Mutable, ?>> parent;
    private Mutable                            contextMutable;

    @SuppressWarnings("rawtypes")
    public <R> R derive(Supplier<R> action, State state, int depth, Mutable contextMutable, Newable child, Pair<Mutable, Setable<Mutable, ?>> parent, ConstantState constantState) {
        this.depth = depth;
        this.child = child;
        this.parent = parent;
        this.contextMutable = contextMutable;
        try {
            return derive(action, state, constantState);
        } finally {
            this.depth = 0;
            this.child = null;
            this.parent = null;
            this.contextMutable = null;
        }
    }

    @Override
    protected <O, T> boolean doDerive(O object, Getable<O, T> getable) {
        return super.doDerive(object, getable) && !isChanged(object, getable);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <O, T> T getNonDerived(O object, Getable<O, T> getable) {
        if (isOld(object)) {
            return universeTransaction().outerStartState().get(object, getable);
        } else if (getable == Mutable.D_PARENT_CONTAINING && object.equals(child)) {
            return (T) parent;
        } else {
            return super.getNonDerived(object, getable);
        }
    }

    private <O, T> boolean isChanged(O object, Getable<O, T> getable) {
        T pre = universeTransaction().preOuterStartState().get(object, getable);
        T post = universeTransaction().outerStartState().get(object, getable);
        return !Objects.equals(pre, post);
    }

    private <O> boolean isOld(O object) {
        return object instanceof Mutable && universeTransaction().outerStartState().get((Mutable) object, Mutable.D_PARENT_CONTAINING) != null;
    }

    public Mutable getContextMutable() {
        return contextMutable;
    }

    @Override
    public int depth() {
        return depth + super.depth();
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected <O> boolean isTraceDerivation(O object, Setable setable) {
        return super.isTraceDerivation(object, setable) && (universeTransaction().getConfig().isTraceMatching() || memoization(object) != memoization());
    }

    @Override
    protected String getCurrentTypeForTrace() {
        return "ID";
    }
}
