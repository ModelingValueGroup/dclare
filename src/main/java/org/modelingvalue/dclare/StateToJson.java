//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2026 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
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

package org.modelingvalue.dclare;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Predicate;

import org.modelingvalue.collections.List;
import org.modelingvalue.collections.QualifiedSet;
import org.modelingvalue.collections.Set;
import org.modelingvalue.dclare.sync.Util;
import org.modelingvalue.json.ToJson;

@SuppressWarnings({"rawtypes", "unused"})
public class StateToJson extends ToJson {
    public static final String                             ID_FIELD_NAME     = "@id";
    public static final String                             ID_REF_FIELD_NAME = "@idref";
    public static final String                             NAME_FIELD_NAME   = "name";
    private static final Comparator<Entry<Object, Object>> FIELD_SORTER      = ((Comparator<Entry<Object, Object>>) (e1, e2) -> isNameOrId(e1) ? -1 : isNameOrId(e2) ? +1 : 0).thenComparing(e -> e.getKey().toString());

    private static boolean isNameOrId(Entry<Object, Object> e) {
        return isNameOrId(e.getKey());
    }

    private static boolean isNameOrId(Object s) {
        return s.equals(NAME_FIELD_NAME) || s.equals(ID_FIELD_NAME);
    }

    private final Comparator<Object> setSorter = Comparator.comparing(o -> o instanceof Mutable ? getId((Mutable) o) : "" + o);
    private final State              state;

    public StateToJson(Mutable m, State state) {
        super(m);
        this.state = state;
    }

    public State getState() {
        return state;
    }

    @Override
    public String render() {
        return state.get(super::render);
    }

    @Override
    protected boolean isMapType(Object o) {
        return o instanceof Mutable || o instanceof QualifiedSet<?, ?> || o instanceof Map<?, ?>;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Iterator<Object> getArrayIterator(Object o) {
        if (o instanceof Set<?>) {
            return (Iterator<Object>) ((Set<?>) o).sorted(setSorter).asList().iterator();
        } else {
            return super.getArrayIterator(o);
        }
    }

    @Override
    protected Iterator<Entry<Object, Object>> getMapIterator(Object o) {
        Map<Object, Object> m;
        if (o instanceof Mutable mutable) {
            m = getMapIterator_Mutable(mutable);
        } else if (o instanceof Map map) {
            m = getMapIterator_Map(map);
        } else if (o instanceof QualifiedSet qualifiedSet) {
            m = getMapIterator_QualifiedSet(qualifiedSet);
        } else {
            throw new RuntimeException("this should not be reachable");
        }
        return m.entrySet().stream().sorted(FIELD_SORTER).iterator();
    }

    protected Map<Object, Object> getMapIterator_Mutable(Mutable mutable) {
        Predicate<Setable> setableFilter = getSetableFilter();
        return mutable.dClass().dSetables() //
                .map((Setable setable) -> {
                    if (!setableFilter.test(setable)) {
                        return null;
                    }
                    @SuppressWarnings("unchecked")
                    Object value = state.getRaw(mutable, setable);
                    @SuppressWarnings("unchecked")
                    Object defValue = setable.getDefault(mutable);
                    if (Objects.equals(value, defValue)) {
                        return null;
                    }
                    return org.modelingvalue.collections.Entry.of((Object) renderTag(setable), renderValue(mutable, setable, value));
                }) //
                .filter(Objects::nonNull) //
                .asMap(e -> e).toMutable();
    }

    @SuppressWarnings("unchecked")
    protected Map<Object, Object> getMapIterator_QualifiedSet(QualifiedSet qualifiedSet) {
        return qualifiedSet.toKeys().asMap(k -> org.modelingvalue.collections.Entry.of(k, qualifiedSet.get(k))).toMutable();
    }

    @SuppressWarnings("unchecked")
    protected Map<Object, Object> getMapIterator_Map(Map map) {
        return map;
    }

    protected Predicate<Setable> getSetableFilter() {
        return ((Predicate<Setable>) Setable::isPlumbing).negate();
    }

    protected String getId(Mutable m) {
        return m.getClass().getName() + "@" + m.hashCode();
    }

    protected String renderTag(Setable s) {
        return s.id().toString();
    }

    protected Object renderValue(Object o, Setable setable, Object value) {
        return setable.containment() ? renderContainmentValue(o, setable, value) : renderReferenceValue(o, setable, value);
    }

    protected Object renderContainmentValue(Object o, Setable setable, Object value) {
        return value;
    }

    @SuppressWarnings("unchecked")
    protected Object renderReferenceValue(Object o, Setable setable, Object value) {
        if (value == null) {
            value = "@@ERROR@NULL_REF@@";
        } else if (value instanceof Mutable) {
            value = makeRef((Mutable) value);
        } else if (value instanceof List) {
            value = ((List) value).map(v -> v instanceof Mutable ? makeRef((Mutable) v) : v).asList();
        } else if (value instanceof Set) {
            value = ((Set) value).map(v -> v instanceof Mutable ? makeRef((Mutable) v) : v).sorted(setSorter).asList();
        } else if (!Util.PREFIX_MAP.containsKey(value.getClass())) {
            value = "@@ERROR@REF_TO_UNKNOWN_TYPE@" + value.getClass().getSimpleName() + "@" + value + "@@";
        }
        return value;
    }

    protected Map<String, String> makeRef(Mutable mutable) {
        Map<String, String> map = new HashMap<>();
        map.put(ID_REF_FIELD_NAME, getId(mutable));
        return map;
    }
}
