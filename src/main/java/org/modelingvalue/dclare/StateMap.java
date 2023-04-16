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

package org.modelingvalue.dclare;

import org.modelingvalue.collections.DefaultMap;

import java.io.Serializable;
import java.util.Comparator;
import java.util.stream.Collectors;

@SuppressWarnings("rawtypes")
public class StateMap implements Serializable {
    public static final  DefaultMap<Setable, Object> EMPTY_SETABLES_MAP = DefaultMap.of(Getable::getDefault);
    public static final  StateMap                    EMPTY_STATE_MAP    = new StateMap(DefaultMap.of(o -> EMPTY_SETABLES_MAP));
    private static final long                        serialVersionUID   = -7537530409013376084L;

    private final DefaultMap<Object, DefaultMap<Setable, Object>> map;

    public StateMap(StateMap stateMap) {
        this(stateMap.map);
    }

    protected StateMap(DefaultMap<Object, DefaultMap<Setable, Object>> map) {
        this.map = map;
    }

    protected DefaultMap<Object, DefaultMap<Setable, Object>> map() {
        return map;
    }

    public StateMap getStateMap() {
        return new StateMap(map);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public String toString() {
        return map
                .sorted(Comparator.comparing(e2 -> e2.getKey().toString()))
                .map(e1 -> {
                    String val = e1.getValue()
                            .sorted(Comparator.comparing(e2 -> e2.getKey().toString()))
                            .map(e2 -> String.format("%-50s = %s", e2.getKey(), e2.getValue()))
                            .collect(Collectors.joining("\n    ", "    ", "\n"));
                    return String.format("%-50s =\n%s", e1.getKey().toString(), val);
                })
                .collect(Collectors.joining("\n"));
    }

    @SuppressWarnings("unchecked")
    public <O, T> T get(O obj, Setable<O, T> settable) {
        return (T) map.get(obj).get(settable);
    }

    public <O, T> StateMap clear(O obj, Setable<O, T> settable) {
        return new StateMap(map.put(obj, map.get(obj).removeKey(settable)));
    }
}
