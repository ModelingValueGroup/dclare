//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2024 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
//                                                                                                                       ~
//  Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in       ~
//  compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0   ~
//  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on  ~
//  an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the   ~
//  specific language governing permissions and limitations under the License.                                           ~
//                                                                                                                       ~
//  Maintainers:                                                                                                         ~
//      Wim Bast, Tom Brus                                                                                               ~
//                                                                                                                       ~
//  Contributors:                                                                                                        ~
//      Ronald Krijgsheld ✝, Arjan Kok, Carel Bast                                                                       ~
// --------------------------------------------------------------------------------------------------------------------- ~
//  In Memory of Ronald Krijgsheld, 1972 - 2023                                                                          ~
//      Ronald was suddenly and unexpectedly taken from us. He was not only our long-term colleague and team member      ~
//      but also our friend. "He will live on in many of the lines of code you see below."                               ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.dclare.sync;

import static org.modelingvalue.collections.util.TraceTimer.traceLog;

import java.io.*;
import java.net.Socket;

public class SocketSyncConnection {
    private final String                      host;
    private final int                         port;
    private final SupplierAndConsumer<String> sac;
    //
    private boolean                           connecting;
    private Socket                            socket;
    private InpStreamDaemon                   inpDaemon;
    private OutStreamDaemon                   outDaemon;

    public boolean isConnecting() {
        return connecting;
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && inpDaemon != null && outDaemon != null;
    }

    public String getName() {
        return host + ":" + port;
    }

    public Integer getNumInPackages() {
        return inpDaemon == null ? 0 : inpDaemon.getNumlines();
    }

    public Integer getNumOutPackages() {
        return outDaemon == null ? 0 : outDaemon.getNumlines();
    }

    public Integer getNumInBytes() {
        return inpDaemon == null ? 0 : inpDaemon.getNumChars();
    }

    public Integer getNumOutBytes() {
        return outDaemon == null ? 0 : outDaemon.getNumChars();
    }

    public SocketSyncConnection(String host, int port, SupplierAndConsumer<String> sac) {
        this.host = host;
        this.port = port;
        this.sac = sac;
    }

    public void connect() {
        try {
            connecting = true;
            this.socket = new Socket(host, port);
            this.inpDaemon = new InpStreamDaemon(socket);
            this.outDaemon = new OutStreamDaemon(socket);
        } catch (IOException e) {
            throw new Error("could not connect to " + getName(), e);
        } finally {
            if (!isConnected()) {
                close();
            }
            connecting = false;
        }
    }

    public void close() {
        if (inpDaemon != null) {
            inpDaemon.close();
            inpDaemon = null;
        }
        if (outDaemon != null) {
            outDaemon.interruptAndClose();
            outDaemon = null;
        }
        if (socket != null) {
            try {
                traceLog("@%s: Connection closing socket", getName());
                socket.close();
                socket = null;
            } catch (IOException e) {
                traceLog("@%s: Connection closing socket IOEXC", getName());
                // ignore
            }
        }
    }

    private abstract static class StreamDaemon extends WorkDaemon<String> {
        protected int numlines;
        protected int numChars;

        public StreamDaemon(String dir, Socket clientSocket) {
            super("serving-" + clientSocket.getPort() + "-" + clientSocket.getLocalPort() + "-" + dir);
        }

        @Override
        protected void execute(String line) {
            if (line != null) {
                numlines++;
                numChars += line.length();
                executeNonNull(line);
            }
        }

        protected abstract void executeNonNull(String line);

        public int getNumlines() {
            return numlines;
        }

        public int getNumChars() {
            return numChars;
        }
    }

    private class InpStreamDaemon extends StreamDaemon {
        private final BufferedReader in;

        public InpStreamDaemon(Socket clientSocket) throws IOException {
            super("inp", clientSocket);
            this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            start();
        }

        @Override
        protected String waitForWork() throws InterruptedException {
            try {
                String line = in.readLine();
                if (line == null) {
                    close();
                }
                return line;
            } catch (Exception e) {
                traceLog("@%s: InpStreamDaemon Exception (stop=%s)", getName(), needsToStop());
                if (needsToStop()) {
                    throw new InterruptedException();
                } else {
                    throw new Error(e);
                }
            }
        }

        @Override
        protected void executeNonNull(String line) {
            sac.accept(line);
        }
    }

    private class OutStreamDaemon extends StreamDaemon {
        private final PrintWriter out;

        public OutStreamDaemon(Socket clientSocket) throws IOException {
            super("out", clientSocket);
            this.out = new PrintWriter(clientSocket.getOutputStream(), true);
            start();
        }

        @Override
        protected String waitForWork() {
            return sac.get();
        }

        @Override
        protected void executeNonNull(String line) {
            out.write(line);
            out.write("\n");
            out.flush();
        }
    }
}
