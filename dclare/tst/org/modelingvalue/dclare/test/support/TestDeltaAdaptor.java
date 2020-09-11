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

package org.modelingvalue.dclare.test.support;

import static org.modelingvalue.collections.util.TraceTimer.*;
import static org.modelingvalue.dclare.sync.SerializationHelper.*;

import java.util.function.*;

import org.modelingvalue.collections.*;
import org.modelingvalue.collections.util.*;
import org.modelingvalue.dclare.*;
import org.modelingvalue.dclare.sync.*;
import org.modelingvalue.dclare.sync.converter.*;

@SuppressWarnings("rawtypes")
public class TestDeltaAdaptor extends DeltaAdaptor<String> {
    private static final boolean TRACE = false;

    public TestDeltaAdaptor(String name, UniverseTransaction tx, Predicate<Object> objectFilter, Predicate<Setable> setableFilter, ConvertJson converter) {
        super(name, tx, objectFilter, setableFilter, converter);
        CommunicationHelper.add(getAdaptorDaemon());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public String serializeMutable(Mutable value) {
        return encodeWithLength(((TestClass) value.dClass()).serializeClass(), ((TestObject) value).serialize());
    }

    @Override
    public String serializeSetable(Setable value) {
        return value.toString();
    }

    @Override
    public Mutable deserializeMutable(String s) {
        String[] parts = decodeFromLength(s, 2);
        return TestObject.of(parts[1], TestClass.existing(parts[0]));
    }

    @Override
    public Setable deserializeSetable(String s) {
        return TestObserved.existing(s);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    protected void queueDelta(State pre, State post, Boolean last) {
        traceDiffHandler(pre, post);
        super.queueDelta(pre, post, last);
    }

    private void traceDiffHandler(State pre, State post) {
        if (TRACE) {
            try {
                synchronized (TestDeltaAdaptor.class) {
                    pre.diff(post, x -> true, x -> true).forEach(e -> {
                        Mutable mutable = (Mutable) e.getKey();

                        Map<Setable, Pair<Object, Object>> map = e.getValue();
                        traceLog("                             - %-30s(%s):", mutable, mutable.getClass().getName());
                        map.forEach((Setable s, Pair<Object, Object> p) -> {
                            traceLog("                                 - %-26s(%s) ", s, s.getClass().getName());
                            traceLog("                                     < %-22s(%s) ", p.a(), p.a() == null ? "<null>" : p.a().getClass().getName());
                            traceLog("                                     > %-22s(%s) ", p.b(), p.b() == null ? "<null>" : p.b().getClass().getName());
                        });
                    });
                    TraceTimer.dumpLogs();
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void applyOneDelta(Mutable mutable, Setable prop, Object value) {
        traceApplyOneDiff(mutable, prop, value);
        super.applyOneDelta(mutable, prop, value);
    }

    @SuppressWarnings("unchecked")
    private void traceApplyOneDiff(Mutable mutable, Setable prop, Object value) {
        if (TRACE) {
            try {
                synchronized (TestDeltaAdaptor.class) {
                    traceLog("APPLY delta\n"
                                    + "  mutable  = %-50s (%s)\n"
                                    + "  prop     = %-50s (%s)\n"
                                    + "  currValue= %-50s (%s)\n"
                                    + "  newValue = %-50s (%s)",
                            mutable, mutable == null ? "" : mutable.getClass().getName(),
                            prop, prop.getClass().getName(),
                            prop.get(mutable), prop.getClass().getName(),
                            value, value == null ? "" : value.getClass().getName()
                    );
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }
}
