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

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.*;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public class SerialisationPool {
    public static final boolean TRACE        = Boolean.getBoolean("TRACE_SERIALIZATION");
    public static final String  ERROR_PREFIX = "__ERROR__";

    public interface Converter<T> {
        char DELIMITER = ':';

        String serialize(Object context, T t);

        T deserialize(Object context, String s);

        Class<? extends T> getClazz();

        default String getPrefix() {
            return getClazz().getSimpleName().toLowerCase();
        }

        default void setPool(@SuppressWarnings("unused") SerialisationPool serialisationPool) {
        }
    }

    public abstract static class BaseConverter<T> implements Converter<T> {
        private final Class<T>          clazz;
        private final String            prefix;
        protected     SerialisationPool serialisationPool;

        protected BaseConverter(Class<T> clazz) {
            this(clazz, Util.PREFIX_MAP.getOrDefault(clazz, clazz.getSimpleName().toLowerCase()));
        }

        protected BaseConverter(Class<T> clazz, String prefix) {
            this.clazz  = clazz;
            this.prefix = prefix;
        }

        @Override
        public void setPool(SerialisationPool serialisationPool) {
            this.serialisationPool = serialisationPool;
        }

        @Override
        public String getPrefix() {
            return prefix;
        }

        @Override
        public Class<T> getClazz() {
            return clazz;
        }
    }

    public static SerialisationPool of(Converter<?>... converters) {
        return new SerialisationPool(converters);
    }

    private final Map<String, Converter<?>>   deserialiseMap;
    private final Map<Class<?>, Converter<?>> serializeMap;
    private       Map<Class<?>, Converter<?>> additionalMappingsCache = Map.of();

    public SerialisationPool(Converter<?>... converters) {
        List<Converter<?>> conv = List.of(converters);
        sanityCheck(conv);
        conv.forEach(c -> c.setPool(this));
        deserialiseMap = conv.toMap(c -> Entry.of(c.getPrefix(), c));
        serializeMap   = conv.toMap(c -> Entry.of(c.getClazz(), c));
    }

    private void sanityCheck(List<Converter<?>> conv) {
        List<List<String>> doublePrefixes = Collection.of(
                conv.map(Converter::getPrefix)
                        .collect(Collectors.groupingBy(x -> x))
                        .values()
                        .stream()
                        .filter(l -> l.size() != 1)
                        .map(List::of)
        ).toList();
        if (!doublePrefixes.isEmpty()) {
            throw new IllegalArgumentException("a SerialisationPool can not hold Converters with the same prefix: " + doublePrefixes);
        }
        // NB: keep this next var separate to avoid java compiler error:
        Collection<List<? extends Class<?>>> avoidCompilerError = Collection.of(
                conv
                        .map(Converter::getClazz)
                        .collect(Collectors.groupingBy(x -> x))
                        .values()
                        .stream()
                        .filter(l -> l.size() != 1)
                        .map(List::of)
        );
        List<List<? extends Class<?>>> doubleClazzes = avoidCompilerError.toList();
        if (!doubleClazzes.isEmpty()) {
            throw new IllegalArgumentException("a SerialisationPool can not hold Converters with the same class: " + doubleClazzes);
        }
        List<String> wrongPrefix = conv.filter(c -> c.getPrefix().equals(ERROR_PREFIX)).map(c -> c.getClazz().getSimpleName()).toList();
        if (!wrongPrefix.isEmpty()) {
            throw new IllegalArgumentException("converters can not have a prefix " + ERROR_PREFIX);
        }
    }

    private SerialisationPool add(Converter<?>... converters) {
        return new SerialisationPool(Stream.concat(deserialiseMap.toValues(), Arrays.stream(converters)).toArray((IntFunction<Converter<?>[]>) Converter[]::new));
    }

    private <T> Converter<T> getConverterFor(Class<T> cls) {
        Converter<T> result = getConverter(cls, serializeMap);
        if (result == null) {
            result = handleCache(cls);
        }
        return result;
    }

    private <T> Converter<T> handleCache(Class<T> clazz) {
        Converter<T> result = getConverter(clazz, additionalMappingsCache);
        if (result == null) {
            Class<?> bestUntilNow = null;
            for (Entry<Class<?>, Converter<?>> e : serializeMap.addAll(additionalMappingsCache)) {
                Class<?> key = e.getKey();
                if (key.isAssignableFrom(clazz) && (bestUntilNow == null || bestUntilNow.isAssignableFrom(key))) {
                    bestUntilNow = key;
                    result       = getValue(e);
                }
            }
            additionalMappingsCache = additionalMappingsCache.put(Entry.of(clazz, result));
        }
        return result;
    }

    public Object deserialize(String string) {
        return deserialize(string, null);
    }

    public Object deserialize(String string, Object context) {
        if (string == null) {
            return null;
        }
        int i = string.indexOf(Converter.DELIMITER);
        if (0 < i) {
            String       prefix    = string.substring(0, i);
            String       rest      = string.substring(i + 1);
            Converter<?> converter = deserialiseMap.get(prefix);
            if (converter != null) {
                Object value = converter.deserialize(context, rest);
                if (TRACE) {
                    System.err.println("[DESERIALIZE] (" + prefix + "," + rest + ") -> " + value);
                }
                return value;
            }
        }
        if (TRACE) {
            System.err.println("[DESERIALIZE] no deserialisation possible for " + string);
        }
        return null;
    }

    public <T> String serialize(T o) {
        return serialize(o, null);
    }

    public <T> String serialize(T o, Object context) {
        @SuppressWarnings("unchecked")
        Converter<T> serializer = getConverterFor((Class<T>) o.getClass());
        String string;
        if (serializer == null) {
            string = ERROR_PREFIX + Converter.DELIMITER + "no Converter for " + o.getClass().getSimpleName();
        } else {
            string = serializer.getPrefix() + Converter.DELIMITER + serializer.serialize(context, o);
        }
        if (TRACE) {
            System.err.println("[  SERIALIZE] " + o + " -> " + string);
        }
        return string;
    }


    @SuppressWarnings("unchecked")
    private static <T> Converter<T> getConverter(Class<T> cls, Map<Class<?>, Converter<?>> map) {
        return (Converter<T>) map.get(cls);
    }

    @SuppressWarnings("unchecked")
    private static <T> Converter<T> getValue(Entry<Class<?>, Converter<?>> e) {
        return (Converter<T>) e.getValue();
    }
}
