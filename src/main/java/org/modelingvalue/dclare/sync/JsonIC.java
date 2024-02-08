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

package org.modelingvalue.dclare.sync;

import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.json.Config;
import org.modelingvalue.json.FromJsonBase;
import org.modelingvalue.json.Json;
import org.modelingvalue.json.ToJson;

import java.util.AbstractMap.SimpleEntry;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.stream.Stream;

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
            if (o instanceof Map) { // check if it is an Immutable Collections Map
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
            super(input, new Config());
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
        protected Map<String, Object> makeMapEntry(Map<String, Object> m, Object key, Object value) {
            return m == null ? null : m.put(key == null ? null : key.toString(), value);
        }

        @Override
        protected List<Object> makeArrayEntry(List<Object> l, int index, Object o) {
            return l == null ? null : l.add(o);
        }
    }
}
