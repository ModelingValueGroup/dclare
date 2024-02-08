//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2024 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
//                                                                                                                       ~
//  Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in       ~
//  compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0   ~
//  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on  ~
//  an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the   ~
//  specific language governing permissions and limitations under the License.                                           ~
//                                                                                                                       ~
//  Maintainers:                                                                                                         ~
//      Wim Bast, Tom Brus                                                                                               ~
//                                                                                                                       ~
//  Contributors:                                                                                                        ~
//      Ronald Krijgsheld ✝, Arjan Kok, Carel Bast                                                                       ~
// --------------------------------------------------------------------------------------------------------------------- ~
//  In Memory of Ronald Krijgsheld, 1972 - 2023                                                                          ~
//      Ronald was suddenly and unexpectedly taken from us. He was not only our long-term colleague and team member      ~
//      but also our friend. "He will live on in many of the lines of code you see below."                               ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.dclare.ex;

import org.modelingvalue.collections.*;
import org.modelingvalue.dclare.*;

import java.util.stream.*;

@SuppressWarnings({"rawtypes", "unused"})
public final class TooManyObserversException extends ConsistencyError {

    private static final long                        serialVersionUID = -1059588522731393631L;

    private final DefaultMap<Observer, Set<Mutable>> observers;
    private final UniverseTransaction                universeTransaction;

    public TooManyObserversException(Object object, Observed observed, DefaultMap<Observer, Set<Mutable>> observers, UniverseTransaction universeTransaction) {
        super(object, observed, 1, universeTransaction.preState().get(() -> "Too many observers (" + LeafTransaction.sizeForConsistency(observers) + ") of " + object + "." + observed));
        this.observers = observers;
        this.universeTransaction = universeTransaction;
    }

    @Override
    public String getMessage() {
        String observersMap = universeTransaction.preState().get(() -> observers.map(String::valueOf).collect(Collectors.joining("\n  ")));
        return getSimpleMessage() + ":\n" + observersMap;
    }

    public String getSimpleMessage() {
        return super.getMessage();
    }

    public int getNrOfObservers() {
        return LeafTransaction.sizeForConsistency(observers);
    }

    public DefaultMap<Observer, Set<Mutable>> getObservers() {
        return observers;
    }

}
