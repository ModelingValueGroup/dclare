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

import static org.modelingvalue.collections.util.TraceTimer.*;

import java.util.stream.*;

import org.modelingvalue.dclare.sync.*;

public class DeltaTransport {
    public final TestDeltaAdaptor producer;
    public final TestDeltaAdaptor consumer;
    public final TransportDaemon  transportDaemon;

    public DeltaTransport(String name, TestDeltaAdaptor producer, TestDeltaAdaptor consumer, int simulatedNetworkDelay) {
        this.producer = producer;
        this.consumer = consumer;
        transportDaemon = new TransportDaemon(name, simulatedNetworkDelay);
        transportDaemon.start();
        CommunicationHelper.add(this);
    }

    boolean isBusy() {
        return transportDaemon.isBusy() || producer.isBusy() || consumer.isBusy();
    }

    public void forceStop() {
        producer.forceStop();
        consumer.forceStop();
        transportDaemon.forceStop();
    }

    public void join() {
        producer.join();
        consumer.join();
        transportDaemon.join_();
    }

    public Stream<Throwable> getThrowables() {
        return Stream.of(producer.getThrowable(), consumer.getThrowable(), transportDaemon.getThrowable());
    }

    public class TransportDaemon extends WorkDaemon<String> {
        private final int simulatedNetworkDelay;

        public TransportDaemon(String name, int simulatedNetworkDelay) {
            super("transport-" + name);
            this.simulatedNetworkDelay = simulatedNetworkDelay;
        }

        @Override
        protected String waitForWork() {
            return producer.get();
        }

        @Override
        protected void execute(String w) throws InterruptedException {
            traceLog("%s: sleeping network delay...", getName());
            Thread.sleep(simulatedNetworkDelay);
            traceLog("%s: sleep done, pass delta on (%d chars).", getName(), w.length());
            consumer.accept(w);
        }

    }
}
