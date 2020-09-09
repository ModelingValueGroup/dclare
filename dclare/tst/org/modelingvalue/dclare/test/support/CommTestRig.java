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
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;

import org.modelingvalue.collections.util.*;
import org.modelingvalue.collections.util.ContextThread.*;
import org.modelingvalue.dclare.Observer;
import org.modelingvalue.dclare.*;
import org.modelingvalue.dclare.ex.*;

@SuppressWarnings({"rawtypes", "FieldCanBeLocal"})
public class CommTestRig {
    // TODO: need to fix the bug and remove this workaround:
    private static final boolean                          BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER          = true;
    private static final ContextPool                      BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER_THE_POOL = BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER ? newPool() : null;
    //
    private static final int                              IDLE_DETECT_TIMEOUT                               = 5_000;
    private static final int                              IDLE_SAMPLES_FOR_DEFINITIVE_IDLE_CONCLUSION       = 10;
    private static final List<CommTestRig>                ALL_RIGS                                          = new ArrayList<>();
    private static final List<DeltaTransport>             ALL_TRANSPORTS                                    = new ArrayList<>();
    //
    private final static Predicate<Object>                objectFilter                                      = o -> o instanceof TestObject;
    private final static Predicate<Setable>               setableFilter                                     = s -> s.id().toString().startsWith("#");
    private final static List<Throwable>                  uncaughtThrowables                                = new ArrayList<>();
    private final static List<Thread>                     uncaughtThreads                                   = new ArrayList<>();
    //
    public final static  Observed<TestObject, Integer>    source                                            = TestObserved.of("#source", 100);
    public final static  Observed<TestObject, Integer>    target                                            = TestObserved.of("#target", 200);
    public final static  Observed<TestObject, String>     targetString                                      = TestObserved.of("#target\n\"String", "xxx");
    public final static  Observed<TestObject, TestObject> extra                                             = TestObserved.of("#extra", null, true);
    private final static TestClass                        extraClass                                        = TestClass.of("Extra");
    private final static TestClass                        plughClass                                        = TestClass.of("Plugh",
            Observer.of("source2target_Setter", o -> target.set(o, source.get(o))),
            Observer.of("source2target_Setter2", o -> targetString.set(o, "@@@@\n\"@@@" + source.get(o))),
            Observer.of("extra_Setter", o -> extra.set(o, TestObject.of("" + source.get(o), extraClass))),
            Observer.of("extraTarget_Setter", o -> target.set(extra.get(o), target.get(o)))
    );
    private final        TestObject                       xyzzy                                             = TestObject.of("xyzzy\n\"", plughClass);
    private final        Constant<TestObject, TestObject> plugConst                                         = Constant.of("plugConst", true, u -> xyzzy);
    private final        TestClass                        universeClass;
    //
    private final        ContextPool                      pool                                              = BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER ? BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER_THE_POOL : newPool();
    private final        TestUniverse                     universe;
    private final        UniverseTransaction              tx;
    private              TestDeltaAdaptor                 adaptor;

    public CommTestRig(String name) {
        ALL_RIGS.add(this);
        universeClass = TestClass.of("Universe-" + name, plugConst);
        universe = TestUniverse.of("universe-" + name,
                u -> adaptor = new TestDeltaAdaptor(name, getTx(), objectFilter, setableFilter),
                universeClass);
        tx = UniverseTransaction.of(universe, pool);
    }

    public TestObject getXyzzy() {
        return xyzzy;
    }

    public UniverseTransaction getTx() {
        return tx;
    }

    public TestDeltaAdaptor getAdaptor() {
        if (adaptor == null) {
            busyWaitAllForIdle();
        }
        return adaptor;
    }

    public static void assertNoUncaughts() {
        assertAll("uncaught in pool", IntStream.range(0, uncaughtThrowables.size()).mapToObj(i -> () -> fail("uncaught in " + uncaughtThreads.get(i).getName(), uncaughtThrowables.get(i))));
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
        ALL_TRANSPORTS.clear();
    }


    private static ContextPool newPool() {
        return ContextThread.createPool(2, CommTestRig::uncaughtException);
    }

    private static void uncaughtException(Thread thread, Throwable throwable) {
        traceLog("ALARM: uncaught exception in pool thread %s: %s", thread.getName(), throwable);
        uncaughtThrowables.add(throwable);
        uncaughtThreads.add(thread);
    }

    public static void add(DeltaTransport testDeltaTransport) {
        ALL_TRANSPORTS.add(testDeltaTransport);
    }

    public static void stopAllDeltaTransports() {
        ALL_TRANSPORTS.forEach(DeltaTransport::stop);
        ALL_TRANSPORTS.forEach(DeltaTransport::interrupt);
        ALL_TRANSPORTS.forEach(DeltaTransport::join);
        rethrowAny();
    }

    private static void rethrowAny() {
        List<Throwable> problems = ALL_TRANSPORTS.stream()
                .flatMap(DeltaTransport::getThrowables)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!problems.isEmpty()) {
            if (problems.size() == 1) {
                throw new Error(problems.get(0));
            } else {
                throw new MultiError("problems after stop of support threads", problems);
            }
        }
    }

    public static void busyWaitAllForIdle() {
        final long t0 = System.currentTimeMillis();
        boolean    busy;
        do {
            // probe isBusy() 10 times and 1 ms apart until we find a busy sample or conclude that we are idle
            busy = false;
            for (int i = 0; i < IDLE_SAMPLES_FOR_DEFINITIVE_IDLE_CONCLUSION && !busy; i++) {
                nap();
                rethrowAny();
                busy = ALL_RIGS.stream().anyMatch(r -> r.adaptor == null) || ALL_TRANSPORTS.stream().anyMatch(DeltaTransport::isBusy);
            }
        } while (System.currentTimeMillis() < t0 + IDLE_DETECT_TIMEOUT && busy);
        traceLog("busyWait ended after %d ms", System.currentTimeMillis() - t0);
        if (busy) {
            // darn,
            System.err.println("this test did not get idle in time:");
            for (DeltaTransport t : ALL_TRANSPORTS) {
                StringBuilder producerWhy  = new StringBuilder();
                StringBuilder consumerWhy  = new StringBuilder();
                String        name         = t.transportThread.getName();
                boolean       ttBusy       = t.transportThread.isBusy();
                boolean       producerBusy = t.producer.isBusy(producerWhy);
                boolean       consumerBusy = t.consumer.isBusy(consumerWhy);

                System.err.printf(" - %s.transportThread: %s\n", name, ttBusy ? "BUSY" : "idle");
                System.err.printf(" - %s.producer       : %s (%s)\n", name, producerBusy ? "BUSY" : "idle", producerWhy);
                System.err.printf(" - %s.consumer       : %s (%s)\n", name, consumerBusy ? "BUSY" : "idle", consumerWhy);
            }
            fail();
        }
    }

    private static void nap() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            fail(e);
        }
    }
}
