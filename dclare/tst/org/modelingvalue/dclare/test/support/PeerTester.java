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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.TimeUnit;

public class PeerTester {
    private Process        process;
    private BufferedReader in;
    private BufferedWriter out;

    public PeerTester(Class<?> peerClass) {
        assertDoesNotThrow(() -> {
            String         dirOrJar      = peerClass.getProtectionDomain().getCodeSource().getLocation().getFile();
            String         peerClassName = peerClass.getName();
            ProcessBuilder pb            = new ProcessBuilder("java", "-cp", dirOrJar, peerClassName);
            process = pb.start();
            in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        });
    }

    public void expectExit(int exitCode, long maxMs) {
        assertDoesNotThrow(() -> {
            out.flush();
            assertTrue(process.waitFor(maxMs, TimeUnit.MILLISECONDS));
            assertEquals(exitCode, process.exitValue());
        });
    }

    public BufferedReader getIn() {
        return in;
    }

    public BufferedWriter getOut() {
        return out;
    }
}
