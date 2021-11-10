//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2021 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import org.modelingvalue.collections.*;
import org.modelingvalue.dclare.sync.SerialisationPool.*;

import java.util.stream.*;

@SuppressWarnings("unused")
public class Converters {
    public static class ByteConverter extends BaseConverter<Byte> {
        public ByteConverter() {
            super(Byte.class);
        }

        @Override
        public String serialize(Object context, Byte o) {
            return o.toString();
        }

        @Override
        public Byte deserialize(Object context, String s) {
            return Byte.valueOf(s);
        }
    }

    public static class ShortConverter extends BaseConverter<Short> {
        public ShortConverter() {
            super(Short.class);
        }

        @Override
        public String serialize(Object context, Short o) {
            return o.toString();
        }

        @Override
        public Short deserialize(Object context, String s) {
            return Short.valueOf(s);
        }
    }

    public static class IntegerConverter extends BaseConverter<Integer> {
        public IntegerConverter() {
            super(Integer.class);
        }

        @Override
        public String serialize(Object context, Integer o) {
            return o.toString();
        }

        @Override
        public Integer deserialize(Object context, String s) {
            return Integer.valueOf(s);
        }
    }

    public static class LongConverter extends BaseConverter<Long> {
        public LongConverter() {
            super(Long.class);
        }

        @Override
        public String serialize(Object context, Long o) {
            return o.toString();
        }

        @Override
        public Long deserialize(Object context, String s) {
            return Long.valueOf(s);
        }
    }

    public static class FloatConverter extends BaseConverter<Float> {
        public FloatConverter() {
            super(Float.class);
        }

        @Override
        public String serialize(Object context, Float o) {
            return o.toString();
        }

        @Override
        public Float deserialize(Object context, String s) {
            return Float.valueOf(s);
        }
    }

    public static class DoubleConverter extends BaseConverter<Double> {
        public DoubleConverter() {
            super(Double.class);
        }

        @Override
        public String serialize(Object context, Double o) {
            return o.toString();
        }

        @Override
        public Double deserialize(Object context, String s) {
            return Double.valueOf(s);
        }
    }

    public static class CharacterConverter extends BaseConverter<Character> {
        public CharacterConverter() {
            super(Character.class);
        }

        @Override
        public String serialize(Object context, Character o) {
            return o.toString();
        }

        @Override
        public Character deserialize(Object context, String s) {
            return s.charAt(0);
        }
    }

    public static class BooleanConverter extends BaseConverter<Boolean> {
        public BooleanConverter() {
            super(Boolean.class);
        }

        @Override
        public String serialize(Object context, Boolean o) {
            return o.toString();
        }

        @Override
        public Boolean deserialize(Object context, String s) {
            return Boolean.valueOf(s);
        }
    }

    public static class StringConverter extends BaseConverter<String> {
        public StringConverter() {
            super(String.class);
        }

        @Override
        public String serialize(Object context, String o) {
            return o;
        }

        @Override
        public String deserialize(Object context, String s) {
            return s;
        }
    }

    public static class ImmutableListConverter extends BaseConverter<List<?>> {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public ImmutableListConverter() {
            super((Class<List<?>>) (Class) List.class, "immutable-list");
        }

        @Override
        public String serialize(Object context, List<?> l) {
            return JsonIC.toJson(l.map(serialisationPool::serialize).collect(Collectors.toList()));
        }

        @SuppressWarnings("unchecked")
        @Override
        public List<?> deserialize(Object context, String string) {
            List<Object> l = (List<Object>) JsonIC.fromJson(string);
            return l.map(e -> serialisationPool.deserialize(e.toString())).toList();
        }
    }

    public static class ListConverter extends BaseConverter<java.util.List<?>> {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public ListConverter() {
            super((Class<java.util.List<?>>) (Class) java.util.List.class, "list");
        }

        @Override
        public String serialize(Object context, java.util.List<?> l) {
            return JsonIC.toJson(l.stream().map(serialisationPool::serialize).collect(Collectors.toList()));
        }

        @SuppressWarnings("unchecked")
        @Override
        public java.util.List<?> deserialize(Object context, String string) {
            List<Object> l = (List<Object>) JsonIC.fromJson(string);
            return l.map(o -> serialisationPool.deserialize(o.toString()))
                    .collect(Collectors.toList());
        }
    }

    public static class ImmutableMapConverter extends BaseConverter<Map<?, ?>> {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public ImmutableMapConverter() {
            super((Class<Map<?, ?>>) (Class) Map.class, "immutable-map");
        }

        @Override
        public String serialize(Object context, Map<?, ?> m) {
            return JsonIC.toJson(
                    m.collect(Collectors.toMap(
                            e -> serialisationPool.serialize(e.getKey(), context),
                            e -> serialisationPool.serialize(e.getValue(), context)))
            );
        }

        @Override
        public Map<?, ?> deserialize(Object context, String string) {
            Map<?, ?> m = (Map<?, ?>) JsonIC.fromJson(string);
            return m.toMap(e -> Entry.of(
                    serialisationPool.deserialize(e.getKey().toString()),
                    serialisationPool.deserialize(e.getValue().toString())
            ));
        }
    }

    public static class MapConverter extends BaseConverter<java.util.Map<?, ?>> {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public MapConverter() {
            super((Class<java.util.Map<?, ?>>) (Class) java.util.Map.class, "map");
        }

        @Override
        public String serialize(Object context, java.util.Map<?, ?> m) {
            return JsonIC.toJson(
                    m.entrySet()
                            .stream()
                            .collect(Collectors.toMap(
                                    e -> serialisationPool.serialize(e.getKey(), context),
                                    e -> serialisationPool.serialize(e.getValue(), context)))
            );
        }

        @Override
        public java.util.Map<?, ?> deserialize(Object context, String string) {
            Map<?, ?> m = (Map<?, ?>) JsonIC.fromJson(string);
            return m.collect(Collectors.toMap(
                    e -> serialisationPool.deserialize(e.getKey().toString()),
                    e -> serialisationPool.deserialize(e.getValue().toString())
            ));
        }
    }

    public static class ImmutableSetConverter extends BaseConverter<Set<?>> {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public ImmutableSetConverter() {
            super((Class<Set<?>>) (Class) Set.class, "immutable-set");
        }

        @Override
        public String serialize(Object context, Set<?> s) {
            return JsonIC.toJson(s.map(serialisationPool::serialize).collect(Collectors.toList()));
        }

        @SuppressWarnings("unchecked")
        @Override
        public Set<?> deserialize(Object context, String string) {
            List<Object> l = (List<Object>) JsonIC.fromJson(string);
            return l.map(e -> serialisationPool.deserialize(e.toString())).toSet();
        }
    }

    public static class SetConverter extends BaseConverter<java.util.Set<?>> {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public SetConverter() {
            super((Class<java.util.Set<?>>) (Class) java.util.Set.class, "set");
        }

        @Override
        public String serialize(Object context, java.util.Set<?> s) {
            return JsonIC.toJson(s.stream().map(serialisationPool::serialize).collect(Collectors.toList()));
        }

        @SuppressWarnings("unchecked")
        @Override
        public java.util.Set<?> deserialize(Object context, String string) {
            List<Object> l = (List<Object>) JsonIC.fromJson(string);
            return l.map(o -> serialisationPool.deserialize(o.toString()))
                    .collect(Collectors.toSet());
        }
    }
}
