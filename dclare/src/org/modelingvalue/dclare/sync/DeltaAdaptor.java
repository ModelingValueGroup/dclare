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

package org.modelingvalue.dclare.sync;

import static org.modelingvalue.collections.util.TraceTimer.*;

import java.util.concurrent.*;
import java.util.function.*;

import org.modelingvalue.collections.*;
import org.modelingvalue.collections.util.*;
import org.modelingvalue.dclare.*;
import org.modelingvalue.dclare.sync.converter.*;

@SuppressWarnings({"rawtypes"})
public abstract class DeltaAdaptor implements Supplier<String>, Consumer<String>, SerializationHelper {
    private final String                                                             name;
    private final UniverseTransaction                                                tx;
    private final Predicate<Object>                                                  objectFilter;
    private final Predicate<Setable>                                                 setableFilter;
    private final Converter<Map<Object, Map<Setable, Pair<Object, Object>>>, String> deltaConverter;
    private final AdaptorThread                                                      adaptorThread;
    private final BlockingQueue<String>                                              deltaQueue = new ArrayBlockingQueue<>(10);

    public DeltaAdaptor(String name, UniverseTransaction tx, Predicate<Object> objectFilter, Predicate<Setable> setableFilter) {
        this.name = name;
        this.tx = tx;
        this.objectFilter = objectFilter;
        this.setableFilter = setableFilter;
        deltaConverter = getDeltaConverter();
        adaptorThread = new AdaptorThread(name);
        adaptorThread.start();
        tx.addImperative("sync-" + name, this::queueDelta, adaptorThread, true);
    }

    protected Converter<Map<Object, Map<Setable, Pair<Object, Object>>>, String> getDeltaConverter() {
        return Converter.concat(new ConvertStringDelta(this), new ConvertJson());
    }

    public Map<Object, Map<Setable, Pair<Object, Object>>> makeDelta(State pre, State post, @SuppressWarnings("unused") boolean last) {
        return pre.diff(post, objectFilter, setableFilter).toMap(e -> e);
    }

    /**
     * Apply the given delta to our model
     *
     * @param delta the delta to apply to our model
     */
    @Override
    public void accept(String delta) {
        traceLog("^^^DeltaAdaptor %s let thread exec the delta", name);
        adaptorThread.accept(() -> {
            traceLog("^^^DeltaAdaptor %s APPLY delta: %s", name, delta);
            applyAllDeltas(deltaConverter.convertBackward(delta));
        });
    }

    /**
     * Retrieve the delta's that happen in our model
     *
     * @return the delta that happened in our model
     */
    @Override
    public String get() {
        try {
            traceLog("^^^DeltaAdaptor %s wait for delta in queue...", name);
            String delta = deltaQueue.take();
            traceLog("^^^DeltaAdaptor %s new delta in queue", name);
            return delta;
        } catch (InterruptedException e) {
            throw new Error(e);
        }
    }

    /**
     * Serialize the delta and queue it for retrieval.
     *
     * @param pre  the pre state
     * @param post the post state
     * @param last indication if this is the last delta in a sequence
     */
    protected void queueDelta(State pre, State post, Boolean last) {
        String delta = deltaConverter.convertForward(makeDelta(pre, post, last));
        if (delta.isEmpty()) {
            traceLog("^^^DeltaAdaptor %s: new delta IGNORED  (%s) (q=%d)", name, (last ? "LAST" : "not last"), deltaQueue.size());
        } else {
            traceLog("^^^DeltaAdaptor %s: new delta to queue (%s) (q=%d) json=%s", name, (last ? "LAST" : "not last"), deltaQueue.size(), delta);
            try {
                deltaQueue.put(delta);
            } catch (InterruptedException e) {
                throw new Error(e);
            }
        }
    }

    public void applyAllDeltas(Map<Object, Map<Setable, Pair<Object, Object>>> delta) {
        delta.forEach((mutable, m) -> m.forEach((settable, pair) -> applyOneDelta((Mutable) mutable, settable, pair.b())));
    }

    @SuppressWarnings("unchecked")
    protected void applyOneDelta(Mutable mutable, Setable prop, Object value) {
        prop.set(mutable, value);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
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

    public boolean isBusy() {
        return adaptorThread.isBusy() || !deltaQueue.isEmpty() || tx.isHandling() || tx.numInQueue() != 0;
    }

    public boolean isBusy(StringBuilder explanation) {
        int l0 = explanation.length();
        if (adaptorThread.isBusy()) {
            explanation.append("adaptorThread busy, ");
        }
        if (!deltaQueue.isEmpty()) {
            explanation.append("deltaQueue not empty, ");
        }
        if (tx.isHandling()) {
            explanation.append("tx is handling, ");
        }
        if (tx.numInQueue() != 0) {
            explanation.append("tx queue not empty, ");
        }
        return explanation.length() != l0;
    }

    protected static class AdaptorThread extends Thread implements Consumer<Runnable> {
        protected final BlockingQueue<Runnable> runnableQueue = new ArrayBlockingQueue<>(10);
        private         boolean                 stop;
        private         boolean                 busy;
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
            traceLog("***DeltaAdaptor %s: wait for Runnable...", getName());
            busy = false;
            Runnable r = runnableQueue.take();
            busy = true;
            traceLog("***DeltaAdaptor %s: got Runnable...", getName());
            return r;
        }

        protected void handle(Runnable r) {
            r.run();
        }

        public boolean isBusy() {
            return !runnableQueue.isEmpty() || busy;
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
