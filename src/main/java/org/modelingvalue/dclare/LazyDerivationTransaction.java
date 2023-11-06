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

import org.modelingvalue.collections.Collection;

public class LazyDerivationTransaction extends AbstractDerivationTransaction {

    private final MutableState state;

    protected LazyDerivationTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
        state = new MutableState(universeTransaction.emptyState());
    }

    @Override
    protected String getCurrentTypeForTrace() {
        return "LZ";
    }

    public State derive(StateDeltaHandler diffHandler) {
        state.setState(state());
        try {
            deriveMutable(universeTransaction().universe());
            State result = state.state();
            diffHandler.handleDelta(state(), result, true, ImperativeTransaction.SETTED_MAP);
            return result;
        } finally {
            state.setState(universeTransaction().emptyState());
        }
    }

    private void deriveMutable(Mutable mutable) {
        MutableClass dClass = mutable.dClass();
        @SuppressWarnings({"unchecked", "rawtypes"})
        Collection<Setable<Mutable, ?>> containments = (Collection) dClass.dSetables().filter(Setable::containment);
        containments.flatMap(s -> s.<Mutable> getCollection(mutable)).forEach(m -> deriveMutable(m));
        dClass.dObservers().filter(o -> o.targets().isEmpty()).forEach(o -> runDeriver(mutable, null, o, 0));
    }

    @Override
    protected <O, T> boolean doDeriveSet(O object, Getable<O, T> getable) {
        return super.doDeriveSet(object, getable) && //
                (((Setable<O, T>) getable).direction().isLazy() || ((Mutable) object).dAllDerivers((Setable<O, T>) getable).anyMatch(o -> o.direction().isLazy()));
    }

    @Override
    protected <T, O> void setInMemoization(ConstantState mem, O object, Setable<O, T> setable, T result) {
        super.setInMemoization(mem, object, setable, result);
        if (setable.preserved() && !setable.direction().isLazy()) {
            state.set(object, setable, result);
        }
    }
}
