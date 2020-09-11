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
import org.modelingvalue.dclare.ex.*;
import org.modelingvalue.dclare.sync.*;

@SuppressWarnings("rawtypes")
public class ConvertStringDelta implements Converter<Map<Object, Map<Setable, Pair<Object, Object>>>, java.util.Map<String, java.util.Map<String, String>>> {
    private final SerializationHelper helper;

    public ConvertStringDelta(SerializationHelper helper) {
        this.helper = helper;
    }

    @Override
    public java.util.Map<String, java.util.Map<String, String>> convertForward(Map<Object, Map<Setable, Pair<Object, Object>>> root) {
        return root
                .map(e1 -> Entry.of(
                        helper.serializeMutable((Mutable) e1.getKey()),
                        e1.getValue()
                                .map(e2 -> Entry.of(
                                        helper.serializeSetable(e2.getKey()),
                                        serializeValue(e2.getValue().b())
                                ))
                                .collect(Collectors.toMap(Entry::getKey, Entry::getValue))
                ))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    @Override
    public Map<Object, Map<Setable, Pair<Object, Object>>> convertBackward(java.util.Map<String, java.util.Map<String, String>> root) {
        return Collection.of(root
                .entrySet()
                .stream()
                .map(e1 -> Entry.of(
                        (Object) helper.deserializeMutable(e1.getKey()),
                        Collection.of(e1.getValue()
                                .entrySet()
                                .stream()
                                .map(e2 -> Entry.of(
                                        helper.deserializeSetable(e2.getKey()),
                                        Pair.of(null, deserializeValue(e2.getValue()))
                                ))
                        ).toMap(e -> e)
                ))
        ).toMap(e -> e);
    }

    private String serializeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "s" + value.toString();
        }
        if (value instanceof Byte) {
            return "B" + value.toString();
        }
        if (value instanceof Character) {
            return "C" + value.toString();
        }
        if (value instanceof Double) {
            return "D" + value.toString();
        }
        if (value instanceof Float) {
            return "F" + value.toString();
        }
        if (value instanceof Integer) {
            return "I" + value.toString();
        }
        if (value instanceof Long) {
            return "J" + value.toString();
        }
        if (value instanceof Short) {
            return "S" + value.toString();
        }
        if (value instanceof Boolean) {
            return "Z" + value.toString();
        }
        if (value instanceof Mutable) {
            return "m" + helper.serializeMutable((Mutable) value);
        }
        throw new NotSerializableError("class=" + value.getClass().getName());
    }

    private Object deserializeValue(String s) {
        if (s == null || s.isEmpty() || s.equals("null")) {
            return null;
        }
        char   pre  = s.charAt(0);
        String rest = s.substring(1);
        switch (pre) {
        case 's':
            return rest;
        case 'B':
            return Byte.parseByte(rest);
        case 'C':
            return rest.charAt(0);
        case 'D':
            return Double.parseDouble(rest);
        case 'F':
            return Float.parseFloat(rest);
        case 'I':
            return Integer.parseInt(rest);
        case 'J':
            return Long.parseLong(rest);
        case 'S':
            return Short.parseShort(rest);
        case 'Z':
            return Boolean.parseBoolean(rest);
        case 'm':
            return helper.deserializeMutable(rest);
        default:
            throw new NotDeserializableError("s='" + s + "'");
        }
    }
}
