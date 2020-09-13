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
import static org.modelingvalue.dclare.test.support.CommunicationHelper.*;

import java.io.*;

import org.junit.jupiter.api.*;
import org.modelingvalue.collections.util.*;
import org.modelingvalue.dclare.test.support.*;

public class CommunicationTests {
    static {
        System.setProperty("TRACE_LOG", "false");
        System.setProperty("PARALLELISM", "1");
        //        System.err.println("~~~FORCED TRACE_LOG   = " + System.getProperty("TRACE_LOG"));
        //        System.err.println("~~~FORCED PARALLELISM = " + System.getProperty("PARALLELISM"));
    }

    //@RepeatedTest(50)
    @Test
    public void universeSyncWithinOneJVM() {
        ModelMaker       a        = new ModelMaker("a");
        TestDeltaAdaptor aAdaptor = CommunicationHelper.hookupDeltaAdaptor(a);

        ModelMaker       b        = new ModelMaker("b");
        TestDeltaAdaptor bAdaptor = CommunicationHelper.hookupDeltaAdaptor(b);

        CommunicationHelper.hookupTransportDaemon("a->b", aAdaptor, bAdaptor);
        CommunicationHelper.hookupTransportDaemon("b->a", bAdaptor, aAdaptor);

        busyWaitAllForIdle();

        for (int NEW_VALUE : new int[]{42, 43, 44, 45}) {
            System.err.println("===========================================================================");
            System.err.printf("MAIN: setting value in universe A to %d\n", NEW_VALUE);
            a.setXyzzyDotSource(NEW_VALUE);

            busyWaitAllForIdle();

            assertEquals(NEW_VALUE, a.getXyzzy_source());
            assertEquals(NEW_VALUE, a.getXyzzy_target());
            assertEquals(NEW_VALUE, b.getXyzzy_source());
            assertEquals(NEW_VALUE, b.getXyzzy_target());
            assertEquals(NEW_VALUE, Integer.parseInt(b.getXyzzy_extra().id().toString()));
            assertEquals(200, a.getXyzzy_target2());
            assertEquals(200, b.getXyzzy_target2());
            assertEquals(NEW_VALUE, a.getXyzzy_aList().size());
            assertEquals(NEW_VALUE, b.getXyzzy_aList().size());

            busyWaitAllForIdle();
        }
    }

    //@RepeatedTest(50)
    @Test
    public void universeSyncBetweenJVMs() throws IOException {
        ModelMaker       mmMaster        = new ModelMaker("mmMaster");
        TestDeltaAdaptor mmMasterAdaptor = CommunicationHelper.hookupDeltaAdaptor(mmMaster);

        PeerTester peer = new PeerTester(CommunicationPeer.class) {
            @Override
            protected String waitForWork() {
                return mmMasterAdaptor.get();
            }

            @Override
            protected void execute(String delta) { // delta master->slave
                tellNoSync("D" + delta);
            }

            @Override
            public void handleStdinLine(String line) { // delta slave->master
                super.handleStdinLine(line);
                if (line.startsWith("D")) {
                    mmMasterAdaptor.accept(line.substring(1));
                }
            }
        };

        busyWaitAllForIdle();
        peer.tell("C100");
        int prev = 100;
        for (int i : new int[]{42, 43, 44, 45}) {
            busyWaitAllForIdle();
            mmMaster.setXyzzyDotSource(i);
            busyWaitAllForIdle();
            assertEquals(prev, mmMaster.getXyzzy_target2());
            peer.tell("C" + i);
            assertEquals(i, mmMaster.getXyzzy_target2());
            prev = i;
        }
        busyWaitAllForIdle();

        peer.tell("Q");
        assertEquals(0, peer.expectExit(2000));
    }

    @AfterEach
    public void after() {
        TraceTimer.dumpLogs();
        CommunicationHelper.tearDownAll();
        ModelMaker.assertNoUncaughts();
        TraceTimer.dumpLogs();
    }
}
