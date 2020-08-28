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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.modelingvalue.collections.util.TraceTimer.traceLog;

import java.io.BufferedWriter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.modelingvalue.collections.util.TraceTimer;
import org.modelingvalue.dclare.ImperativeTransaction;
import org.modelingvalue.dclare.State;
import org.modelingvalue.dclare.test.support.PeerTester;
import org.modelingvalue.dclare.test.support.TestDeltaTransport;

public class CommunicationTests {
    @RepeatedTest(5)
    //@Test
    public void universeSyncWithinOneJVM() {
        traceLog("MAIN: BEGIN");
        CommTestRig a = new CommTestRig("a");
        CommTestRig b = new CommTestRig("b");

        new TestDeltaTransport("a->b", a.adaptor, b.adaptor, 100);
        new TestDeltaTransport("b->a", b.adaptor, a.adaptor, 100);

        TestDeltaTransport.busyWaitAllForIdle();

        for (int NEW_VALUE : new int[]{42, 43, 44, 45}) {
            traceLog("=========\nMAIN: setting value in universe A to %d", NEW_VALUE);
            a.tx.put("set new value in universe A", () -> CommTestRig.source.set(a.object, NEW_VALUE));

            traceLog("MAIN: wait for idle");
            TestDeltaTransport.busyWaitAllForIdle();
            traceLog("MAIN: IDLE detected");
            CommTestRig.assertNoUncaughts();

            State stateA = ImperativeTransaction.clean(a.tx.currentState());
            State stateB = ImperativeTransaction.clean(b.tx.currentState());

            traceLog("checking for sync of %d...", NEW_VALUE);
            //            traceLog("stateA=%s", stateA.asString());
            //            traceLog("stateB=%s", stateB.asString());
            traceLog("DIFF states = %s", stateA.get(() -> stateA.diffString(stateB)));

            assertEquals(NEW_VALUE, (int) stateA.get(a.object, CommTestRig.source));
            assertEquals(NEW_VALUE, (int) stateA.get(a.object, CommTestRig.target));
            assertEquals(NEW_VALUE, (int) stateB.get(b.object, CommTestRig.source));
            assertEquals(NEW_VALUE, (int) stateB.get(b.object, CommTestRig.target));

            CommTestRig.assertNoUncaughts();
        }
        traceLog("MAIN: END");
    }

    @Test
    public void universeSyncBetweenJVMs() throws Throwable {
        PeerTester peer = new PeerTester(CommunicationPeer.class);
        BufferedWriter out = peer.getOut();
        out.write("66\n");
        peer.expectExit(66, 1000);
    }

    @AfterEach
    public void after() {
        TraceTimer.dumpLogs();
        traceLog("MAIN: cleanup...");
        TestDeltaTransport.stopAllDeltaTransports();
        CommTestRig.tearDownAll();
        CommTestRig.assertNoUncaughts();
        traceLog("MAIN: cleanup done");
        TraceTimer.dumpLogs();
    }
}
