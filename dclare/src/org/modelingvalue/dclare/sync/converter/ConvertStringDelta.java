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

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.*;
import org.modelingvalue.collections.util.*;
import org.modelingvalue.dclare.*;
import org.modelingvalue.dclare.sync.*;

@SuppressWarnings("rawtypes")
public class ConvertStringDelta implements Converter<Map<Object, Map<Setable, Pair<Object, Object>>>, java.util.Map<String, java.util.Map<String, Object>>> {
    private final SerializationHelper helper;

    public ConvertStringDelta(SerializationHelper helper) {
        this.helper = helper;
    }

    @Override
    public java.util.Map<String, java.util.Map<String, Object>> convertForward(Map<Object, Map<Setable, Pair<Object, Object>>> root) {
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
    public Map<Object, Map<Setable, Pair<Object, Object>>> convertBackward(java.util.Map<String, java.util.Map<String, Object>> root) {
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

    private Object serializeValue(Object value) {
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
        if (value instanceof ContainingCollection) {
            ContainingCollection<?> col = (ContainingCollection<?>) value;
            java.util.List<Object>  l   = new ArrayList<>();
            l.add(col.getClass().getName());
            col.doSerialize(new Serializer() {
                @Override
                public void writeObject(Object o) {
                    l.add(serializeValue(o));
                }

                @Override
                public void writeInt(int i) {
                    l.add(Integer.toString(i));
                }
            });
            return l;
        }
        if (value instanceof Serializable) {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream    oos = new ObjectOutputStream(bos);
                oos.writeObject(value);
                oos.close();
                byte[] bytes = bos.toByteArray();
                String vv    = "z" + Base64.getEncoder().encodeToString(bytes);
                System.err.println("TOMTOMTOM ser = " + vv.length());
                return vv;
            } catch (IOException e) {
                throw new NotSerializableError("class=" + value.getClass().getName());
            }
        }
        throw new NotSerializableError("class=" + value.getClass().getName());
    }

    private Object deserializeValue(Object s_l) {
        if (s_l == null) {
            return null;
        } else if (s_l instanceof String) {
            String s = (String) s_l;

            if (s.isEmpty() || s.equals("null")) {
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
            case 'z':
                try {
                    ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(rest.getBytes()));
                    ObjectInputStream    ois = new ObjectInputStream(bis);
                    Object               o   = ois.readObject();
                    ois.close();
                    return o;
                } catch (IOException | ClassNotFoundException e) {
                    throw new NotSerializableError("s=" + rest);
                }
            default:
                throw new NotDeserializableError("s='" + s + "'");
            }
        } else if (s_l instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<Object> l = (java.util.List<Object>) s_l;
            ContainingCollection<Object> col = makeContColl((String) l.get(0));

            AtomicInteger i = new AtomicInteger(1);
            col.doDeserialize(new Deserializer() {
                @Override
                public Object readObject() {
                    return deserializeValue(l.get(i.getAndIncrement()));
                }

                @Override
                public int readInt() {
                    return Integer.parseInt((String) l.get(i.getAndIncrement()));
                }
            });


            return col;
        } else {
            throw new NotDeserializableError("s='" + s_l + "'");
        }
    }

    @SuppressWarnings("unchecked")
    private ContainingCollection<Object> makeContColl(String s) {
        try {
            Class<? extends ContainingCollection>       clazz = (Class<? extends ContainingCollection>) getClass().getClassLoader().loadClass(s);
            Constructor<? extends ContainingCollection> conzt = clazz.getDeclaredConstructor(Object.class);
            conzt.setAccessible(true);
            return (ContainingCollection<Object>) conzt.newInstance(new Object[]{null});
        } catch (IllegalAccessException | ClassNotFoundException | NoSuchMethodException | InstantiationException | InvocationTargetException e) {
            throw new NotDeserializableError("s='" + s + "'", e);
        }
    }
}
