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
import org.modelingvalue.dclare.sync.json.JsonIC.*;

public class DeltaAdaptor<C extends MutableClass, M extends Mutable, S extends Setable<M, Object>> implements SupplierAndConsumer<String> {
    private final String                       name;
    private final UniverseTransaction          tx;
    private final SerializationHelper<C, M, S> helper;
    private final AdaptorDaemon                adaptorDaemon;
    private final BlockingQueue<String>        deltaQueue = new ArrayBlockingQueue<>(10);

    public DeltaAdaptor(String name, UniverseTransaction tx, SerializationHelper<C, M, S> helper) {
        this.name = name;
        this.tx = tx;
        this.helper = helper;
        adaptorDaemon = new AdaptorDaemon("adaptor-" + name);
        adaptorDaemon.start();
        tx.addImperative("sync-" + name, this::queueDelta, adaptorDaemon, true);
    }

    /**
     * When a delta is received from a remote party it can be given to the local model through this method.
     * The delta will be queued and applied to the model async but in order of arrival.
     *
     * @param delta the delta to apply to our model
     */
    @Override
    public void accept(String delta) {
        adaptorDaemon.accept(() ->
        {
            try {
                new DeltaFromJson().fromJson(delta);
            } catch (Throwable e) {
                throw new Error(e);
            }
        });
    }

    protected void applyOneDelta(M mutable, S settable, Object value) {
        settable.set(mutable, value);
    }

    /**
     * Retrieve the delta's that happen in our model to send to a remote party.
     *
     * @return the next delta that happened in our model
     */
    @Override
    public String get() {
        try {
            return deltaQueue.take();
        } catch (InterruptedException e) {
            throw new Error(e);
        }
    }

    /**
     * Serialize the delta coming from the local model and queue it for async retrieval through get().
     *
     * @param pre  the pre state
     * @param post the post state
     * @param last indication if this is the last delta in a sequence
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void queueDelta(State pre, State post, Boolean last) {
        Map<Object, Map<Setable, Pair<Object, Object>>> deltaMap = pre.diff(post, getObjectFilter(), (Predicate<Setable>) (Object) helper.setableFilter()).toMap(e1 -> e1);
        if (!deltaMap.isEmpty()) {
            try {
                deltaQueue.put(new DeltaToJson().toJson(deltaMap));
            } catch (InterruptedException e) {
                throw new Error(e);
            }
        }
    }

    private Predicate<Object> getObjectFilter() {
        return o -> o instanceof Mutable && helper.mutableFilter().test((Mutable) o);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public String getName() {
        return name;
    }

    public AdaptorDaemon getAdaptorDaemon() {
        return adaptorDaemon;
    }

    public boolean isBusy() {
        return adaptorDaemon.isBusy() || !deltaQueue.isEmpty() || (!tx.isStopped() && (tx.isHandling() || tx.numInQueue() != 0));
    }

    public boolean isBusy(StringBuilder explanation) {
        int l0 = explanation.length();
        if (adaptorDaemon.isBusy()) {
            explanation.append("adaptorThread busy, ");
        }
        if (!deltaQueue.isEmpty()) {
            explanation.append("deltaQueue not empty, ");
        }
        if (tx.isStopped()) {
            explanation.append("tx is stopped");
        } else {
            if (tx.isHandling()) {
                explanation.append("tx is handling, ");
            }
            if (tx.numInQueue() != 0) {
                explanation.append("tx queue not empty (").append(tx.numInQueue()).append(")");
            }
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

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private class DeltaToJson extends ToJsonIC {
        private M      currentMutable;
        private S      currentSetable;
        private Object currentOldValue;
        private Object currentNewValue;

        @Override
        protected Object filter(Object o) {
            if (getLevel() != 3) {
                if (o instanceof Mutable) {
                    return helper.serializeMutable((M) o);
                } else if (o instanceof Setable) {
                    return helper.serializeSetable((S) o);
                } else {
                    return o;
                }
            }
            if (getIndex() == 0) {
                currentOldValue = o;
                return null;
            }
            if (getIndex() == 1) {
                currentNewValue = o;
                return helper.serializeValue(currentSetable, currentNewValue);
            }
            throw new Error("bad delta format");
        }

        @Override
        protected String stringFromKey(Object keyObj) {
            String key;
            if (getLevel() == 1) {
                if (!(keyObj instanceof Mutable)) {
                    throw new Error("bad delta format");
                }
                //noinspection unchecked
                currentMutable = (M) keyObj;
                key = helper.serializeMutable(currentMutable);
            } else if (getLevel() == 2) {
                if (!(keyObj instanceof Setable)) {
                    throw new Error("bad delta format");
                }
                //noinspection unchecked
                currentSetable = (S) keyObj;
                key = helper.serializeSetable(currentSetable);
            } else {
                key = super.stringFromKey(keyObj);
            }
            return key;
        }
    }

    @SuppressWarnings("unused")
    private class DeltaFromJson extends FromJsonIC {
        private M      currentMutable;
        private S      currentSetable;
        private Object currentOldValue;
        private Object currentNewValue;

        @Override
        protected Map<String, Object> makeMap() {
            return getLevel() < 2 ? null : super.makeMap();
        }

        @Override
        protected String makeMapKey(String key) {
            switch (getLevel()) {
            case 1:
                currentMutable = helper.deserializeMutable(key);
                break;
            case 2:
                //noinspection unchecked
                currentSetable = helper.deserializeSetable((C) currentMutable.dClass(), key);
                break;
            }
            return super.makeMapKey(key);
        }

        @Override
        protected List<Object> makeArray() {
            return getLevel() <= 2 ? null : super.makeArray();
        }

        @Override
        protected List<Object> makeArrayEntry(List<Object> l, Object o) {
            if (l != null) {
                return super.makeArrayEntry(l, o);
            }
            switch (getIndex()) {
            case 0:
                //currentOldValue = helper.deserializeValue(currentSetable, o);
                break;
            case 1:
                currentNewValue = helper.deserializeValue(currentSetable, o);
                break;
            }
            return null;
        }

        @Override
        protected Object closeArray(List<Object> l) {
            if (l != null) {
                return super.closeArray(l);
            } else {
                applyOneDelta(currentMutable, currentSetable, currentNewValue);
                return null;
            }
        }
    }
}
