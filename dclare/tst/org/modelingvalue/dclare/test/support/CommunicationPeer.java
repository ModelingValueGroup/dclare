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

import java.io.*;

import org.modelingvalue.collections.util.*;

public class CommunicationPeer {
    static {
        System.setProperty("TRACE_LOG", "false");
        System.setProperty("PARALLELISM", "1");
        //        System.err.println("~~~FORCED TRACE_LOG   = " + System.getProperty("TRACE_LOG"));
        //        System.err.println("~~~FORCED PARALLELISM = " + System.getProperty("PARALLELISM"));
    }

    public static void main(String[] args) throws Throwable {
        System.err.println("starting...");
        ModelMaker       mmSlave        = new ModelMaker("mmSlave");
        TestDeltaAdaptor mmSlaveAdaptor = CommunicationHelper.hookupDeltaAdaptor(mmSlave, true);

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        boolean        stop           = false;
        String         line;
        while (!stop && (line = bufferedReader.readLine()) != null) {
            CommunicationHelper.busyWaitAllForIdle();
            System.err.println("got line: " + line);
            if (1 <= line.length()) {
                char cmd = line.charAt(0);
                line = line.substring(1);
                switch (cmd) {
                case '.':
                    System.out.println("." + line);
                    break;
                case 'D':
                    mmSlaveAdaptor.accept(line);
                    break;
                case 'S':
                    check("source", Integer.parseInt(line), mmSlave.getXyzzy_source());
                    break;
                case 'T':
                    check("target", Integer.parseInt(line), mmSlave.getXyzzy_target());
                    break;
                case 'Q':
                    stop = true;
                    break;
                default:
                    System.err.println("ERROR: unknown command " + cmd + " (" + line + ")");
                    System.exit(10);
                }
            }
        }
        CommunicationHelper.busyWaitAllForIdle();
        CommunicationHelper.tearDownAll();
        TraceTimer.dumpLogs();
        System.err.println("stopping...");
    }

    private static void check(String name, int expected, int value) {
        System.err.println("CHECK: " + name + " == " + value + " (expecting " + expected + ")");
        if (value != expected) {
            System.err.println("CHECK FAILED; immediate exit(1)");
            System.exit(1);
        }
    }
}
