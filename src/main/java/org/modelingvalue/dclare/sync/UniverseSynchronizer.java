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

import java.util.function.Predicate;

import org.modelingvalue.dclare.*;

@SuppressWarnings({"rawtypes", "unused"})
public interface UniverseSynchronizer<M extends Mutable> {

    String serializeDelta(State pre, State post);

    void deserializeDelta(String delta);

    /////////////////////////////////
    Predicate<Mutable> mutableFilter();

    Predicate<Setable> setableFilter();

    /////////////////////////////////
    String serializeClass(MutableClass clazz);

    String serializeSetable(Setable<M, ?> setable);

    String serializeMutable(M mutable);

    <V> String serializeValue(Setable<M, V> setable, V value);

    /////////////////////////////////
    MutableClass deserializeClass(String s);

    Setable<M, ?> deserializeSetable(MutableClass clazz, String s);

    M deserializeMutable(MutableClass clazz, String s);

    <V> V deserializeValue(Setable<M, V> setable, String s);
    /////////////////////////////////
}
