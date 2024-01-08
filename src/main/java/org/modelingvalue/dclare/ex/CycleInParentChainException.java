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

import org.modelingvalue.collections.List;
import org.modelingvalue.dclare.Mutable;
import org.modelingvalue.dclare.Setable;

import java.io.Serial;

public class CycleInParentChainException extends ConsistencyError {
    @Serial
    private static final long serialVersionUID = -8866815750182251938L;

    private final List<Mutable> chain;

    public CycleInParentChainException(Object object, Setable<?, ?> setable, List<Mutable> chain) {
        super(object, setable, 10);
        this.chain = chain;
    }

    @Override
    public String getMessage() {
        return "cycle (#" + (chain.size() - 1) + ") detected in parent chain while getting property '" + getFeature() + "' of object '" + getObject() + "' chain=" + chain;
    }
}
