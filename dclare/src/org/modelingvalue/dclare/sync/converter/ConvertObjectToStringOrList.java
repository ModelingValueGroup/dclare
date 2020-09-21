package org.modelingvalue.dclare.sync.converter;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import org.modelingvalue.collections.util.*;
import org.modelingvalue.dclare.*;
import org.modelingvalue.dclare.sync.*;

public class ConvertObjectToStringOrList implements Converter<Object, Object> {
    private final SerializationHelper helper;

    public ConvertObjectToStringOrList(SerializationHelper helper) {
        this.helper = helper;
    }

    @Override
    public Object convertForward(Object o) {
        if (o == null) {
            return "null";
        }
        if (o instanceof String) {
            return "s" + o.toString();
        }
        if (o instanceof Byte) {
            return "B" + o.toString();
        }
        if (o instanceof Character) {
            return "C" + o.toString();
        }
        if (o instanceof Double) {
            return "D" + o.toString();
        }
        if (o instanceof Float) {
            return "F" + o.toString();
        }
        if (o instanceof Integer) {
            return "I" + o.toString();
        }
        if (o instanceof Long) {
            return "J" + o.toString();
        }
        if (o instanceof Short) {
            return "S" + o.toString();
        }
        if (o instanceof Boolean) {
            return "Z" + o.toString();
        }
        if (o instanceof Mutable) {
            return "m" + helper.serializeMutable((Mutable) o);
        }
        if (o instanceof Setable) {
            return "n" + helper.serializeSetable((Setable<?, ?>) o);
        }
        if (hasSerializationMethods(o)) {
            List<Object> l = new ArrayList<>();
            l.add(o.getClass().getName());
            invokeSerialize(o, new Serializer() {
                @Override
                public void writeObject(Object o1) {
                    l.add(convertForward(o1));
                }

                @Override
                public void writeInt(int i) {
                    l.add(i);
                }
            });
            return l;
        }
        if (o instanceof Serializable) {
            if (!(o instanceof LambdaReflection)) {
                System.err.println("TOMTOMTOM using java serialization on " + o.getClass().getName());//TOMTOMTOM
            }
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream oos = new ObjectOutputStream(bos)) {
                oos.writeObject(o);
                oos.flush();
                return "z" + Base64.getEncoder().encodeToString(bos.toByteArray());
            } catch (IOException e) {
                throw new NotSerializableError("class=" + o.getClass().getName(), e);
            }
        }
        throw new NotSerializableError("class=" + o.getClass().getName());
    }

    @Override
    public Object convertBackward(Object o) {
        if (o == null) {
            return null;
        } else if (o instanceof String) {
            String s = (String) o;

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
            case 'n':
                return helper.deserializeSetable(rest);
            case 'z':
                try {
                    try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(rest.getBytes())))) {
                        return ois.readObject();
                    }
                } catch (IOException | ClassNotFoundException e) {
                    throw new NotSerializableError("s=" + rest, e);
                }
            default:
                throw new NotDeserializableError("s='" + s + "'");
            }
        } else if (o instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> l = (List<Object>) o;
            AtomicInteger i = new AtomicInteger(1);
            return invokeDeserialize(getClassFromList(l), new Deserializer() {
                @Override
                public <X> X readObject() {
                    //noinspection unchecked
                    return (X) convertBackward(l.get(i.getAndIncrement()));
                }

                @Override
                public int readInt() {
                    return ((Number) l.get(i.getAndIncrement())).intValue();
                }
            });
        } else {
            throw new NotDeserializableError("s='" + o + "'");
        }
    }

    private static void invokeSerialize(Object value, Serializer s) {
        try {
            Objects.requireNonNull(getSerializeMethod(value)).invoke(value, s);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new NotSerializableError("class=" + value.getClass().getName(), e);
        }
    }

    private static Object invokeDeserialize(Class<?> clazz, Deserializer s) {
        try {
            return Objects.requireNonNull(getDeserializeMethod(clazz)).invoke(null, s);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new NotDeserializableError("class=" + clazz.getName(), e);
        }
    }

    private static boolean hasSerializationMethods(Object o) {
        return getSerializeMethod(o) != null && getDeserializeMethod(o) != null;
    }

    private static Method getDeserializeMethod(Object o) {
        return getDeserializeMethod(o.getClass());
    }

    private static Method getSerializeMethod(Object o) {
        return getSerializeMethod(o.getClass());
    }

    private static Method getSerializeMethod(Class<?> clazz) {
        try {
            Method m = clazz.getDeclaredMethod("serialize", Serializer.class);
            if (m.getReturnType() != void.class || Modifier.isStatic(m.getModifiers()) || !Modifier.isPrivate(m.getModifiers()) || Modifier.isAbstract(m.getModifiers())) {
                return null;
            }
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Method getDeserializeMethod(Class<?> clazz) {
        try {
            Method m = clazz.getDeclaredMethod("deserialize", Deserializer.class);
            if (m.getReturnType() != clazz || !Modifier.isStatic(m.getModifiers()) || !Modifier.isPrivate(m.getModifiers()) || Modifier.isAbstract(m.getModifiers())) {
                return null;
            }
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Class<?> getClassFromList(java.util.List<Object> l) {
        if (l.size() <= 1) {
            throw new NotDeserializableError("list has not enough elements (expecting >1) l='" + l + "'");
        }
        if (!(l.get(0) instanceof String)) {
            throw new NotDeserializableError("list should have a string as first element but has a " + l.get(0) + " l='" + l + "'");
        }
        try {
            return ConvertDeltaToStringDelta.class.getClassLoader().loadClass((String) l.get(0));
        } catch (ClassNotFoundException e) {
            throw new NotDeserializableError(e);
        }
    }
}
