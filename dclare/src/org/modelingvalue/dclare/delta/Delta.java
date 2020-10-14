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

package org.modelingvalue.dclare.delta;

import static org.modelingvalue.collections.util.TraceTimer.*;

import java.io.Serializable;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.collections.util.QuadConsumer;
import org.modelingvalue.dclare.Setable;

@SuppressWarnings("rawtypes")
public class Delta implements Serializable {
    static final         long    serialVersionUID = 6094717687064448915L;
    private static final boolean TRACE_APPLIES    = false;

    private final Map<Object, Map<Setable, Pair<Object, Object>>> changes;

    public Delta(Collection<Entry<Object, Map<Setable, Pair<Object, Object>>>> diff) {
        changes = diff.toMap(e -> e);
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public void apply() {
        forEach((prop, obj, oldValue, newValue) -> {
            if (TRACE_APPLIES) {
                traceLog("APPLY delta\n"
                                + "  obj      = %-50s (%s)\n"
                                + "  prop     = %-50s (%s)\n"
                                + "  currValue= %-50s (%s)\n"
                                + "  oldValue = %-50s (%s)\n"
                                + "  newValue = %-50s (%s)",
                        obj, obj == null ? "" : obj.getClass().getName(),
                        prop, prop.getClass().getName(),
                        prop.get(obj), prop.getClass().getName(),
                        oldValue, oldValue == null ? "" : oldValue.getClass().getName(),
                        newValue, newValue == null ? "" : newValue.getClass().getName()
                );
            }
            prop.set(obj, newValue);
        });
    }

    public void forEach(QuadConsumer<Setable, Object, Object, Object> f) {
        changes.forEach(e ->
                e.getValue().forEach(sv ->
                        f.accept(sv.getKey(), e.getKey(), sv.getValue().a(), sv.getValue().b())));
    }
}
