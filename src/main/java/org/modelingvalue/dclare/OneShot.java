//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2023 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

package org.modelingvalue.dclare;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.ContextThread;
import org.modelingvalue.collections.util.ContextPool;
import org.modelingvalue.collections.util.MutationWrapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class will enable you to build a model in a dclare universe and then finish.
 * All methods marked with {@link OneShotAction} will be executed in alphabetical order.
 * <p>
 * You can mark the first method with {@link OneShotAction#caching()} to cache the state after running the method the first time.
 * This state will be the start state at the next invocation of this class and the method can (and will) be skipped.
 * This is meant for setting up a constant state in the universe that is used every time.
 *
 * @param <U> the Universe class for this repo
 */
@SuppressWarnings("unused")
public abstract class OneShot<U extends Universe> {
    private static final boolean                                     TRACE_ONE_SHOT         = Boolean.getBoolean("TRACE_ONE_SHOT");
    private static final String                                      TRACE_ONE_SHOT_OBJECT  = System.getProperty("TRACE_ONE_SHOT_OBJECT");
    private static final String                                      TRACE_ONE_SHOT_SETABLE = System.getProperty("TRACE_ONE_SHOT_SETABLE");
    private static final MutationWrapper<Map<Class<?>, StateMap>>    STATE_MAP_CACHE        = new MutationWrapper<>(Map.of());
    private static final MutationWrapper<Map<Class<?>, Set<Method>>> ALL_METHODS_CACHE      = new MutationWrapper<>(Map.of());
    private static final ContextPoolPool                             CONTEXT_POOL_POOL      = new ContextPoolPool();

    private final Class<?> cacheKey = getClass();
    private final U        universe;
    private       State    endState;

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface OneShotAction {
        boolean caching() default false;
    }


    @SuppressWarnings("DataFlowIssue")
    public OneShot(U universe) {
        this.universe = universe;

        List<String> cachingMethods = getAllMethodsOf(cacheKey).filter(m -> m.getAnnotation(OneShotAction.class).caching()).map(Method::getName).asList();
        if (1 < cachingMethods.count()) {
            throw new IllegalStateException("the oneshot " + cacheKey.getSimpleName() + " has too many caching actions: " + cachingMethods.collect(Collectors.joining(", ")));
        }
    }

    /**
     * get the universe passed at creation.
     *
     * @return the universe
     */
    public U getUniverse() {
        return universe;
    }

    /**
     * overrule where needed
     *
     * @return the config
     */
    public DclareConfig getConfig() {
        return new DclareConfig();
    }

    /**
     * overrule where needed
     *
     * @return the ContextPool that was created
     */
    public ContextPool getContextPool() {
        return CONTEXT_POOL_POOL.getContextPool();
    }

    public void doneWithContextPool(ContextPool contextPool) {
        if (!CONTEXT_POOL_POOL.doneWithContextPool(contextPool)) {
            contextPool.shutdownNow();
        }
    }

    public StateMap getEndStateMap() {
        return getEndState().getStateMap();
    }

    public void clearCache() {
        STATE_MAP_CACHE.update(m -> m.remove(cacheKey));
    }

    /**
     * get the end state after all actions are done.
     * if the actions have not run yet they will first be executed.
     *
     * @return the end state
     */
    @SuppressWarnings("DataFlowIssue")
    public State getEndState() {
        if (endState != null) {
            return endState;
        }
        synchronized (this) {
            if (endState == null) {
                ContextPool contextPool = getContextPool();
                try {
                    StateMap            cachedStateMap      = STATE_MAP_CACHE.get().get(cacheKey);
                    boolean             runningFromCache    = cachedStateMap != null;
                    UniverseTransaction universeTransaction = new UniverseTransaction(getUniverse(), contextPool, getConfig(), null, cachedStateMap);
                    long                t0                  = System.currentTimeMillis();
                    List<MyAction>      allActions          = getAllActions(runningFromCache);
                    trace("START", "#actions=%d", allActions.size());
                    allActions.forEach(a -> a.putAndWaitForIdle(universeTransaction));
                    universeTransaction.stop();
                    endState = universeTransaction.waitForEnd();
                    trace("DONE", "duration=%5d ms", System.currentTimeMillis() - t0);
                } finally {
                    doneWithContextPool(contextPool);
                }
            }
            return endState;
        }
    }

    /**
     * Get all the actions that should be run: all methods starting with 'action' and having no parameters and no result.
     *
     * @return the list of actions to perform
     */
    private List<MyAction> getAllActions(boolean runningFromCache) {
        return getAllMethodsOf(cacheKey).map(method -> new MyAction(method, runningFromCache)).sorted(Comparator.comparing(a -> a.id().toString())).asList();
    }

    private class MyAction extends Action<Universe> {
        private final boolean runningFromCache;
        private final boolean isCachingMethod;

        protected MyAction(Method method, boolean runningFromCache) {
            super("~~" + method.getName(), (u) -> {
                try {
                    method.invoke(OneShot.this);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new Error(e);
                }
            });
            isCachingMethod       = method.getAnnotation(OneShotAction.class).caching();
            this.runningFromCache = runningFromCache;
        }

        protected void putAndWaitForIdle(UniverseTransaction universeTransaction) {
            long    t00                = System.currentTimeMillis();
            boolean writeResultToCache = isCachingMethod && !runningFromCache;
            boolean skip               = isCachingMethod && runningFromCache;
            if (skip) {
                trace(" CACHE-SKIP", "%s", id());
            } else {
                trace(" >>ACTION", "%s", id());
                State intermediateState = universeTransaction.putAndWaitForIdle(this);
                traceDiff(intermediateState);
                trace(" <<ACTION", "%s duration=%5d ms\n", id(), System.currentTimeMillis() - t00);
                if (writeResultToCache) {
                    trace(" CACHE-WRITE", "%s", id());
                    STATE_MAP_CACHE.update(a -> a.computeIfAbsent(cacheKey, __ -> intermediateState.getStateMap()));
                }
            }
        }

        private static void traceDiff(State intermediateState) {
            if (TRACE_ONE_SHOT_OBJECT != null || TRACE_ONE_SHOT_SETABLE != null) {
                Predicate<String> objPred = TRACE_ONE_SHOT_OBJECT == null ? s -> true : Pattern.compile(TRACE_ONE_SHOT_OBJECT).asPredicate();
                Predicate<String> setPred = TRACE_ONE_SHOT_SETABLE == null ? s -> true : Pattern.compile(TRACE_ONE_SHOT_SETABLE).asPredicate();
                System.err.println("******************************State*************************************");
                System.err.println(intermediateState.universeTransaction().emptyState().diffString(intermediateState, o -> objPred.test(o.toString()), s -> s.isTraced() && setPred.test(s.toString())));
                System.err.println("************************************************************************");
            }
        }
    }

    private static Set<Method> getAllMethodsOf(Class<?> clazz) {
        return ALL_METHODS_CACHE.updateAndGet(a -> a.computeIfAbsent(clazz, __ -> computeAllMethodsOf(clazz))).get(clazz);
    }

    private static Set<Method> computeAllMethodsOf(Class<?> clazz) {
        Map<String, Method> map = Map.of();
        for (Class<?> c = clazz; c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(OneShotAction.class) //
                    && m.getParameterCount() == 0//
                    && m.getReturnType().equals(void.class)//
                    && Modifier.isPublic(m.getModifiers())//
                    && !map.containsKey(m.getName())) {//
                    map = map.put(m.getName(), m);
                }
            }
        }
        return map.toValues().asSet();
    }

    private void trace(String what, String format, Object... args) {
        if (TRACE_ONE_SHOT) {
            System.err.printf("TRACE_ONE_SHOT: %-14s %-40s  -  %s\n", what, cacheKey.getSimpleName(), String.format(format, args));
        }
    }

    private static class ContextPoolPool {
        private static final boolean                              NO_POOL_POOL_TRACE             = Boolean.getBoolean("NO_POOL_POOL_TRACE");
        private static final int                                  POOL_POOL_SIZE                 = Integer.getInteger("POOL_POOL_SIZE", Collection.PARALLELISM);
        private static final int                                  POOL_POOL_ALARM_THRESHOLD_SEC  = Integer.getInteger("POOL_POOL_ALARM_THRESHOLD_SEC", 30);
        private static final int                                  POOL_POOL_AQUIRE_TIMEOUT_SEC   = Integer.getInteger("POOL_POOL_AQUIRE_TIMEOUT_SEC", 30);
        private static final int                                  POOL_POOL_MONITOR_INTERVAL_SEC = Integer.getInteger("POOL_POOL_MONITOR_INTERVAL_SEC", 60);
        //
        private final        BlockingQueue<PoolInfo>              idleQueue                      = new LinkedBlockingQueue<>(makePools());
        private final        BlockingQueue<PoolInfo>              busyQueue                      = new LinkedBlockingQueue<>();
        private final        AtomicReference<PoolPoolInfo>        poolPoolInfo                   = new AtomicReference<>(new PoolPoolInfo());
        private final        java.util.Map<ContextPool, PoolInfo> poolInfoMap;

        private static java.util.Collection<PoolInfo> makePools() {
            return IntStream.range(0, POOL_POOL_SIZE)
                            .mapToObj(i -> new PoolInfo())
                            .toList();
        }

        public ContextPoolPool() {
            trace("INIT");
            poolInfoMap = makePoolInfoMap();
            new PoolPoolMonitor(this).start();
        }

        private java.util.Map<ContextPool, PoolInfo> makePoolInfoMap() {
            return idleQueue.stream().collect(Collectors.toMap(info -> info.pool, info -> info));
        }

        public ContextPool getContextPool() {
            try {
                PoolPoolInfo.preUpdate(poolPoolInfo);
                trace("get");
                long     t0   = System.nanoTime();
                PoolInfo info = idleQueue.poll(POOL_POOL_AQUIRE_TIMEOUT_SEC, TimeUnit.SECONDS);
                long     dt   = (System.nanoTime() - t0) / 1_000_000;
                if (info == null) {
                    trace("timeout");
                    throw new RuntimeException("timeout after " + dt + " ms while waiting for ContextPool");
                }
                PoolPoolInfo.postUpdate(poolPoolInfo, dt);
                info.start();
                busyQueue.add(info);
                trace(String.format("waited %6d ms", dt));
                return info.pool;
            } catch (InterruptedException e) {
                throw new RuntimeException("interrupted while waiting for ContextPool", e);
            }
        }

        public boolean doneWithContextPool(ContextPool pool) {
            PoolInfo info = poolInfoMap.get(pool);
            if (info == null) {
                return false;
            }
            if (!busyQueue.remove(info)) {
                trace("pool not busy");
                throw new IllegalStateException("pool not busy");
            }
            info.stop();
            idleQueue.add(info);
            trace("done");
            return true;
        }

        private void trace(String msg) {
            if (!NO_POOL_POOL_TRACE) {
                System.err.printf("TRACE: ContextPoolPool: [%-25s] %-25s: idle/busy=%3d/%3d: %s\n",
                                  Thread.currentThread().getName(),
                                  msg,
                                  idleQueue.size(),
                                  busyQueue.size(),
                                  poolPoolInfo.get());
            }
        }

        private void check() {
            for (PoolInfo info : poolInfoMap.values()) {
                if (info.busy) {
                    long duration = info.duration();
                    if (POOL_POOL_ALARM_THRESHOLD_SEC * 1000L < duration) {
                        System.err.printf("ALARM: ContextPool probably stuck (busy for %-8d ms): %s\n", duration, info.pool);
                    }
                }
            }
        }

        private static class PoolPoolMonitor extends Thread {
            private final ContextPoolPool contextPoolPool;

            private PoolPoolMonitor(ContextPoolPool contextPoolPool) {
                super("PoolPoolMonitor");
                setDaemon(true);
                this.contextPoolPool = contextPoolPool;
            }

            @SuppressWarnings("BusyWait")
            @Override
            public void run() {
                for (; ; ) {
                    try {
                        Thread.sleep(POOL_POOL_MONITOR_INTERVAL_SEC * 1000L);
                        contextPoolPool.check();
                    } catch (InterruptedException e) {
                        System.err.println("WARNING PoolPoolMonitor interrupted!");
                        return;
                    }
                }
            }
        }

        private static class PoolInfo {
            private final ContextPool pool;
            private       boolean     busy;
            private       long        startTick;
            private       long        lastDuration;

            public PoolInfo() {
                pool = ContextThread.createPool();
                // No-op tasks, just to get all Threads started
                for (int i = 0; i < pool.getParallelism(); i++) {
                    pool.execute(() -> {
                    });
                }
            }

            public void start() {
                busy         = true;
                lastDuration = 0;
                startTick    = System.currentTimeMillis();
            }

            public void stop() {
                busy         = false;
                lastDuration = duration();
                startTick    = 0;
            }

            public long duration() {
                long t = startTick;
                return t == 0 ? 0 : System.currentTimeMillis() - t;
            }
        }

        private static class PoolPoolInfo {
            private final long gets;
            private final long immediates;
            private final long waits;
            private final long totalWaitTime;
            private final long maxWaitTime;

            public PoolPoolInfo() {
                this.gets          = 0;
                this.immediates    = 0;
                this.waits         = 0;
                this.totalWaitTime = 0;
                this.maxWaitTime   = 0;
            }

            public static void preUpdate(AtomicReference<PoolPoolInfo> poolPoolInfo) {
                update(poolPoolInfo, info -> new PoolPoolInfo(info.gets + 1, info.immediates, info.waits, info.totalWaitTime, info.maxWaitTime));
            }

            public static void postUpdate(AtomicReference<PoolPoolInfo> poolPoolInfo, long dt) {
                update(poolPoolInfo, info -> new PoolPoolInfo(info.gets, info.immediates + (0 == dt ? 1 : 0), info.waits + (0 == dt ? 0 : 1), info.totalWaitTime + dt, Math.max(info.maxWaitTime, dt)));
            }

            private static void update(AtomicReference<PoolPoolInfo> poolPoolInfo, Function<PoolPoolInfo, PoolPoolInfo> f) {
                for (; ; ) {
                    PoolPoolInfo oldInfo = poolPoolInfo.get();
                    PoolPoolInfo newInfo = f.apply(oldInfo);
                    if (poolPoolInfo.compareAndSet(oldInfo, newInfo)) {
                        break;
                    }
                }
            }

            private PoolPoolInfo(long gets, long immediates, long waits, long totalWaitTime, long maxWaitTime) {
                this.gets          = gets;
                this.immediates    = immediates;
                this.waits         = waits;
                this.totalWaitTime = totalWaitTime;
                this.maxWaitTime   = maxWaitTime;
            }

            @Override
            public String toString() {
                return String.format("%4d gets (%4d immediates %4d waits %8d ms totalWait, %8d ms max-wait)",
                                     gets,
                                     immediates,
                                     waits,
                                     totalWaitTime,
                                     maxWaitTime);
            }
        }
    }
}
