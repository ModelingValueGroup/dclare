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
import static org.modelingvalue.collections.util.TraceTimer.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.modelingvalue.dclare.delta.Delta;
import org.modelingvalue.dclare.delta.DeltaTransport;
import org.modelingvalue.dclare.delta.MultiError;

public class TestDeltaTransport extends DeltaTransport {
    private static final List<TestDeltaTransport> ALL = new ArrayList<>();

    public TestDeltaTransport(String name, TestDeltaAdaptor producer, TestDeltaAdaptor consumer, int simulatedNetworkDelay) {
        super(name, producer, consumer);
        ALL.add(this);
        ((TestTransportThread) transportThread).setSimulatedNetworkDelay(simulatedNetworkDelay);
    }

    protected TransportThread makeTransportThread(String name) {
        return new TestTransportThread(name);
    }

    private boolean isBusy() {
        return ((TestTransportThread) transportThread).isBusy() || ((TestDeltaAdaptor) producer).isBusy() || ((TestDeltaAdaptor) consumer).isBusy();
    }

    public static void stopAllDeltaTransports() {
        ALL.forEach(DeltaTransport::stop);
        ALL.forEach(DeltaTransport::interrupt);
        ALL.forEach(DeltaTransport::join);

        List<Throwable> problems = ALL.stream()
                .flatMap(DeltaTransport::getThrowables)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        ALL.clear();
        if (!problems.isEmpty()) {
            if (problems.size() == 1) {
                throw new Error(problems.get(0));
            } else {
                throw new MultiError("problems after stop of support threads", problems);
            }
        }
    }

    public static void busyWaitAllForIdle() {
        final int  TIMEOUT          = 60_000;
        final int  MAX_SAMPLE_COUNT = 10;
        final long t0               = System.currentTimeMillis();
        boolean    busy;
        do {
            // probe isBusy() 10 times and 1 ms apart until we find a busy sample or conclude that we are idle
            busy = false;
            for (int i = 0; i < MAX_SAMPLE_COUNT && !busy; i++) {
                nap();
                busy = ALL.stream().anyMatch(TestDeltaTransport::isBusy);
            }
        } while (System.currentTimeMillis() < t0 + TIMEOUT && busy);
        traceLog("busyWait ended after %d ms", System.currentTimeMillis() - t0);
        if (busy) {
            // darn,
            System.err.println("this test did not get idle in time:");
            for (TestDeltaTransport t : ALL) {
                boolean ttBusy       = ((TestTransportThread) t.transportThread).isBusy();
                String  producerBusy = ((TestDeltaAdaptor) t.producer).isBusyExplaining();
                String  consumerBusy = ((TestDeltaAdaptor) t.consumer).isBusyExplaining();

                System.err.printf(" - %s.transportThread: %s\n", t.transportThread.getName(), ttBusy ? "BUSY" : "idle");
                System.err.printf(" - %s.producer       : %s%s\n", t.transportThread.getName(), !producerBusy.isEmpty() ? "BUSY" : "idle", producerBusy);
                System.err.printf(" - %s.consumer       : %s%s\n", t.transportThread.getName(), !consumerBusy.isEmpty() ? "BUSY" : "idle", consumerBusy);
            }
            fail();
        }
    }

    private static void nap() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            fail(e);
        }
    }

    protected class TestTransportThread extends TransportThread {
        private boolean busy = true;
        private int     simulatedNetworkDelay;

        public TestTransportThread(String name) {
            super(name);
        }

        protected void handle(Delta delta) throws InterruptedException {
            traceLog("***Transport    %s: sleeping network delay...", getName());
            Thread.sleep(simulatedNetworkDelay);
            traceLog("***Transport    %s: sleep done, pass delta on.", getName());
            super.handle(delta);
        }

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
    }
}
