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

import java.util.concurrent.*;
import java.util.function.*;

import org.modelingvalue.collections.*;
import org.modelingvalue.collections.util.*;
import org.modelingvalue.dclare.*;
import org.modelingvalue.dclare.sync.converter.*;

@SuppressWarnings({"rawtypes"})
public abstract class DeltaAdaptor<T> implements Supplier<T>, Consumer<T>, SerializationHelper {
    private final UniverseTransaction                                           tx;
    private final boolean                                                       noDeltasOut;
    private final Predicate<Object>                                             objectFilter;
    private final Predicate<Setable>                                            setableFilter;
    private final Converter<Map<Object, Map<Setable, Pair<Object, Object>>>, T> deltaConverter;
    private final AdaptorDaemon                                                 adaptorThread;
    private final BlockingQueue<T>                                              deltaQueue = new ArrayBlockingQueue<>(10);

    public DeltaAdaptor(String name, UniverseTransaction tx, boolean noDeltasOut, Predicate<Object> objectFilter, Predicate<Setable> setableFilter, Converter<java.util.Map<String, java.util.Map<String, String>>, T> converter) {
        this.tx = tx;
        this.noDeltasOut = noDeltasOut;
        this.objectFilter = objectFilter;
        this.setableFilter = setableFilter;
        deltaConverter = Converter.concat(new ConvertStringDelta(this), converter);
        adaptorThread = new AdaptorDaemon("adaptor-" + name);
        adaptorThread.start();
        tx.addImperative("sync-" + name, this::queueDelta, adaptorThread, true);
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
    public void accept(T delta) {
        adaptorThread.accept(() -> applyAllDeltas(deltaConverter.convertBackward(delta)));
    }

    /**
     * Retrieve the delta's that happen in our model
     *
     * @return the delta that happened in our model
     */
    @Override
    public T get() {
        try {
            return deltaQueue.take();
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
        if (!noDeltasOut) {
            Map<Object, Map<Setable, Pair<Object, Object>>> map = makeDelta(pre, post, last);
            if (!map.isEmpty()) {
                try {
                    deltaQueue.put(deltaConverter.convertForward(map));
                } catch (InterruptedException e) {
                    throw new Error(e);
                }
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
    public void forceStop() {
        adaptorThread.forceStop();
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

    protected static class AdaptorDaemon extends WorkDaemon<Runnable> implements Consumer<Runnable> {
        private final BlockingQueue<Runnable> runnableQueue = new ArrayBlockingQueue<>(10);

        public AdaptorDaemon(String name) {
            super(name);
        }

        @Override
        protected Runnable waitForWork() throws InterruptedException {
            return runnableQueue.take();
        }

        @Override
        protected void execute(Runnable r) {
            r.run();
        }

        @Override
        public void accept(Runnable r) {
            try {
                runnableQueue.put(r);
            } catch (InterruptedException e) {
                throw new Error("unexpected interrupt", e);
            }
        }

        public boolean isBusy() {
            return super.isBusy() || !runnableQueue.isEmpty();
        }
    }
}
