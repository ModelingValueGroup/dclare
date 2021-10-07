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

package org.modelingvalue.dclare.sync;

import java.util.*;
import java.util.concurrent.*;

public class SyncConnectionHandler {
    private final SupplierAndConsumer<String> sac;
    private final List<SocketSyncConnection>  connectionList = new ArrayList<>();
    private final AsyncConnectorDaemon        asyncConnector = new AsyncConnectorDaemon();

    public SyncConnectionHandler(SupplierAndConsumer<String> sac) {
        this.sac = sac;
    }

    public List<SocketSyncConnection> getConnections() {
        return Collections.unmodifiableList(connectionList);
    }

    public void connect(String host, int port) {
        SocketSyncConnection newConnection = new SocketSyncConnection(host, port, sac) {
            @Override
            public void close() {
                super.close();
                connectionList.remove(this);
            }
        };
        try {
            connectionList.add(newConnection);
            asyncConnector.queue.put(newConnection);
        } catch (InterruptedException e) {
            throw new Error("connect to " + newConnection.getName() + " failed", e);
        }
    }

    public void disconnect(SocketSyncConnection conn) {
        conn.close();
    }

    public void disconnect() {
        getConnections().forEach(this::disconnect);
    }

    private static class AsyncConnectorDaemon extends WorkDaemon<SocketSyncConnection> {
        private final BlockingQueue<SocketSyncConnection> queue = new ArrayBlockingQueue<>(10);

        public AsyncConnectorDaemon() {
            super("AsyncConnectorDaemon");
            start();
        }

        @Override
        protected SocketSyncConnection waitForWork() throws InterruptedException {
            return queue.take();
        }

        @Override
        protected void execute(SocketSyncConnection connection) {
            connection.connect();
        }
    }
}
