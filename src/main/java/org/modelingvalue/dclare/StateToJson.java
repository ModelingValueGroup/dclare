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

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.QualifiedSet;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.json.ToJson;

import java.util.AbstractMap.SimpleEntry;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.function.Predicate;

@SuppressWarnings({"rawtypes", "unused"})
public abstract class StateToJson extends ToJson {
    private static final String                            ID_FIELD_NAME     = "$id";
    private static final String                            ID_REF_FIELD_NAME = "$idref";
    private static final String                            NAME_FIELD_NAME   = "name";
    private static final Comparator<Entry<Object, Object>> FIELD_SORTER      = ((Comparator<Entry<Object, Object>>) (e1, e2) -> isNameOrId(e1) ? -1 : isNameOrId(e2) ? +1 : 0).thenComparing(e -> e.getKey().toString());

    private static boolean isNameOrId(Entry<Object, Object> e) {
        return e.getKey().equals(NAME_FIELD_NAME) || e.getKey().equals(ID_FIELD_NAME);
    }

    private final Comparator<Object> setSorter = Comparator.comparing(o -> o instanceof Mutable ? getId((Mutable) o) : "" + o);
    private final State              state;

    public StateToJson(Mutable o, State state) {
        super(o);
        this.state = state;
    }

    protected Predicate<Setable> getSetableFilter() {
        return ((Predicate<Setable>) Setable::isPlumbing).negate();
    }

    protected String renderSetable(Setable s) {
        return s.id().toString();
    }

    protected abstract String getId(Mutable m);

    @Override
    protected boolean isMapType(Object o) {
        return o instanceof Mutable || o instanceof QualifiedSet;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Iterator<Entry<Object, Object>> getMapIterator(Object o) {
        if (o instanceof Mutable) {
            Mutable                           mutable = (Mutable) o;
            Collection<Entry<Object, Object>> idEntry = Collection.of(new SimpleEntry<>(ID_FIELD_NAME, getId(mutable)));
            Collection<Entry<Object, Object>> rest = mutable.dClass().dSetables().filter(getSetableFilter()).map(setable -> Pair.of(setable, state.get(mutable, (Setable) setable))).filter(pair -> pair.b() != null).map(pair -> {
                Setable<? extends Mutable, ?> setable = pair.a();
                Object                        value   = pair.b();
                if (setable.containment() || value.getClass().isPrimitive() || value instanceof String || value instanceof QualifiedSet) {
                    return pair;
                } else if (value instanceof Mutable) {
                    Mutable mutableValue = (Mutable) value;
                    return Pair.of(setable, makeRef(mutableValue));
                } else if (value instanceof List) {
                    return Pair.of(setable, ((List) value).map(v -> v instanceof Mutable ? makeRef((Mutable) v) : v));
                } else if (value instanceof Set) {
                    return Pair.of(setable, ((Set) value).map(v -> v instanceof Mutable ? makeRef((Mutable) v) : v).sorted(FIELD_SORTER));
                } else {
                    return Pair.of(setable, "@@ERROR-UNKNOWN-VALUE@" + value + "@" + value.getClass().getSimpleName() + "@@");
                }
            }).map(pair -> new SimpleEntry<>(renderSetable(pair.a()), pair.b()));
            return Collection.concat(idEntry, rest).sorted(FIELD_SORTER).iterator();
        }
        if (o instanceof QualifiedSet) {
            QualifiedSet<Object, Object> q = (QualifiedSet<Object, Object>) o;
            return q.toKeys().map(k -> (Entry<Object, Object>) new SimpleEntry<>(k, q.get(k))).sortedBy(e -> e.getKey().toString()).iterator();
        }
        throw new RuntimeException("this should not be reachable");
    }

    private QualifiedSet<String, String> makeRef(Mutable mutableValue) {
        return QualifiedSet.of(__ -> ID_REF_FIELD_NAME, getId(mutableValue));
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Iterator<Object> getArrayIterator(Object o) {
        if (o instanceof Set) {
            return (Iterator<Object>) ((Set) o).toList().sorted(setSorter).iterator();
        }
        return super.getArrayIterator(o);
    }
}
