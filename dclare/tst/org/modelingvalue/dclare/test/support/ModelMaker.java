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

import org.modelingvalue.collections.util.*;
import org.modelingvalue.collections.util.ContextThread.*;
import org.modelingvalue.dclare.Observer;
import org.modelingvalue.dclare.*;

@SuppressWarnings({"FieldCanBeLocal"})
public class ModelMaker {
    // TODO: need to fix the bug and remove this workaround:
    private static final boolean                          BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER          = true;
    private static final ContextPool                      BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER_THE_POOL = BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER ? newPool() : null;
    private static final List<Pair<Thread, Throwable>>    uncaughts                                         = new ArrayList<>();
    //
    public static final  Observed<TestObject, Integer>    source                                            = TestObserved.of("#source", 100);
    public static final  Observed<TestObject, Integer>    target                                            = TestObserved.of("#target", 200);
    public static final  Observed<TestObject, TestObject> extra                                             = TestObserved.of("#extraRef", null, true);
    public static final  Observed<TestObject, String>     extraString                                       = TestObserved.of("#extra\n\"String", "default");
    //
    private static final TestClass                        extraClass                                        = TestClass.of("ExtraClass");
    private static final TestClass                        plughClass                                        = TestClass.of("PlughClass",
            Observer.of("source->target      ", o -> target.set(o, source.get(o))),
            Observer.of("source->extraString ", o -> extraString.set(o, "@@@@\n\"@@@" + source.get(o) + "@@@")),
            Observer.of("source->extra       ", o -> extra.set(o, TestObject.of("" + source.get(o), extraClass))),
            Observer.of("target->extra.target", o -> target.set(extra.get(o), target.get(o)))
    );
    //
    private final        TestObject                       xyzzy                                             = TestObject.of("xyzzy\n\"", plughClass);
    private final        Constant<TestObject, TestObject> plugConst                                         = Constant.of("plugConst", true, u -> xyzzy);
    private final        ContextPool                      pool                                              = BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER ? BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER_THE_POOL : newPool();
    //
    private final        String                           name;
    private final        TestClass                        universeClass;
    private final        TestUniverse                     universe;
    private final        UniverseTransaction              tx;

    public ModelMaker(String name) {
        this.name = name;
        universeClass = TestClass.of("Universe-" + name, plugConst);
        universe = TestUniverse.of("universe-" + name, universeClass);
        tx = UniverseTransaction.of(universe, pool);

        CommunicationHelper.add(this);
        if (!BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER) {
            CommunicationHelper.add(pool);
        }
    }

    public String getName() {
        return name;
    }

    public UniverseTransaction getTx() {
        return tx;
    }

    private static ContextPool newPool() {
        return ContextThread.createPool(2, ModelMaker::uncaughtException);
    }

    private static void uncaughtException(Thread thread, Throwable throwable) {
        traceLog("ALARM: uncaught exception in pool thread %s: %s", thread.getName(), throwable);
        uncaughts.add(Pair.of(thread, throwable));
    }

    public static void assertNoUncaughts() {
        assertAll("uncaught in pool", uncaughts.stream().map(u -> () -> fail("uncaught in " + u.a().getName(), u.b())));
    }

    public void setXyzzyDotSource(int i) {
        tx.put("set source to " + i, () -> ModelMaker.source.set(xyzzy, i));
    }

    public int getXyzzy_source() {
        return tx.currentState().get(xyzzy, source);
    }

    public int getXyzzy_target() {
        return tx.currentState().get(xyzzy, target);
    }

    public TestObject getXyzzy_extra() {
        return tx.currentState().get(xyzzy, extra);
    }
}
