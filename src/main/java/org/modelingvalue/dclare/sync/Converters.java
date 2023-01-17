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

package org.modelingvalue.dclare.sync;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.stream.Collectors;

import org.modelingvalue.collections.*;
import org.modelingvalue.dclare.sync.SerialisationPool.BaseConverter;
import org.modelingvalue.dclare.sync.SerialisationPool.Converter;

@SuppressWarnings("unused")
public class Converters {
    public static List<Converter<?>> ALL = List.of(new ByteConverter(), new ShortConverter(), new IntegerConverter(), new LongConverter(), new FloatConverter(), new DoubleConverter(), new CharacterConverter(), new BooleanConverter(),

            new StringConverter(), new ListConverter(), new SetConverter(), new MapConverter(), new BigIntegerConverter(), new BigDecimalConverter(),

            new ImmutableListConverter(), new ImmutableSetConverter(), new ImmutableMapConverter());

    // Primitive Converters =============================================================
    public static class ByteConverter extends BaseConverter<Byte> {
        public ByteConverter() {
            super(Byte.class);
        }

        @Override
        public String serialize(Byte o) {
            return o.toString();
        }

        @Override
        public Byte deserialize(String s) {
            return Byte.valueOf(s);
        }
    }

    public static class ShortConverter extends BaseConverter<Short> {
        public ShortConverter() {
            super(Short.class);
        }

        @Override
        public String serialize(Short o) {
            return o.toString();
        }

        @Override
        public Short deserialize(String s) {
            return Short.valueOf(s);
        }
    }

    public static class IntegerConverter extends BaseConverter<Integer> {
        public IntegerConverter() {
            super(Integer.class);
        }

        @Override
        public String serialize(Integer o) {
            return o.toString();
        }

        @Override
        public Integer deserialize(String s) {
            return Integer.valueOf(s);
        }
    }

    public static class LongConverter extends BaseConverter<Long> {
        public LongConverter() {
            super(Long.class);
        }

        @Override
        public String serialize(Long o) {
            return o.toString();
        }

        @Override
        public Long deserialize(String s) {
            return Long.valueOf(s);
        }
    }

    public static class FloatConverter extends BaseConverter<Float> {
        public FloatConverter() {
            super(Float.class);
        }

        @Override
        public String serialize(Float o) {
            return o.toString();
        }

        @Override
        public Float deserialize(String s) {
            return Float.valueOf(s);
        }
    }

    public static class DoubleConverter extends BaseConverter<Double> {
        public DoubleConverter() {
            super(Double.class);
        }

        @Override
        public String serialize(Double o) {
            return o.toString();
        }

        @Override
        public Double deserialize(String s) {
            return Double.valueOf(s);
        }
    }

    public static class CharacterConverter extends BaseConverter<Character> {
        public CharacterConverter() {
            super(Character.class);
        }

        @Override
        public String serialize(Character o) {
            return o.toString();
        }

        @Override
        public Character deserialize(String s) {
            return s.charAt(0);
        }
    }

    public static class BooleanConverter extends BaseConverter<Boolean> {
        public BooleanConverter() {
            super(Boolean.class);
        }

        @Override
        public String serialize(Boolean o) {
            return o.toString();
        }

        @Override
        public Boolean deserialize(String s) {
            return Boolean.valueOf(s);
        }
    }

    // Java Converters ==================================================================
    public static class StringConverter extends BaseConverter<String> {
        public StringConverter() {
            super(String.class);
        }

        @Override
        public String serialize(String o) {
            return o;
        }

        @Override
        public String deserialize(String s) {
            return s;
        }
    }

    public static class ListConverter extends BaseConverter<java.util.List<?>> {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public ListConverter() {
            super((Class<java.util.List<?>>) (Class) java.util.List.class);
        }

        @Override
        public String serialize(java.util.List<?> l) {
            return JsonIC.toJson(l.stream().map(o -> getPool().serialize(o)).collect(Collectors.toList()));
        }

        @SuppressWarnings("unchecked")
        @Override
        public java.util.List<?> deserialize(String string) {
            List<Object> l = (List<Object>) JsonIC.fromJson(string);
            return l.map(o -> getPool().deserialize(o.toString())).collect(Collectors.toList());
        }
    }

    public static class SetConverter extends BaseConverter<java.util.Set<?>> {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public SetConverter() {
            super((Class<java.util.Set<?>>) (Class) java.util.Set.class);
        }

        @Override
        public String serialize(java.util.Set<?> s) {
            return JsonIC.toJson(s.stream().map(o -> getPool().serialize(o)).collect(Collectors.toList()));
        }

        @SuppressWarnings("unchecked")
        @Override
        public java.util.Set<?> deserialize(String string) {
            List<Object> l = (List<Object>) JsonIC.fromJson(string);
            return l.map(o -> getPool().deserialize(o.toString())).collect(Collectors.toSet());
        }
    }

    public static class MapConverter extends BaseConverter<java.util.Map<?, ?>> {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public MapConverter() {
            super((Class<java.util.Map<?, ?>>) (Class) java.util.Map.class);
        }

        @Override
        public String serialize(java.util.Map<?, ?> m, Object context) {
            return JsonIC.toJson(m.entrySet().stream().collect(Collectors.toMap(e -> getPool().serialize(e.getKey(), context), e -> getPool().serialize(e.getValue(), context))));
        }

        @Override
        public java.util.Map<?, ?> deserialize(String string) {
            Map<?, ?> m = (Map<?, ?>) JsonIC.fromJson(string);
            return m.collect(Collectors.toMap(e -> getPool().deserialize(e.getKey().toString()), e -> getPool().deserialize(e.getValue().toString())));
        }
    }

    public static class BigIntegerConverter extends BaseConverter<BigInteger> {
        public BigIntegerConverter() {
            super(BigInteger.class);
        }

        @Override
        public String serialize(BigInteger o) {
            return o.toString();
        }

        @Override
        public BigInteger deserialize(String s) {
            return new BigInteger(s);
        }
    }

    public static class BigDecimalConverter extends BaseConverter<BigDecimal> {
        public BigDecimalConverter() {
            super(BigDecimal.class);
        }

        @Override
        public String serialize(BigDecimal o) {
            return o.toString();
        }

        @Override
        public BigDecimal deserialize(String s) {
            return new BigDecimal(s);
        }
    }

    // Immutable Collections Converters =================================================
    public static class ImmutableListConverter extends BaseConverter<List<?>> {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public ImmutableListConverter() {
            super((Class<List<?>>) (Class) List.class, "immutableList");
        }

        @Override
        public String serialize(List<?> l) {
            return JsonIC.toJson(l.map(o -> getPool().serialize(o)).collect(Collectors.toList()));
        }

        @SuppressWarnings("unchecked")
        @Override
        public List<?> deserialize(String string) {
            List<Object> l = (List<Object>) JsonIC.fromJson(string);
            return l.map(e -> getPool().deserialize(e.toString())).toList();
        }
    }

    public static class ImmutableSetConverter extends BaseConverter<Set<?>> {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public ImmutableSetConverter() {
            super((Class<Set<?>>) (Class) Set.class, "immutableSet");
        }

        @Override
        public String serialize(Set<?> s) {
            return JsonIC.toJson(s.map(o -> getPool().serialize(o)).collect(Collectors.toList()));
        }

        @SuppressWarnings("unchecked")
        @Override
        public Set<?> deserialize(String string) {
            List<Object> l = (List<Object>) JsonIC.fromJson(string);
            return l.map(e -> getPool().deserialize(e.toString())).toSet();
        }
    }

    public static class ImmutableMapConverter extends BaseConverter<Map<?, ?>> {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public ImmutableMapConverter() {
            super((Class<Map<?, ?>>) (Class) Map.class, "immutableMap");
        }

        @Override
        public String serialize(Map<?, ?> m, Object context) {
            return JsonIC.toJson(m.collect(Collectors.toMap(e -> getPool().serialize(e.getKey(), context), e -> getPool().serialize(e.getValue(), context))));
        }

        @Override
        public Map<?, ?> deserialize(String string) {
            Map<?, ?> m = (Map<?, ?>) JsonIC.fromJson(string);
            return m.toMap(e -> Entry.of(getPool().deserialize(e.getKey().toString()), getPool().deserialize(e.getValue().toString())));
        }
    }
}
