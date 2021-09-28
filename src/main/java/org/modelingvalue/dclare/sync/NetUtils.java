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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import org.modelingvalue.dclare.Mutable;
import org.modelingvalue.dclare.MutableClass;
import org.modelingvalue.dclare.Setable;

public class NetUtils {

    public enum Role {
        server(),
        client(),
        none()
    }

    private static Role role = null;

    public static boolean isActive() {
        return getRole() != Role.none;
    }

    public static Role getRole() {
        if (role == null) {
            String roleProp = System.getProperty("ROLE", "none");
            System.err.println("DCLARE: ROLE=" + roleProp);
            switch (roleProp) {
            case "server":
                role = Role.server;
                break;
            case "client":
                role = Role.client;
                break;
            case "none":
                role = Role.none;
                break;
            default:
                role = Role.none;
                throw new Error("DCLARE: Illegal ROLE=" + roleProp + " (must be 'server', 'client' or 'none')");
            }
        }
        return role;
    }

    public static <M extends Mutable> void startDeltaSupport(DeltaAdaptor<? extends MutableClass, M, Setable<M, Object>> deltaAdapter) {
        int portNumber = 55055;
        switch (getRole()) {
        case server:
            start("dclare-server", () -> runServer(deltaAdapter, portNumber));
            break;
        case client:
            start("dclare-client", () -> runClient(deltaAdapter, portNumber));
            break;
        case none:
            break;
        }
    }

    public static void start(String name, Runnable runnable) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(1000);
                runnable.run();
            } catch (Throwable t1) {
                t1.printStackTrace();
            }
        }, name);
        t.setDaemon(true);
        t.start();
    }

    public static <M extends Mutable> void runServer(DeltaAdaptor<? extends MutableClass, M, Setable<M, Object>> deltaAdapter, int portNumber) {
        try (ServerSocket sock = new ServerSocket(portNumber); Socket clientSocket = sock.accept(); PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true); BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
            //noinspection InfiniteLoopStatement
            while (true) {
                String json = deltaAdapter.get();
                out.write(json);
                out.write('\n');
                out.flush();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static <M extends Mutable> void runClient(DeltaAdaptor<? extends MutableClass, M, Setable<M, Object>> deltaAdapter, int portNumber) {
        try (Socket sock = new Socket("localhost", portNumber);
             BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()))) {
            in.lines().forEach(deltaAdapter);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
