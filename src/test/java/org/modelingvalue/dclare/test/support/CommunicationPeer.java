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

package org.modelingvalue.dclare.test.support;

import java.util.concurrent.atomic.AtomicBoolean;

import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.util.TraceTimer;
import org.modelingvalue.dclare.sync.WorkDaemon;

@SuppressWarnings("PointlessBooleanExpression")
public class CommunicationPeer {
    static {
        System.setProperty("JSON_TRACE", "" + (false || Boolean.getBoolean("JSON_TRACE")));
        System.setProperty("TRACE_LOG", "" + (false || Boolean.getBoolean("TRACE_LOG")));
        System.setProperty("PARALLELISM", System.getProperty("PARALLELISM", "1"));
        //        System.err.println("~~~FORCED TRACE_LOG   = " + System.getProperty("TRACE_LOG"));
        //        System.err.println("~~~FORCED PARALLELISM = " + System.getProperty("PARALLELISM"));
    }

    public static void main(String[] args) throws Throwable {
        System.err.println("peer started");

        ModelMaker mmSlave = new ModelMaker("mmSlave", true);
        TestDeltaAdaptor mmSlaveAdaptor = CommunicationHelper.hookupDeltaAdaptor(mmSlave);

        WorkDaemon<String> backFeeder = new WorkDaemon<>("backFeeder") {
            @Override
            protected String waitForWork() {
                return mmSlaveAdaptor.get();
            }

            @Override
            protected void execute(String delta) { // delta robot->main
                System.out.println("D" + delta);
            }
        };
        CommunicationHelper.add(backFeeder);
        backFeeder.start();

        AtomicBoolean stop = new AtomicBoolean();
        CommunicationHelper.interpreter(System.in, stop, Map.of(Entry.of('.', (c, line) -> System.out.println("." + line)), Entry.of('D', (c, line) -> mmSlaveAdaptor.accept(line)), // delta main->robot
                Entry.of('C', (c, line) -> check(line, mmSlave.getXyzzy_source(), mmSlave.getXyzzy_target(), mmSlave.getXyzzy_aList().size(), mmSlave.getXyzzy_aSet().size() / 2, mmSlave.getXyzzy_aMap().size(), mmSlave.getXyzzy_aDefMap().size(), mmSlave.getXyzzy_aQuaSet().size(), mmSlave.getXyzzy_aQuaDefSet().size())), Entry.of('Q', (c, line) -> stop.set(true)), Entry.of('*', (c, line) -> exit(10, "ERROR: unknown command " + c + line))));

        CommunicationHelper.tearDownAll();
        System.err.println("peer stopped");
        TraceTimer.dumpLogs();
    }

    private static void check(String expectedInt, int... values) {
        int expected = Integer.parseInt(expectedInt);
        for (int value : values) {
            if (value != expected) {
                exit(1, "CHECK FAILED: expected " + expected + " (found " + value + ")");
            }
        }
    }

    private static void exit(int status, String msg) {
        TraceTimer.dumpLogs();
        System.err.println(msg + "; immediate exit(" + status + ")");
        System.exit(status);
    }
}
