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
        while (Arrays.stream(transports).anyMatch(TestDeltaTransport::isBusy)) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                fail(e);
            }
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