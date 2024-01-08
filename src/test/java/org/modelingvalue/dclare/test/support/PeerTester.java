//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2024 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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
import static org.modelingvalue.collections.util.TraceTimer.traceLog;

import java.io.*;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.List;
import org.modelingvalue.dclare.sync.WorkDaemon;

public abstract class PeerTester extends WorkDaemon<String> {
    private final AtomicReference<String> lastLine = new AtomicReference<>("");
    private final Process                 process;
    private final Sucker                  inSucker;
    private final Sucker                  errSucker;
    private final BufferedWriter          out;

    public PeerTester(Class<?> mainClass) throws IOException {
        super("PEER-" + mainClass.getName());
        process = new ProcessBuilder("java", "-cp", getClassPath(), mainClass.getName()).start();

        inSucker = new Sucker("in", new BufferedReader(new InputStreamReader(process.getInputStream())), this::handleStdinLine);
        errSucker = new Sucker("err", new BufferedReader(new InputStreamReader(process.getErrorStream())), this::handleStderrLine);
        out = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

        CommunicationHelper.add(this);
        start();
    }

    public String getClassPath() {
        List<String> cp = List.of();

        String classLoaderRender = getClass().getClassLoader().toString();
        if (classLoaderRender.startsWith("AntClassLoader[")) {
            // the AntClassLoader can have classpath elements that are not in the java.class.path property
            // luckely it renders its classpath in a toString()!
            String[] classPath = classLoaderRender.replaceAll("AntClassLoader\\[", "").replaceAll("]", "").split(":");
            cp = cp.addAll(Collection.of(classPath));
        }
        cp = cp.addAll(Collection.of(System.getProperty("java.class.path").split(":")));
        return String.join(":", cp);
    }

    public void handleStdinLine(String line) {
        traceLog("PEER-STDIN  < " + line);
        lastLine.set(line);
    }

    public void handleStderrLine(String line) {
        traceLog("PEER-STDERR < " + line);
        System.err.println("PEER-STDERR < " + line);
    }

    public void tell(String line) {
        sync();
        tellAsync(line);
    }

    public void tellAsync(String line) {
        try {
            traceLog("PEER-TELL   > " + line);
            out.write(line);
            out.newLine();
            out.flush();
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    public void sync() {
        String msg = ".............." + Integer.toString(Math.abs(new Random().nextInt()), 36) + "..............";
        tellAsync(msg);
        long t0 = System.currentTimeMillis();
        while (!lastLine.get().equals(msg)) {
            assertDoesNotThrow(() -> Thread.sleep(10));
            assertTrue(process.isAlive(), "peer died permaturely");
            assertTrue(System.currentTimeMillis() < t0 + 2000);
        }
    }

    public int expectExit(long maxMs) {
        assertDoesNotThrow(() -> {
            out.flush();
            assertTrue(process.waitFor(maxMs, TimeUnit.MILLISECONDS));
            Thread.sleep(10);
            assertAll(() -> assertFalse(inSucker.isAlive()), () -> assertFalse(errSucker.isAlive()), () -> assertNull(inSucker.throwable), () -> assertNull(errSucker.throwable));
        });
        return process.exitValue();
    }

    private static class Sucker extends Thread {
        private final BufferedReader   reader;
        private final Consumer<String> action;
        private Throwable              throwable;

        public Sucker(String name, BufferedReader reader, Consumer<String> action) {
            super("peerSucker-" + name);
            this.reader = reader;
            this.action = action;
            setDaemon(true);
            start();
        }

        @Override
        public void run() {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    action.accept(line);
                }
            } catch (IOException e) {
                throwable = e;
            }
        }
    }
}
