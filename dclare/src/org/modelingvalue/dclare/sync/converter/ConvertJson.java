package org.modelingvalue.dclare.sync.converter;

import java.util.*;

import org.json.simple.*;
import org.json.simple.parser.*;
import org.modelingvalue.dclare.ex.*;

public class ConvertJson implements Converter<Map<String, Map<String, String>>, String> {
    @Override
    public String convertForward(Map<String, Map<String, String>> o) {
        return JSONValue.toJSONString(o);
    }

    @Override
    public Map<String, Map<String, String>> convertBackward(String s) {
        try {
            return throwOnIncompatibleStructure(new JSONParser().parse(s));
        } catch (ParseException e) {
            throw new NotDeserializableError(e);
        }
    }

    private Map<String, Map<String, String>> throwOnIncompatibleStructure(Object o) {
        if (o != null) {
            if (!(o instanceof Map<?, ?>)) {
                throw new NotDeserializableError("root is not a Map but a " + o.getClass().getTypeName());
            }
            ((Map<?, ?>) o).forEach((k0, v0) -> {
                if (!(k0 instanceof String)) {
                    throw new NotDeserializableError("root map key is not a String: " + k0);
                }
                if (!(v0 instanceof Map)) {
                    throw new NotDeserializableError("root map value [" + k0 + "] is not a Map but a " + v0.getClass().getTypeName());
                }
                ((Map<?, ?>) v0).forEach((k1, v1) -> {
                    if (!(k1 instanceof String)) {
                        throw new NotDeserializableError("sub map key [" + k0 + "] is not a String: " + k1);
                    }
                    if (!(v1 instanceof String)) {
                        throw new NotDeserializableError("sub map value [" + k0 + "," + k1 + "] is not a String: " + v1);
                    }
                });
            });
        }
        //noinspection unchecked
        return (Map<String, Map<String, String>>) o;
    }
}
