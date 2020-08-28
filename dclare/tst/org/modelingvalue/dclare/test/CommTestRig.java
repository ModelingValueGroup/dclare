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

package org.modelingvalue.dclare.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.modelingvalue.collections.util.TraceTimer.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import org.modelingvalue.collections.util.ContextThread;
import org.modelingvalue.collections.util.ContextThread.ContextPool;
import org.modelingvalue.dclare.Constant;
import org.modelingvalue.dclare.Observed;
import org.modelingvalue.dclare.Observer;
import org.modelingvalue.dclare.Setable;
import org.modelingvalue.dclare.UniverseTransaction;
import org.modelingvalue.dclare.test.support.TestClass;
import org.modelingvalue.dclare.test.support.TestDeltaAdaptor;
import org.modelingvalue.dclare.test.support.TestObject;
import org.modelingvalue.dclare.test.support.TestUniverse;

@SuppressWarnings("rawtypes")
class CommTestRig {
    private static final boolean           BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER          = true;
    private static final ContextPool       BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER_THE_POOL = BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER ? newPool() : null;
    private static final List<CommTestRig> ALL_RIGS                                          = new ArrayList<>();

    final static Observed<TestObject, Integer> source            = Observed.of("#source", 100);
    final static Observed<TestObject, Integer> target            = Observed.of("#target", 200);
    final static TestClass                     clazz             = TestClass.of("Object", Observer.of("observer", o -> target.set(o, source.get(o))));
    final static Predicate<Object>             objectFilter      = o -> o instanceof TestObject;
    final static Predicate<Setable>            setableFilter     = s -> s.id().toString().startsWith("#");
    final static List<Throwable>               uncaughtThowables = new ArrayList<>();
    final static List<Thread>                  uncaughtThreads   = new ArrayList<>();

    final ContextPool                        pool   = BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER ? BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER_THE_POOL : newPool();
    final TestObject                         object = TestObject.of("object", clazz);
    final Constant<TestUniverse, TestObject> child  = Constant.of("child", true, u -> object);
    final TestUniverse                       universe;
    final UniverseTransaction                tx;
    final TestDeltaAdaptor                   adaptor;

    CommTestRig(String name) {
        ALL_RIGS.add(this);
        universe = TestUniverse.of("universe-" + name, TestClass.of("Universe", child));
        tx = UniverseTransaction.of(universe, pool);
        adaptor = new TestDeltaAdaptor(name, tx, objectFilter, setableFilter);
    }

    public static void assertNoUncaughts() {
        assertAll("uncaught in pool", IntStream.range(0, uncaughtThowables.size()).mapToObj(i -> () -> fail("uncaught in " + uncaughtThreads.get(i).getName(), uncaughtThowables.get(i))));
    }

    public static void tearDownAll() {
        ALL_RIGS.forEach(rig -> {
            if (rig.tx != null) {
                rig.tx.stop();
            }
        });
        ALL_RIGS.forEach(rig -> {
            if (rig.tx != null) {
                rig.tx.waitForEnd();
            }
        });
        if (!BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER) {
            ALL_RIGS.forEach(rig -> {
                if (rig.pool != null) {
                    rig.pool.shutdownNow();
                    assertDoesNotThrow(() -> rig.pool.awaitTermination(1, TimeUnit.SECONDS));
                }
            });
        }
        ALL_RIGS.clear();
    }


    private static ContextPool newPool() {
        return ContextThread.createPool(2, CommTestRig::uncaughtException);
    }

    private static void uncaughtException(Thread thread, Throwable throwable) {
        traceLog("ALARM: uncaught exception in pool thread %s: %s", thread.getName(), throwable);
        uncaughtThowables.add(throwable);
        uncaughtThreads.add(thread);
    }
}
