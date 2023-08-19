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

import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.ContextThread;
import org.modelingvalue.collections.util.ContextThread.ContextPool;
import org.modelingvalue.collections.util.MutationWrapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.stream.Collectors;

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
    private static final boolean                                     TRACE_ONE_SHOT    = Boolean.getBoolean("TRACE_ONE_SHOT");
    private static final MutationWrapper<Map<Class<?>, StateMap>>    STATE_MAP_CACHE   = new MutationWrapper<>(Map.of());
    private static final MutationWrapper<Map<Class<?>, Set<Method>>> ALL_METHODS_CACHE = new MutationWrapper<>(Map.of());
    private static       ContextPool                                 CONTEXT_POOL;

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

        List<String> cachingMethods = getAllMethodsOf(cacheKey).filter(m -> m.getAnnotation(OneShotAction.class).caching()).map(Method::getName).toList();
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
        synchronized (OneShot.class) {
            if (CONTEXT_POOL == null) {
                CONTEXT_POOL = ContextThread.createPool();
            }
        }
        return CONTEXT_POOL;
    }

    public void shutdownContextPool(ContextPool contextPool) {
        if (contextPool != CONTEXT_POOL) {
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
                    trace("TRACE_ONE_SHOT: %-6s %-40s actions=[%s]\n", "START", cacheKey.getSimpleName(), allActions.map(a -> a.id().toString()).collect(Collectors.joining(", ")));
                    allActions.forEach(a -> a.putAndWaitForIdle(universeTransaction));
                    universeTransaction.stop();
                    endState = universeTransaction.waitForEnd();
                    trace("TRACE_ONE_SHOT: %-6s %-40s duration=%5d ms\n", "DONE", cacheKey.getSimpleName(), System.currentTimeMillis() - t0);
                } finally {
                    shutdownContextPool(contextPool);
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
    private List<MyAction> getAllActions
    (
            boolean runningFromCache) {
        return getAllMethodsOf(cacheKey).map(method -> new MyAction(method, runningFromCache)).sorted(Comparator.comparing(a -> a.id().toString())).toList();
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
            if (!skip) {
                State intermediateState = universeTransaction.putAndWaitForIdle(this);
                if (writeResultToCache) {
                    STATE_MAP_CACHE.update(a -> a.computeIfAbsent(cacheKey, __ -> intermediateState.getStateMap()));
                }
            }
            trace("TRACE_ONE_SHOT: %-6s %-40s     %-40s (%5d ms)%s%s\n", "ACTION", cacheKey.getSimpleName(), id(), System.currentTimeMillis() - t00, writeResultToCache ? " CACHE-WRITE" : "", skip ? " CACHE-SKIP" : "");
        }
    }

    private static Set<Method> getAllMethodsOf
            (Class<?> clazz) {
        return ALL_METHODS_CACHE.updateAndGet(a -> a.computeIfAbsent(clazz, __ -> computeAllMethodsOf(clazz))).get(clazz);
    }

    private static Set<Method> computeAllMethodsOf
            (Class<?> clazz) {
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
        return map.toValues().toSet();
    }

    private static void trace
            (String
                     format, Object...
                                     args) {
        if (TRACE_ONE_SHOT) {
            System.err.printf(format, args);
        }
    }
}
