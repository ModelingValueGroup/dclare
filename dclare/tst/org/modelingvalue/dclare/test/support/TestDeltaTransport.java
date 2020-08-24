package org.modelingvalue.dclare.test.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.modelingvalue.collections.util.TraceTimer.*;

import java.util.Arrays;

import org.modelingvalue.dclare.delta.Delta;
import org.modelingvalue.dclare.delta.DeltaTransport;

public class TestDeltaTransport extends DeltaTransport {
    public TestDeltaTransport(String name, TestDeltaAdaptor producer, TestDeltaAdaptor consumer, int simulatedNetworkDelay) {
        super(name, producer, consumer);
        ((TestTransportThread) transportThread).setSimulatedNetworkDelay(simulatedNetworkDelay);
    }

    protected TransportThread makeTransportThread(String name) {
        return new TestTransportThread(name);
    }

    private boolean isBusy() {
        return ((TestTransportThread) transportThread).isBusy() || ((TestDeltaAdaptor) producer).isBusy() || ((TestDeltaAdaptor) consumer).isBusy();
    }

    @SuppressWarnings("BusyWait")
    public static void busyWaitForIdle(TestDeltaTransport... transports) {
        long t0 = System.currentTimeMillis();
        while (Arrays.stream(transports).anyMatch(TestDeltaTransport::isBusy) && System.currentTimeMillis() < t0 + 20_000) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                fail(e);
            }
        }
        if (Arrays.stream(transports).anyMatch(TestDeltaTransport::isBusy)) {
            traceLog("this test did not get idle in time:");
            for (TestDeltaTransport t : transports) {
                System.err.println(" - " + t.transportThread.getName() + ".transportThread: busy=" + ((TestTransportThread) t.transportThread).isBusy());
                System.err.println(" - " + t.transportThread.getName() + ".producer       : busy=" + ((TestDeltaAdaptor) t.producer).isBusy());
                System.err.println(" - " + t.transportThread.getName() + ".consumer       : busy=" + ((TestDeltaAdaptor) t.consumer).isBusy());
            }
            fail();
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