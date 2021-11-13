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

import org.modelingvalue.dclare.*;
import org.modelingvalue.dclare.sync.SerialisationPool.*;

import java.util.*;
import java.util.stream.*;

@SuppressWarnings("unused")
public abstract class SerializationHelperWithPool<C extends MutableClass, M extends Mutable, S extends Setable<M, ?>> implements SerializationHelper<C, M, S> {
    protected final SerialisationPool serialisationPool;

    public SerializationHelperWithPool(Converter<?>... converters) {
        serialisationPool = new SerialisationPool(converters);
    }

    public SerializationHelperWithPool(List<Converter<?>> converters) {
        serialisationPool = new SerialisationPool(converters);
    }

    public SerializationHelperWithPool(Stream<Converter<?>> converters) {
        serialisationPool = new SerialisationPool(converters);
    }

    //==================================================================================================================
    @Override
    public String serializeSetable(S setable) {
        return serialisationPool.serialize(setable);
    }

    @Override
    public String serializeMutable(M mutable) {
        return serialisationPool.serialize(mutable);
    }

    @Override
    public Object serializeValue(S setable, Object value) {
        return serialisationPool.serialize(value);
    }

    //==================================================================================================================
    @SuppressWarnings("unchecked")
    @Override
    public S deserializeSetable(C clazz, String s) {
        return (S) serialisationPool.deserialize(s, clazz);
    }

    @SuppressWarnings("unchecked")
    @Override
    public M deserializeMutable(String string) {
        return (M) serialisationPool.deserialize(string);
    }

    @Override
    public Object deserializeValue(S setable, Object value) {
        return serialisationPool.deserialize((String) value);
    }
}
