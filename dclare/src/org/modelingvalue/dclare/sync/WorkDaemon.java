package org.modelingvalue.dclare.sync;

import static org.modelingvalue.collections.util.TraceTimer.*;

@SuppressWarnings("unused")
public abstract class WorkDaemon<WORK> extends Thread {
    private boolean   stop;
    private boolean   busy = true;
    private Throwable throwable;

    public WorkDaemon(String name) {
        super(name);
        setDaemon(true);
    }

    protected abstract WORK waitForWork() throws InterruptedException;

    protected abstract void execute(WORK w) throws InterruptedException;

    public void run() {
        traceLog("%s: START", getName());
        while (!stop) {
            try {
                busy = false;
                WORK w = waitForWork();
                busy = true;
                execute(w);
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
        traceLog("%s: STOP", getName());
    }

    public void requestStop() {
        stop = true;
    }

    public void forceStop() {
        requestStop();
        interrupt();
    }

    public boolean isBusy() {
        return busy && isAlive();
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void join_() {
        try {
            join();
        } catch (InterruptedException e) {
            throw new Error(e);
        }
    }
}
