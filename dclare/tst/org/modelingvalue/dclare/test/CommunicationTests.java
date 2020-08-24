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
import static org.modelingvalue.dclare.test.support.Shared.*;

import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.modelingvalue.collections.util.TraceTimer;
import org.modelingvalue.dclare.Constant;
import org.modelingvalue.dclare.ImperativeTransaction;
import org.modelingvalue.dclare.Observed;
import org.modelingvalue.dclare.Observer;
import org.modelingvalue.dclare.Setable;
import org.modelingvalue.dclare.State;
import org.modelingvalue.dclare.UniverseTransaction;
import org.modelingvalue.dclare.test.support.TestClass;
import org.modelingvalue.dclare.test.support.TestDeltaAdaptor;
import org.modelingvalue.dclare.test.support.TestDeltaTransport;
import org.modelingvalue.dclare.test.support.TestObject;
import org.modelingvalue.dclare.test.support.TestUniverse;

@SuppressWarnings({"rawtypes"})
public class CommunicationTests {
    @RepeatedTest(5)
    public void source2target() {
        TestEnvironment a = new TestEnvironment();
        TestEnvironment b = new TestEnvironment();

        UniverseTransaction txA = UniverseTransaction.of(a.universe, THE_POOL);
        UniverseTransaction txB = UniverseTransaction.of(b.universe, THE_POOL);

        Predicate<Object>  objectFilter  = o -> o instanceof TestObject;
        Predicate<Setable> setableFilter = s -> s.id().toString().startsWith("#");

        TestDeltaAdaptor   adaptorA = new TestDeltaAdaptor("a", txA, objectFilter, setableFilter);
        TestDeltaAdaptor   adaptorB = new TestDeltaAdaptor("b", txB, objectFilter, setableFilter);
        TestDeltaTransport a2b      = new TestDeltaTransport("a->b", adaptorA, adaptorB, 100);
        TestDeltaTransport b2a      = new TestDeltaTransport("b->a", adaptorB, adaptorA, 100);


        final int NEW_VALUE = 900;
        traceLog("MAIN: setting value in universe A");
        txA.put("set new value in universe A", () -> TestEnvironment.source.set(a.object, NEW_VALUE));

        traceLog("MAIN: wait for idle");
        TestDeltaTransport.busyWaitForIdle(a2b, b2a);
        traceLog("MAIN: IDLE detected");
        TraceTimer.dumpAll();

        State resultA = ImperativeTransaction.clean(txA.currentState());
        State resultB = ImperativeTransaction.clean(txB.currentState());

        //        Shared.printState(txA, resultA);
        //        Shared.printState(txB, resultB);
        //        System.err.println("====== diff = " + resultA.diffString(resultB));

        traceLog("checking...");
        assertEquals(NEW_VALUE, (int) resultA.get(a.object, TestEnvironment.source));
        assertEquals(NEW_VALUE, (int) resultA.get(a.object, TestEnvironment.target));
        assertEquals(NEW_VALUE, (int) resultB.get(b.object, TestEnvironment.source));
        assertEquals(NEW_VALUE, (int) resultB.get(b.object, TestEnvironment.target));

        assertEquals(resultA, resultB);

        traceLog("cleanup...");
        TestDeltaTransport.stopAllDeltaTransports(a2b, b2a);
        txA.stop();
        txB.stop();
        txA.waitForEnd();
        txB.waitForEnd();
        traceLog("cleanup done");
        TraceTimer.dumpAll();
    }

    @AfterEach
    public void after() {
        TraceTimer.dumpAll();
    }

    private static class TestEnvironment {
        final static Observed<TestObject, Integer> source = Observed.of("#source", 100);
        final static Observed<TestObject, Integer> target = Observed.of("#target", 200);
        final static TestClass                     clazz  = TestClass.of("Object", Observer.of("observer", o -> target.set(o, source.get(o))));

        final TestObject                         object;
        final Constant<TestUniverse, TestObject> child;
        final TestUniverse                       universe;

        TestEnvironment() {
            object = TestObject.of("object", clazz);
            child = Constant.of("child", true, u -> object);
            universe = TestUniverse.of("test-universe", TestClass.of("Universe", child));
        }
    }
}
