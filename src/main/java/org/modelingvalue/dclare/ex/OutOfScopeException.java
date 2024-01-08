//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2024 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

package org.modelingvalue.dclare.ex;

import org.modelingvalue.collections.Set;
import org.modelingvalue.dclare.Setable;

@SuppressWarnings("unused")
public final class OutOfScopeException extends ConsistencyError {

    private static final long serialVersionUID = -6687018038130352922L;

    private final Object      value;
    private final Set<?>      scope;

    public OutOfScopeException(Object object, Setable<?, ?> setable, Object value, Set<?> scope) {
        super(object, setable, 6, "The value '" + value + "' of '" + setable + "' of object '" + object + "' is out of scope '" + scope + "'");
        this.value = value;
        this.scope = scope;
    }

    public Object getValue() {
        return value;
    }

    public Set<?> getScope() {
        return scope;
    }

}
