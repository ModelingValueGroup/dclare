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

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;

import java.util.Comparator;
import java.util.stream.Collectors;

public class SerialisationPool {
    public static final boolean TRACE_SERIALIZATION = Boolean.getBoolean("TRACE_SERIALIZATION");

    @SuppressWarnings("unused")
    public interface Converter<T> {
        char DELIMITER = ':';

        default String serialize(T t, Object context) {
            return serialize(t);
        }

        default String serialize(T t) {
            throw new IllegalArgumentException("deserialize(String) should be implemented in " + getClass().getSimpleName());
        }

        default T deserialize(String s, Object context) {
            return deserialize(s);
        }

        default T deserialize(String s) {
            throw new IllegalArgumentException("deserialize(String) should be implemented in " + getClass().getSimpleName());
        }

        Class<? extends T> getClazz();

        default String getPrefix() {
            return getClazz().getSimpleName();
        }

        default void setPool(SerialisationPool serialisationPool) {
        }

        default SerialisationPool getPool() {
            return null;
        }
    }

    public abstract static class BaseConverter<T> implements Converter<T> {

        private final Class<T>    clazz;
        private final String      prefix;
        private SerialisationPool pool;

        protected BaseConverter(Class<T> clazz) {
            this(clazz, Util.PREFIX_MAP.getOrDefault(clazz, clazz.getSimpleName()));
        }

        protected BaseConverter(Class<T> clazz, String prefix) {
            this.clazz = clazz;
            this.prefix = prefix;
        }

        @Override
        public void setPool(SerialisationPool serialisationPool) {
            this.pool = serialisationPool;
        }

        @Override
        public SerialisationPool getPool() {
            return pool;
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

    private final Map<String, Converter<?>>   deserialiseMap;
    private final Map<Class<?>, Converter<?>> serializeMap;
    private Map<Class<?>, Converter<?>>       additionalMappingsCache = Map.of();

    public SerialisationPool(Converter<?>... converters) {
        this(Collection.of(converters));
    }

    public SerialisationPool(Collection<Converter<?>> converters) {
        List<Converter<?>> conv = converters.toList();
        sanityCheck(conv);
        conv.forEach(c -> c.setPool(this));
        deserialiseMap = conv.toMap(c -> Entry.of(c.getPrefix(), c));
        serializeMap = conv.toMap(c -> Entry.of(c.getClazz(), c));
    }

    @SuppressWarnings("DataFlowIssue")
    private void sanityCheck(List<Converter<?>> conv) {
        if (TRACE_SERIALIZATION) {
            System.err.println("serialisation-pool vacabulary:");
            int maxLength = conv.mapToInt(c -> c.getClazz().getName().length()).max().orElse(0);
            conv.sequential().sorted(Comparator.comparing(a -> a.getClazz().getName())).forEach(c -> System.err.printf("  - %-" + maxLength + "s '%s%s'\n", c.getClazz().getName(), c.getPrefix(), Converter.DELIMITER));
        }
        List<List<String>> doublePrefixes = Collection.of(conv.map(Converter::getPrefix).collect(Collectors.groupingBy(x -> x)).values().stream().filter(l -> l.size() != 1).map(List::of)).toList();
        if (!doublePrefixes.isEmpty()) {
            throw problem("a SerialisationPool can not hold Converters with the same prefix: " + doublePrefixes.map(l -> l.get(0)).collect(Collectors.toList()));
        }
        // NB: keep this next var separate to avoid java compiler error:
        Collection<List<? extends Class<?>>> avoidCompilerError = Collection.of(conv.map(Converter::getClazz).collect(Collectors.groupingBy(x -> x)).values().stream().filter(l -> l.size() != 1).map(List::of));
        List<List<? extends Class<?>>> doubleClazzes = avoidCompilerError.toList();
        if (!doubleClazzes.isEmpty()) {
            throw problem("a SerialisationPool can not hold Converters with the same class: " + doubleClazzes.map(l -> l.get(0)).collect(Collectors.toList()));
        }
    }

    @SuppressWarnings("unused")
    public <T> boolean canConvert(Class<T> cls) {
        return getConverterFor(cls)!=null;
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
                    result = getValue(e);
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
        if (string == null || string.isBlank()) {
            if (TRACE_SERIALIZATION) {
                System.err.printf("[DESERIALIZE] %-25s: %-25s -> <null>\n", context, string);
            }
            return null;
        }
        int i = string.indexOf(Converter.DELIMITER);
        String prefix = i < 0 ? string : string.substring(0, i);
        String rest = i < 0 ? null : string.substring(i + 1);
        Converter<?> converter = deserialiseMap.get(prefix);
        if (converter == null) {
            throw problem("[DESERIALIZE] " + ("missing converter for '" + prefix + "' in \"" + string + "\" (no deserialisation possible)"));
        }
        Object value = converter.deserialize(rest, context);
        if (TRACE_SERIALIZATION) {
            System.err.println("[DESERIALIZE] (" + prefix + "," + rest + ") -> " + value);
        }
        return value;
    }

    @SuppressWarnings({"unused", "unchecked"})
    public <T> boolean canSerialize(T o) {
        return o == null || getConverterFor((Class<T>) o.getClass()) != null;
    }

    public <T> String serialize(T o) {
        return serialize(o, null);
    }

    public <T> String serialize(T o, Object context) {
        if (o == null) {
            if (TRACE_SERIALIZATION) {
                System.err.printf("[  SERIALIZE] %-25s: <null> -> <null>\n", context);
            }
            return null;
        }
        @SuppressWarnings("unchecked")
        Converter<T> converter = getConverterFor((Class<T>) o.getClass());
        String string;
        if (converter == null) {
            throw problem(String.format("[  SERIALIZE] %-25s: no converter available for class %s\n", context, o.getClass().getSimpleName()));
        }
        string = converter.getPrefix() + Converter.DELIMITER + converter.serialize(o, context);
        if (TRACE_SERIALIZATION) {
            System.err.printf("[  SERIALIZE] %-25s: %-25s -> \"%s\"\n", context == null ? "<root>" : context.toString(), o, string);
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

    private static IllegalArgumentException problem(String msg) {
        if (TRACE_SERIALIZATION) {
            System.err.println(msg);
        }
        return new IllegalArgumentException(msg);
    }
}
