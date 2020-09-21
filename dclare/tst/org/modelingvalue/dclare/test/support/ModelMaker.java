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

import static org.junit.jupiter.api.Assertions.*;
import static org.modelingvalue.collections.util.TraceTimer.*;

import java.util.*;
import java.util.stream.*;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.*;
import org.modelingvalue.collections.util.*;
import org.modelingvalue.collections.util.ContextThread.*;
import org.modelingvalue.dclare.Observer;
import org.modelingvalue.dclare.*;

@SuppressWarnings({"FieldCanBeLocal"})
public class ModelMaker {
    // TODO: need to fix the bug and remove this workaround:
    public static final  boolean                                                   BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER = true;
    public static final  int                                                       SOURCE_DEFAULT                           = 11;
    public static final  int                                                       TARGET_DEFAULT                           = 12;
    public static final  int                                                       TARGET2_DEFAULT                          = 13;
    private static final java.util.List<Pair<Thread, Throwable>>                   UNCAUGHTS                                = new ArrayList<>();
    //
    private static final Observed<TestObject, Integer>                             source                                   = TestObserved.of("#source", SOURCE_DEFAULT);
    private static final Observed<TestObject, Integer>                             target                                   = TestObserved.of("#target", TARGET_DEFAULT);
    private static final Observed<TestObject, Integer>                             target2                                  = TestObserved.of("#target2", TARGET2_DEFAULT);
    private static final Observed<TestObject, TestObject>                          extra                                    = TestObserved.of("#extraRef", null, true);
    private static final Observed<TestObject, String>                              extraString                              = TestObserved.of("#extra\n\"String", "default");
    private static final Observed<TestObject, List<String>>                        aList                                    = TestObserved.of("#aList", List.of());
    private static final Observed<TestObject, Set<String>>                         aSet                                     = TestObserved.of("#aSet", Set.of());
    private static final Observed<TestObject, Map<String, String>>                 aMap                                     = TestObserved.of("#aMap", Map.of());
    private static final Observed<TestObject, DefaultMap<String, String>>          aDefMap                                  = TestObserved.of("#aDefMap", DefaultMap.of(k -> "zut"));
    private static final Observed<TestObject, QualifiedSet<String, String>>        aQuaSet                                  = TestObserved.of("#aQuaSet", QualifiedSet.of(v -> v));
    private static final Observed<TestObject, QualifiedDefaultSet<String, String>> aQuaDefSet                               = TestObserved.of("#aQuaDefSet", QualifiedDefaultSet.of(v -> v, k -> "zutje"));
    //
    private static final TestClass                                                 extraClass                               = TestClass.of("ExtraClass");
    private static final TestClass                                                 plughClassMain                           = TestClass.of("PlughClass",
            Observer.of("M-source->target      ", o -> target.set(o, source.get(o))),
            Observer.of("M-source->extraString ", o -> extraString.set(o, "@@@@\n\"@@@" + source.get(o) + "@@@")),
            Observer.of("M-source->extra       ", o -> extra.set(o, TestObject.of("" + source.get(o), extraClass)))
    );
    private static final TestClass                                                 plughClassRobot                          = TestClass.of("PlughClass",
            Observer.of("R-source->target      ", o -> target.set(o, source.get(o))),
            Observer.of("R-source->target2     ", o -> target2.set(o, source.get(o))),
            Observer.of("R-source->extraString ", o -> extraString.set(o, "@@@@\n\"@@@" + source.get(o) + "@@@")),
            Observer.of("R-source->extra       ", o -> extra.set(o, TestObject.of("" + source.get(o), extraClass))),
            Observer.of("R-target->extra.target", o -> target.set(extra.get(o), target.get(o))),
            Observer.of("R-target->aList       ", o -> aList.set(o, Collection.range(0, source.get(o)).map(i -> "~" + i).toList())),
            Observer.of("R-target->aSet        ", o -> aSet.set(o, Collection.range(0, source.get(o)).flatMap(i -> Stream.of("&" + i, "@" + i * 2)).toSet())),
            Observer.of("R-target->aMap        ", o -> aMap.set(o, Collection.range(0, source.get(o)).toMap(i -> Entry.of(i + "!m!k!", i + "!m!v!")))),
            Observer.of("R-target->aDefMap     ", o -> aDefMap.set(o, Collection.range(0, source.get(o)).toDefaultMap(aDefMap.get(o).defaultFunction(), i -> Entry.of(i + "!dm!k!", i + "!dm!v!")))),
            Observer.of("R-target->aQuaSet     ", o -> aQuaSet.set(o, Collection.range(0, source.get(o)).map(i -> "QS" + i).toQualifiedSet(s -> "w" + s))),
            Observer.of("R-target->aQuaDefSet  ", o -> aQuaDefSet.set(o, Collection.range(0, source.get(o)).map(i -> "QDS" + i).toQualifiedDefaultSet(s -> "a" + s, s -> "b" + s)))
    );
    private final        String                                                    name;
    private final        TestObject                                                xyzzy;
    private final        Constant<TestObject, TestObject>                          plugConst;
    private final        TestClass                                                 universeClass;
    private final        TestUniverse                                              universe;
    private final        ContextPool                                               pool;
    private final        UniverseTransaction                                       tx;

    public ModelMaker(String name, boolean isRobot) {
        this.name = name;
        xyzzy = TestObject.of("xyzzy\n\"", isRobot ? plughClassRobot : plughClassMain);
        plugConst = Constant.of("plugConst", true, u -> xyzzy);
        universeClass = TestClass.of("Universe-" + name, plugConst);
        universe = TestUniverse.of("universe-" + name, universeClass);
        pool = newPool();
        tx = UniverseTransaction.of(universe, pool);

        CommunicationHelper.add(this);
        CommunicationHelper.add(pool);
    }

    public String getName() {
        return name;
    }

    public UniverseTransaction getTx() {
        return tx;
    }

    private static ContextPool BUGGERS_POOL;
    private static ContextPool newPool() {
        if (BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER) {
            if (BUGGERS_POOL == null) {
                BUGGERS_POOL = ContextThread.createPool(2, ModelMaker::uncaughtException);
            }
            return BUGGERS_POOL;
        }
        return ContextThread.createPool(2, ModelMaker::uncaughtException);
    }

    private static void uncaughtException(Thread thread, Throwable throwable) {
        traceLog("ALARM: uncaught exception in pool thread %s: %s", thread.getName(), throwable);
        synchronized (UNCAUGHTS) {
            UNCAUGHTS.add(Pair.of(thread, throwable));
        }
    }

    public static void assertNoUncaughts() {
        assertAll("uncaught in pool", UNCAUGHTS.stream().map(u -> () -> fail("uncaught in " + u.a().getName(), u.b())));
    }

    public void setXyzzy_source(int i) {
        tx.put("set source to " + i, () -> ModelMaker.source.set(xyzzy, i));
    }

    public int getXyzzy_source() {
        return tx.currentState().get(xyzzy, source);
    }

    public int getXyzzy_target() {
        return tx.currentState().get(xyzzy, target);
    }

    public int getXyzzy_target2() {
        return tx.currentState().get(xyzzy, target2);
    }

    public List<String> getXyzzy_aList() {
        return tx.currentState().get(xyzzy, aList);
    }

    public Set<String> getXyzzy_aSet() {
        return tx.currentState().get(xyzzy, aSet);
    }

    public Map<String, String> getXyzzy_aMap() {
        return tx.currentState().get(xyzzy, aMap);
    }

    public DefaultMap<String, String> getXyzzy_aDefMap() {
        return tx.currentState().get(xyzzy, aDefMap);
    }

    public QualifiedSet<String, String> getXyzzy_aQuaSet() {
        return tx.currentState().get(xyzzy, aQuaSet);
    }

    public QualifiedDefaultSet<String, String> getXyzzy_aQuaDefSet() {
        return tx.currentState().get(xyzzy, aQuaDefSet);
    }

    public TestObject getXyzzy_extra() {
        return tx.currentState().get(xyzzy, extra);
    }
}
