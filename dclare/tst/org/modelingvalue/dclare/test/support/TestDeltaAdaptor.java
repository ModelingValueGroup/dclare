package org.modelingvalue.dclare.test.support;

import static org.modelingvalue.collections.util.TraceTimer.*;

import java.util.function.Predicate;

import org.modelingvalue.dclare.Setable;
import org.modelingvalue.dclare.UniverseTransaction;
import org.modelingvalue.dclare.delta.DeltaAdaptor;

public class TestDeltaAdaptor extends DeltaAdaptor {
    public TestDeltaAdaptor(String name, UniverseTransaction tx, Predicate<Object> objectFilter, Predicate<Setable> setableFilter) {
        super(name, tx, objectFilter, setableFilter);
    }

    protected AdaptorThread makeThread(String name) {
        return new TestAdaptorThread(name);
    }

    public boolean isBusy() {
        return ((TestAdaptorThread) adaptorThread).isBusy() || !deltaQueue.isEmpty() || tx.isHandling() || tx.numInQueue() != 0;
    }

    private class TestAdaptorThread extends AdaptorThread {
        private boolean busy;

        public TestAdaptorThread(String name) {
            super(name);
        }

        public boolean isBusy() {
            return !runnableQueue.isEmpty() || busy;
        }

        protected Runnable next() throws InterruptedException {
            traceLog("***DeltaAdaptor %s: wait for Runnable...", getName());
            busy = false;
            Runnable r = super.next();
            // TODO: there is a small period that the queue could be empty and that 'handling' is false but we still have work todo...
            busy = true;
            traceLog("***DeltaAdaptor %s: got Runnable...", getName());
            return r;
        }
    }
}
