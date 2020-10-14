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

import static org.modelingvalue.collections.util.TraceTimer.traceLog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.modelingvalue.dclare.delta.Delta;
import org.modelingvalue.dclare.delta.DeltaTransport;

public class TestDeltaTransport extends DeltaTransport {

    public static final boolean PRINT_STATE = true;

    public TestDeltaTransport(String name, TestDeltaAdaptor producer, TestDeltaAdaptor consumer, int simulatedNetworkDelay) {
        super(name, producer, consumer);
        ((TestTransportThread) transportThread).setSimulatedNetworkDelay(simulatedNetworkDelay);
    }

    @Override
    protected TransportThread makeTransportThread(String name) {
        return new TestTransportThread(name);
    }

    boolean isBusy() {
        return ((TestTransportThread) transportThread).isBusy() || ((TestDeltaAdaptor) producer).isBusy() || ((TestDeltaAdaptor) consumer).isBusy();
    }

    protected class TestTransportThread extends TransportThread {
        private boolean busy = true;
        private int     simulatedNetworkDelay;

        public TestTransportThread(String name) {
            super(name);
        }

        @Override
        protected void handle(Delta delta) throws InterruptedException {
            traceLog("***Transport    %s: sleeping network delay...", getName());
            if (PRINT_STATE) {
                byte[] bytes = writeObject(delta);
                Thread.sleep(simulatedNetworkDelay);
                delta = (Delta) readObject(bytes);
            }
            traceLog("***Transport    %s: sleep done, pass delta on.", getName());
            super.handle(delta);
        }

        @Override
        protected Delta next() {
            traceLog("***Transport    %s: wait for delta...", getName());
            busy = false;
            Delta delta = super.next();
            // TODO: slight chance of seemingly being idle...
            busy = true;
            return delta;
        }

        public boolean isBusy() {
            return busy;
        }

        public void setSimulatedNetworkDelay(int simulatedNetworkDelay) {
            this.simulatedNetworkDelay = simulatedNetworkDelay;
        }

        private byte[] writeObject(Serializable s) {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                ObjectOutput out;
                out = new ObjectOutputStream(bos);
                out.writeObject(s);
                out.flush();
                return bos.toByteArray();
            } catch (IOException ex) {
                throw new Error(ex);
            }
        }

        private Object readObject(byte[] bytes) {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            try (ObjectInput in = new ObjectInputStream(bis)) {
                return in.readObject();
            } catch (ClassNotFoundException | IOException e) {
                throw new Error(e);
            }
        }
    }
}
