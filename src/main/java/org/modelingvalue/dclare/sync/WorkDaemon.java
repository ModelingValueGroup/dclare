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

import java.io.Closeable;

import static org.modelingvalue.collections.util.TraceTimer.traceLog;

@SuppressWarnings("unused")
public abstract class WorkDaemon<WORK> extends Thread implements Closeable {
    private boolean   stop;
    private boolean   busy = true;
    private Throwable throwable;

    public WorkDaemon(String name) {
        super(name);
        setDaemon(true);
    }

    protected abstract WORK waitForWork() throws InterruptedException;

    protected abstract void execute(WORK w) throws InterruptedException;

    @Override
    public void run() {
        traceLog("@%s: WorkDaemon BEGIN", getName());
        while (!stop) {
            try {
                busy = false;
                WORK w = waitForWork();
                busy = true;
                execute(w);
            } catch (InterruptedException e) {
                traceLog("@%s: WorkDaemon InterruptedException (stop=%s)", getName(), stop);
                if (!stop) {
                    throwable = new Error("unexpected interrupt", e);
                }
            } catch (Error e) {
                traceLog("@%s: WorkDaemon Error (stop=%s)", getName(), stop);
                if (!(e.getCause() instanceof InterruptedException)) {
                    throwable = new Error("unexpected interrupt", e);
                }
            } catch (Throwable t) {
                traceLog("@%s: WorkDaemon Throwable (stop=%s)", getName(), stop);
                throwable = new Error("unexpected throwable", t);
            }
        }
        traceLog("@%s: WorkDaemon END", getName());
    }

    @Override
    public void close() {
        traceLog("@%s: WorkDaemon close: stop:=true", getName());
        stop = true;
    }

    public boolean needsToStop() {
        return stop;
    }

    public void interruptAndClose() {
        stop = true;
        traceLog("@%s: WorkDaemon interrupting", getName());
        interrupt();
        traceLog("@%s: WorkDaemon close", getName());
        close();
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
