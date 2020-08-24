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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.modelingvalue.dclare.Setable;
import org.modelingvalue.dclare.UniverseTransaction;

@SuppressWarnings({"rawtypes"})
public class DeltaAdaptor implements DeltaSupplier, DeltaConsumer {
    private final   String               name;
    protected final UniverseTransaction  tx;
    protected final AdaptorThread        adaptorThread;
    protected final BlockingQueue<Delta> deltaQueue = new ArrayBlockingQueue<>(10);

    public DeltaAdaptor(String name, UniverseTransaction tx, Predicate<Object> objectFilter, Predicate<Setable> setableFilter) {
        this.name = name;
        this.tx = tx;
        adaptorThread = makeThread(name);
        adaptorThread.start();
        tx.put(name, () -> tx.addImperative("sync", (pre, post, last) -> {
            Delta delta = new Delta(pre.diff(post, objectFilter, setableFilter));
            if (!delta.isEmpty()) {
                traceLog("+++DeltaAdaptor %s: new delta to queue (%s) (q=%d)", name, (last ? "LAST" : "not last"), deltaQueue.size());
                try {
                    deltaQueue.put(delta);
                } catch (InterruptedException e) {
                    throw new Error(e);
                }
            } else {
                traceLog("+++DeltaAdaptor %s: new delta IGNORED  (%s) (q=%d)%s", name, (last ? "LAST" : "not last"), deltaQueue.size(), delta);
            }
        }, adaptorThread, true));
    }

    protected AdaptorThread makeThread(String name) {
        return new AdaptorThread(name);
    }

    @Override
    public void accept(Delta delta) {
        traceLog("+++DeltaAdaptor %s let thread exec the delta: %s", name, delta);
        adaptorThread.accept(() -> {
            traceLog("+++DeltaAdaptor %s EXEC delta: %s", name, delta);
            delta.apply();
        });
    }

    @Override
    public Delta get() {
        try {
            traceLog("+++DeltaAdaptor %s wait for delta in queue...", name);
            Delta delta = deltaQueue.take();
            traceLog("+++DeltaAdaptor %s new delta in queue: %s", name, delta);
            return delta;
        } catch (InterruptedException e) {
            throw new Error(e);
        }
    }

    public void stop() {
        adaptorThread.stop = true;
    }

    public void interrupt() {
        adaptorThread.interrupt();
    }

    public void join() {
        adaptorThread.join_();
    }

    public Throwable getThrowable() {
        return adaptorThread.getThrowable();
    }

    protected static class AdaptorThread extends Thread implements Consumer<Runnable> {
        protected final BlockingQueue<Runnable> runnableQueue = new ArrayBlockingQueue<>(10);
        private         boolean                 stop;
        private         Throwable               throwable;

        public AdaptorThread(String name) {
            super("adaptor-" + name);
        }

        @Override
        public void run() {
            traceLog("***DeltaAdaptor %s: START", getName());
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
            traceLog("***DeltaAdaptor %s: STOP", getName());
        }

        @Override
        public void accept(Runnable r) {
            try {
                traceLog("***DeltaAdaptor %s: queue new Runnable", getName());
                runnableQueue.put(r);
            } catch (InterruptedException e) {
                throw new Error("unexpected interrupt", e);
            }
        }

        protected Runnable next() throws InterruptedException {
            return runnableQueue.take();
        }

        protected void handle(Runnable r) {
            r.run();
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
