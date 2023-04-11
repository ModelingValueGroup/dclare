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

import static org.modelingvalue.dclare.SetableModifier.*;

import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.QualifiedSet;
import org.modelingvalue.dclare.Observer.Constructed;

public interface Newable extends Mutable {

    Constant<Newable, Construction>                          D_INITIAL_CONSTRUCTION = Constant.of("D_INITIAL_CONSTRUCTION", null, plumbing, durable);
    @SuppressWarnings({"unchecked", "rawtypes"})
    Observed<Newable, QualifiedSet<Direction, Construction>> D_ALL_DERIVATIONS      = Observed.of("D_ALL_DERIVATIONS", QualifiedSet.of(c -> c.reason().direction()), (tx, o, b, a) -> {
                                                                                        Setable.<QualifiedSet<Direction, Construction>, Construction> diff(b, a,                       //
                                                                                                add -> {
                                                                                                    Constructed cons = add.observer().constructed();
                                                                                                    cons.set(add.object(), Map::put, Entry.of(add.reason(), o));
                                                                                                },                                                                                     //
                                                                                                rem -> {
                                                                                                    Constructed cons = rem.observer().constructed();
                                                                                                    if (o.equals(cons.current(rem.object()).get(rem.reason()))) {
                                                                                                        cons.set(rem.object(), Map::removeKey, rem.reason());
                                                                                                    }
                                                                                                });
                                                                                    }, plumbing, doNotMerge);

    Constant<Newable, Object>                                D_IDENTITY             = Constant.of("D_IDENTITY", null, plumbing, durable);

    @SuppressWarnings("rawtypes")
    Object dIdentity();

    Object dNewableType();

    @SuppressWarnings("rawtypes")
    Comparable dSortKey();

    default Construction dInitialConstruction() {
        return D_INITIAL_CONSTRUCTION.get(this);
    }

    default QualifiedSet<Direction, Construction> dAllDerivations() {
        return D_ALL_DERIVATIONS.current(this);
    }

    @Override
    default void dActivate() {
        Mutable.super.dActivate();
    }

    @Override
    default void dDeactivate() {
        Mutable.super.dDeactivate();
    }

    @Override
    default void dHandleRemoved(Mutable parent) {
        Mutable.super.dHandleRemoved(parent);
    }

    default Newable dReplacing() {
        Construction init = D_INITIAL_CONSTRUCTION.get(this);
        if (init.isDerived()) {
            Constructed cons = init.observer().constructed();
            Mutable mut = init.object();
            Newable newable = cons.get(mut).get(init.reason());
            return equals(newable) ? null : newable;
        } else {
            return null;
        }
    }

}
