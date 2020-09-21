//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2019 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

package org.modelingvalue.dclare.sync.converter;

import java.util.stream.*;

import org.modelingvalue.collections.*;
import org.modelingvalue.collections.util.*;
import org.modelingvalue.dclare.*;
import org.modelingvalue.dclare.sync.*;

@SuppressWarnings("rawtypes")
public class ConvertDeltaToStringDelta implements Converter<Map<Object, Map<Setable, Pair<Object, Object>>>, java.util.Map<String, java.util.Map<String, Object>>> {
    private final ConvertObjectToStringOrList convertObjectToStringOrList;

    public ConvertDeltaToStringDelta(SerializationHelper helper) {
        this.convertObjectToStringOrList = new ConvertObjectToStringOrList(helper);
    }

    @Override
    public java.util.Map<String, java.util.Map<String, Object>> convertForward(Map<Object, Map<Setable, Pair<Object, Object>>> root) {
        return root
                .map(e1 -> Entry.of(
                        (String) convertObjectToStringOrList.convertForward(e1.getKey()),
                        e1.getValue()
                                .map(e2 -> Entry.of(
                                        (String) convertObjectToStringOrList.convertForward(e2.getKey()),
                                        convertObjectToStringOrList.convertForward(e2.getValue().b())
                                ))
                                .collect(Collectors.toMap(Entry::getKey, Entry::getValue))
                ))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    @Override
    public Map<Object, Map<Setable, Pair<Object, Object>>> convertBackward(java.util.Map<String, java.util.Map<String, Object>> root) {
        return Collection.of(root
                .entrySet()
                .stream()
                .map(e1 -> Entry.of(
                        convertObjectToStringOrList.convertBackward(e1.getKey()),
                        Collection.of(e1.getValue()
                                .entrySet()
                                .stream()
                                .map(e2 -> Entry.of(
                                        (Setable) convertObjectToStringOrList.convertBackward(e2.getKey()),
                                        Pair.of(null, convertObjectToStringOrList.convertBackward(e2.getValue()))
                                ))
                        ).toMap(e -> e)
                ))
        ).toMap(e -> e);
    }
}
