//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2020 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;
import java.util.function.Function;

import org.modelingvalue.dclare.Mutable;
import org.modelingvalue.dclare.MutableClass;
import org.modelingvalue.dclare.Setable;

public class NetUtils {
	
	
	
	public static <M extends Mutable> void startDeltaSupport(DeltaAdaptor<? extends MutableClass, M, Setable<M, Object>> deltaAdapter) {
		int portNumber = 55055;
		System.err.println("ROLE: " +System.getProperty("ROLE") );
        switch (System.getProperty("ROLE")) {
        case "server":
        	startServerThread(deltaAdapter, portNumber);
        	break;
        case "client":
        	startClientThread(deltaAdapter, portNumber);
        	break;
        default:
            throw new Error("define role with -DROLE=(server|client)");
        }
	}

	public static <M extends Mutable> void startServer(
			DeltaAdaptor<? extends MutableClass, M, Setable<M, Object>> deltaAdapter, int portNumber) {
		try (ServerSocket sock = new ServerSocket(portNumber);
				Socket clientSocket = sock.accept();
				PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
				BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
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

	public static <M extends Mutable> void startServerThread(
			DeltaAdaptor<? extends MutableClass, M, Setable<M, Object>> deltaAdapter, int portNumber) {
		Thread t = new Thread(() -> threadCatch(()->startServer(deltaAdapter, portNumber)), "dclare-server");
		t.setDaemon(true);
		t.start();
	}

	public static <M extends Mutable> void startClient(
			DeltaAdaptor<? extends MutableClass, M, Setable<M, Object>> deltaAdapter, int portNumber) {	
		try (Socket sock = new Socket("localhost", portNumber);
				PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
				BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));) {
			in.lines().forEach(l -> deltaAdapter.accept(l));
		} catch (ConnectException e) {
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	public static <M extends Mutable> void startClientThread(
			DeltaAdaptor<? extends MutableClass, M, Setable<M, Object>> deltaAdapter, int portNumber) {
		Thread t = new Thread(() -> threadCatch(()->startClient(deltaAdapter, portNumber)), "dclare-client");
		t.setDaemon(true);
		t.start();
	}
	
	private static <M extends Mutable>  void threadCatch(Runnable r) {
		try {			  
              Thread.sleep(1000);
              r.run();
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
	
}
