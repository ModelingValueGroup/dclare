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
import static org.modelingvalue.dclare.test.support.CommunicationHelper.*;

import org.junit.jupiter.api.*;
import org.modelingvalue.collections.util.*;
import org.modelingvalue.dclare.*;
import org.modelingvalue.dclare.sync.*;
import org.modelingvalue.dclare.test.support.*;

public class CommunicationTests {
    static {
        System.setProperty("TRACE_LOG", "false");
        System.setProperty("PARALLELISM", "1");
        //        System.err.println("~~~FORCED TRACE_LOG   = " + System.getProperty("TRACE_LOG"));
        //        System.err.println("~~~FORCED PARALLELISM = " + System.getProperty("PARALLELISM"));
    }

    //@RepeatedTest(10)
    @Test
    public void universeSyncWithinOneJVM() {
        CommunicationModelMakerWithDeltaAdaptor a = new CommunicationModelMakerWithDeltaAdaptor("a", false);
        CommunicationModelMakerWithDeltaAdaptor b = new CommunicationModelMakerWithDeltaAdaptor("b", false);

        new DeltaTransport("a->b", a.getDeltaAdaptor(), b.getDeltaAdaptor(), 100);
        new DeltaTransport("b->a", b.getDeltaAdaptor(), a.getDeltaAdaptor(), 100);

        busyWaitAllForIdle();

        for (int NEW_VALUE : new int[]{42, 43, 44, 45}) {
            traceLog("=========\nMAIN: setting value in universe A to %d", NEW_VALUE);
            a.setXyzzyDotSource(NEW_VALUE);

            traceLog("MAIN: wait for idle");
            busyWaitAllForIdle();
            traceLog("MAIN: IDLE detected");

            State stateA = ImperativeTransaction.clean(a.getTx().currentState());
            State stateB = ImperativeTransaction.clean(b.getTx().currentState());

            assertEquals(NEW_VALUE, a.getXyzzy_source());
            assertEquals(NEW_VALUE, a.getXyzzy_target());
            assertEquals(NEW_VALUE, b.getXyzzy_source());
            assertEquals(NEW_VALUE, b.getXyzzy_target());
            assertEquals(NEW_VALUE, Integer.parseInt(stateB.get(b.getXyzzy(), CommunicationModelMaker.extra).id().toString()));

            busyWaitAllForIdle();
        }
    }

    @Test
    public void universeSyncBetweenJVMs() {
        CommunicationModelMakerWithDeltaAdaptor x = new CommunicationModelMakerWithDeltaAdaptor("x", false);

        PeerTester peer = new PeerTester(CommunicationPeer.class);
        WorkDaemon<String> feeder = new WorkDaemon<>("peer-feeder") {
            @Override
            protected String waitForWork() {
                return x.getDeltaAdaptor().get();
            }

            @Override
            protected void execute(String delta) {
                peer.tellNoSync("D" + delta);
            }
        };
        CommunicationHelper.add(feeder);
        feeder.start();

        busyWaitAllForIdle();//================================================
        peer.tell("S100");
        peer.tell("T100");
        for (int i : new int[]{2020, 4711, 300000}) {
            busyWaitAllForIdle();//================================================
            x.setXyzzyDotSource(i);
            busyWaitAllForIdle();//================================================
            peer.tell("S" + i);
            peer.tell("T" + i);
        }
        busyWaitAllForIdle();//================================================

        peer.tell("Q");
        assertEquals(0, peer.expectExit(2000));
    }


    @AfterEach
    public void after() {
        TraceTimer.dumpLogs();
        CommunicationHelper.tearDownAll();
        CommunicationModelMaker.assertNoUncaughts();
        TraceTimer.dumpLogs();
    }
}
