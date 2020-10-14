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

package org.modelingvalue.dclare.delta;

import static org.modelingvalue.collections.util.TraceTimer.*;

import java.util.stream.Stream;

public class DeltaTransport {
    public final DeltaAdaptor    producer;
    public final DeltaAdaptor    consumer;
    public final TransportThread transportThread;

    public DeltaTransport(String name, DeltaAdaptor producer, DeltaAdaptor consumer) {
        this.producer = producer;
        this.consumer = consumer;
        transportThread = makeTransportThread(name);
        transportThread.start();
    }

    protected TransportThread makeTransportThread(String name) {
        return new TransportThread(name);
    }

    public void stop() {
        producer.stop();
        consumer.stop();
        transportThread.stop = true;
    }

    public void interrupt() {
        producer.interrupt();
        consumer.interrupt();
        transportThread.interrupt();
    }

    public void join() {
        producer.join();
        consumer.join();
        transportThread.join_();
    }

    public Stream<Throwable> getThrowables() {
        return Stream.of(producer.getThrowable(), producer.getThrowable(), transportThread.getThrowable());
    }

    public class TransportThread extends Thread {
        private boolean   stop;
        private Throwable throwable;

        public TransportThread(String name) {
            super("transport-" + name);
        }

        @Override
        public void run() {
            traceLog("***Transport    %s: START", getName());
            while (!stop) {
                try {
                    handle(next());
                } catch (InterruptedException e) {
                    if (!stop) {
                        throwable = new Error("unexpected interrupt", e);
                    }
                } catch (Error e) {
                    if (!(e.getCause() instanceof InterruptedException)) {
                        throwable = new Error("unexpected interrupt", e);
                    }
                } catch (Throwable t) {
                    throwable = new Error("unexpected throwable", t);
                }
            }
            traceLog("***Transport    %s: STOP", getName());
        }

        protected Delta next() {
            return producer.get();
        }

        protected void handle(Delta delta) throws InterruptedException {
            consumer.accept(delta);
        }

        public void join_() {
            try {
                join();
            } catch (InterruptedException e) {
                throw new Error(e);
            }
        }

        public Throwable getThrowable() {
            return throwable;
        }
    }
}
