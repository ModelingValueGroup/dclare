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

package org.modelingvalue.dclare.sync;

import java.util.AbstractMap.SimpleEntry;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.stream.Stream;

import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.json.*;

public class JsonIC extends Json {
    public static String toJson(Object o) {
        return ToJsonIC.toJson(o);
    }

    public static Object fromJson(String s) {
        return FromJsonIC.fromJson(s);
    }

    public static class ToJsonIC extends ToJson {
        public static String toJson(Object o) {
            return new ToJsonIC(o).render();
        }

        protected ToJsonIC(Object root) {
            super(root);
        }

        @Override
        protected boolean isMapType(Object o) {
            return super.isMapType(o) || o instanceof Map;
        }

        @Override
        protected Iterator<Entry<Object, Object>> getMapIterator(Object o) {
            if (o instanceof Map) {
                @SuppressWarnings("unchecked")
                Stream<Entry<Object, Object>> entryStream = ((Map<Object, Object>) o).map(e1 -> new SimpleEntry<>(e1.getKey(), e1.getValue()));
                return entryStream.sorted(Comparator.comparing(e -> e.getKey().toString())).iterator();
            } else {
                return super.getMapIterator(o);
            }
        }
    }

    public static class FromJsonIC extends FromJsonBase<List<Object>, Map<String, Object>> {
        public static Object fromJson(String s) {
            return new FromJsonIC(s).parse();
        }

        protected FromJsonIC(String input) {
            super(input);
        }

        @Override
        public Object parse() {
            return super.parse();
        }

        @Override
        protected Map<String, Object> makeMap() {
            return Map.of();
        }

        @Override
        protected List<Object> makeArray() {
            return List.of();
        }

        @Override
        protected Map<String, Object> makeMapEntry(Map<String, Object> m, String key, Object value) {
            return m == null ? null : m.put(key, value);
        }

        @Override
        protected List<Object> makeArrayEntry(List<Object> l, Object o) {
            return l == null ? null : l.add(o);
        }
    }
}
