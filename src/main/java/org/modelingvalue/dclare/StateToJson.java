//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2022 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

package org.modelingvalue.dclare;

import org.modelingvalue.collections.QualifiedSet;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.json.ToJson;

import java.util.AbstractMap.SimpleEntry;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.function.Predicate;

@SuppressWarnings({"rawtypes", "unused"})
public class StateToJson extends ToJson {
    private final State              state;
    private final Predicate<Setable> setableFilter;

    public static String toJson(Mutable o, State state, Predicate<Setable> setableFilter) {
        return new StateToJson(o, state, setableFilter).render();
    }

    public StateToJson(Mutable o, State state, Predicate<Setable> setableFilter) {
        super(o);
        this.state         = state;
        this.setableFilter = setableFilter != null ? setableFilter : Setable::isPlumbing;
    }

    @Override
    protected boolean isMapType(Object o) {
        return o instanceof Mutable || o instanceof QualifiedSet;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Iterator<Entry<Object, Object>> getMapIterator(Object o) {
        if (o instanceof Mutable) {
            Mutable m = (Mutable) o;
            return m.dClass().dSetables()
                    .filter(setableFilter)
                    .map(setable -> Pair.of(setable, state.get(m, (Setable) setable)))
                    .filter(pair -> pair.b() != null)
                    .map(pair -> (Entry<Object, Object>) new SimpleEntry<Object, Object>(pair.a().id().toString().substring(1), pair.b()))
                    .sortedBy(e -> e.getKey().toString())
                    .iterator();
        }
        if (o instanceof QualifiedSet) {
            QualifiedSet<Object, Object> q = (QualifiedSet<Object, Object>) o;
            return q.toKeys()
                    .map(k -> (Entry<Object, Object>) new SimpleEntry<>(k, q.get(k)))
                    .sortedBy(e -> e.getKey().toString())
                    .iterator();
        }
        throw new RuntimeException("this should not be reachable");
    }
}
