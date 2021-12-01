//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2021 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.modelingvalue.dclare.test.support.CommunicationHelper.busyWaitAllForIdle;

import java.io.IOException;
import java.util.ConcurrentModificationException;

import org.junit.jupiter.api.*;
import org.modelingvalue.collections.util.TraceTimer;
import org.modelingvalue.dclare.test.support.*;

public class CommunicationTests {
    static {
        //noinspection PointlessBooleanExpression
        System.setProperty("TRACE_LOG", "" + (false || Boolean.getBoolean("TRACE_LOG")));
        System.setProperty("PARALLELISM", System.getProperty("PARALLELISM", "1"));
        //        System.err.println("~~~FORCED TRACE_LOG   = " + System.getProperty("TRACE_LOG"));
        //        System.err.println("~~~FORCED PARALLELISM = " + System.getProperty("PARALLELISM"));
    }

    //@RepeatedTest(50)
    @Test
    public void universeSyncWithinOneJVM() {
        ModelMaker a = new ModelMaker("a", false);
        TestDeltaAdaptor aAdaptor = CommunicationHelper.hookupDeltaAdaptor(a);

        ModelMaker b = new ModelMaker("b", true);
        TestDeltaAdaptor bAdaptor = CommunicationHelper.hookupDeltaAdaptor(b);

        CommunicationHelper.hookupTransportDaemon("a->b", aAdaptor, bAdaptor);
        CommunicationHelper.hookupTransportDaemon("b->a", bAdaptor, aAdaptor);

        busyWaitAllForIdle();

        for (int NEW_VALUE : new int[]{3, 6, 9, 10}) {
            busyWaitAllForIdle();

            //System.err.printf("universeSyncWithinOneJVM: setting value to %d\n", NEW_VALUE);
            a.setXyzzy_source(NEW_VALUE);

            busyWaitAllForIdle();

            assertEquals(NEW_VALUE, a.getXyzzy_source());
            assertEquals(NEW_VALUE, a.getXyzzy_target());
            assertEquals(NEW_VALUE, b.getXyzzy_source());
            assertEquals(NEW_VALUE, b.getXyzzy_target());
            assertEquals(NEW_VALUE, Integer.parseInt(b.getXyzzy_extra().id().toString()));
            assertEquals(NEW_VALUE, a.getXyzzy_target2());
            assertEquals(NEW_VALUE, b.getXyzzy_target2());

            assertEquals(NEW_VALUE, a.getXyzzy_aList().size());
            assertEquals(NEW_VALUE, b.getXyzzy_aList().size());
            assertEquals(NEW_VALUE, a.getXyzzy_aSet().size() / 2);
            assertEquals(NEW_VALUE, b.getXyzzy_aSet().size() / 2);
            assertEquals(NEW_VALUE, a.getXyzzy_extraSet().size());
            assertEquals(NEW_VALUE, b.getXyzzy_extraSet().size());
            assertEquals(NEW_VALUE, a.getXyzzy_aMap().size());
            assertEquals(NEW_VALUE, b.getXyzzy_aMap().size());
            assertEquals(NEW_VALUE, a.getXyzzy_aDefMap().size());
            assertEquals(NEW_VALUE, b.getXyzzy_aDefMap().size());
            assertEquals(NEW_VALUE, a.getXyzzy_aQuaSet().size());
            assertEquals(NEW_VALUE, b.getXyzzy_aQuaSet().size());
            assertEquals(NEW_VALUE, a.getXyzzy_aQuaDefSet().size());
            assertEquals(NEW_VALUE, b.getXyzzy_aQuaDefSet().size());

            assertEquals("~1", a.getXyzzy_aList().get(1));
            assertEquals("~1", b.getXyzzy_aList().get(1));
            assertTrue(a.getXyzzy_aSet().contains("&1"));
            assertTrue(b.getXyzzy_aSet().contains("&1"));
            assertEquals("1!m!v!", a.getXyzzy_aMap().get("1!m!k!"));
            assertEquals("1!m!v!", b.getXyzzy_aMap().get("1!m!k!"));
            assertEquals("1!dm!v!", a.getXyzzy_aDefMap().get("1!dm!k!"));
            assertEquals("1!dm!v!", b.getXyzzy_aDefMap().get("1!dm!k!"));
            assertEquals("QS1", a.getXyzzy_aQuaSet().get("QS1"));
            assertEquals("QS1", b.getXyzzy_aQuaSet().get("QS1"));
            assertEquals("QDS1", a.getXyzzy_aQuaDefSet().get("QDS1"));
            assertEquals("QDS1", b.getXyzzy_aQuaDefSet().get("QDS1"));
        }
        busyWaitAllForIdle();
    }

    //@RepeatedTest(50)
    @Test
    @Disabled
    public void universeSyncBetweenJVMs() throws IOException {
        ModelMaker mmMain = new ModelMaker("mmMain", false);
        TestDeltaAdaptor mmMainAdaptor = CommunicationHelper.hookupDeltaAdaptor(mmMain);

        PeerTester peer = new PeerTester(CommunicationPeer.class) {
            @Override
            protected String waitForWork() {
                return mmMainAdaptor.get();
            }

            @Override
            protected void execute(String delta) { // delta main->robot
                tellAsync("D" + delta);
            }

            @Override
            public void handleStdinLine(String line) { // delta robot->main
                super.handleStdinLine(line);
                if (line.startsWith("D")) {
                    mmMainAdaptor.accept(line.substring(1));
                }
            }
        };

        busyWaitAllForIdle();
        peer.tell("C" + ModelMaker.SOURCE_DEFAULT);
        int prevTarget2Value = mmMain.getXyzzy_target2();
        for (int NEW_VALUE : new int[]{3, 6, 9, 10}) {
            busyWaitAllForIdle();

            System.err.printf("universeSyncBetweenJVMs: setting value to %d\n", NEW_VALUE);
            assertEquals(prevTarget2Value, mmMain.getXyzzy_target2());
            mmMain.setXyzzy_source(NEW_VALUE);

            busyWaitAllForIdle();

            peer.tell("C" + NEW_VALUE);
            assertEquals(NEW_VALUE, mmMain.getXyzzy_target2());

            prevTarget2Value = NEW_VALUE;
        }
        busyWaitAllForIdle();

        peer.tell("Q");
        assertEquals(0, peer.expectExit(2000));
    }

    @AfterEach
    public void after() {
        CommunicationHelper.rethrowAllDaemonProblems();
        TraceTimer.dumpLogs();
        try {
            CommunicationHelper.tearDownAll();
        } catch (ConcurrentModificationException e) {
            System.err.println("ignored exception during tearDownAll(): " + e);
        }
        ModelMaker.assertNoUncaughtThrowables();
        TraceTimer.dumpLogs();
    }
}
