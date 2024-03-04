//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2024 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
//                                                                                                                       ~
//  Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in       ~
//  compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0   ~
//  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on  ~
//  an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the   ~
//  specific language governing permissions and limitations under the License.                                           ~
//                                                                                                                       ~
//  Maintainers:                                                                                                         ~
//      Wim Bast, Tom Brus                                                                                               ~
//                                                                                                                       ~
//  Contributors:                                                                                                        ~
//      Ronald Krijgsheld ✝, Arjan Kok, Carel Bast                                                                       ~
// --------------------------------------------------------------------------------------------------------------------- ~
//  In Memory of Ronald Krijgsheld, 1972 - 2023                                                                          ~
//      Ronald was suddenly and unexpectedly taken from us. He was not only our long-term colleague and team member      ~
//      but also our friend. "He will live on in many of the lines of code you see below."                               ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.dclare;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.QualifiedSet;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.impl.HashCollectionImpl;
import org.modelingvalue.collections.util.Context;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.collections.util.StringUtil;
import org.modelingvalue.dclare.ex.NonDeterministicException;

@SuppressWarnings("rawtypes")
public class ConstantState {
    private static final Context<Boolean>                            WEAK    = Context.of(false);
    public static final Object                                       NULL    = new Object() {
                                                                                 @Override
                                                                                 public String toString() {
                                                                                     return "null";
                                                                                 }
                                                                             };
    private static final AtomicReferenceFieldUpdater<Constants, Map> UPDATOR = AtomicReferenceFieldUpdater.newUpdater(Constants.class, Map.class, "constants");

    private final ReferenceQueue<Object>                             queue   = new ReferenceQueue<>();
    private final AtomicReference<QualifiedSet<Object, Constants>>   state   = new AtomicReference<>(QualifiedSet.of(Constants::object));
    private final Thread                                             remover;
    private final String                                             name;
    private boolean                                                  stopRequested;

    private static final class ConstantDepthOverflowException extends RuntimeException {
        private static final long            serialVersionUID = -6980064786088373917L;

        private List<Pair<Object, Constant>> list             = List.of();

        public ConstantDepthOverflowException(Object object, Constant lazy) {
            addLazy(object, lazy);
        }

        private void addLazy(Object object, Constant lazy) {
            list = list.append(Pair.of(object, lazy));
        }

        @Override
        public String getMessage() {
            return "Depth overflow while deriving constants " + list;
        }
    }

    private interface Ref<O> {
        O get();

        void clear();

        Constants<O> constants();
    }

    public class Constants<O> {

        private class WeakRef extends WeakReference<O> implements Ref<O> {
            private WeakRef(O referent, ReferenceQueue<? super O> queue) {
                super(referent, queue);
            }

            @Override
            public Constants<O> constants() {
                return Constants.this;
            }
        }

        private class SoftRef extends SoftReference<O> implements Ref<O> {
            private SoftRef(O referent, ReferenceQueue<? super O> queue) {
                super(referent, queue);
            }

            @Override
            public Constants<O> constants() {
                return Constants.this;
            }
        }

        private class DurableRef implements Ref<O> {
            private O referent;

            private DurableRef(O referent) {
                this.referent = referent;
            }

            @Override
            public Constants<O> constants() {
                return Constants.this;
            }

            @Override
            public O get() {
                return referent;
            }

            @Override
            public void clear() {
                referent = null;
            }
        }

        public volatile Map<Constant<O, ?>, Object> constants;
        private final int                           hash;
        private Ref<O>                              ref;

        public Constants(O object, ReferenceType referenceType, ReferenceQueue<? super O> queue) {
            ref = referenceType == ReferenceType.weak ? new WeakRef(object, queue) : referenceType == ReferenceType.soft ? new SoftRef(object, queue) : new DurableRef(object);
            UPDATOR.lazySet(this, Map.of());
            hash = object.hashCode();
        }

        protected void upgradeStrongness(ReferenceType referenceType, O object) {
            ref = referenceType == ReferenceType.soft ? new SoftRef(object, queue) : new DurableRef(object);
        }

        public ReferenceType referenceType() {
            return ref instanceof ConstantState.Constants.WeakRef ? ReferenceType.weak : ref instanceof ConstantState.Constants.SoftRef ? ReferenceType.soft : ReferenceType.durable;
        }

        @SuppressWarnings("unchecked")
        public O object() {
            O o = ref.get();
            return o == null ? (O) this : o;
        }

        @SuppressWarnings("unchecked")
        public <V> V get(ConstantChangeHandler cch, O object, Constant<O, V> constant, Function<O, V> deriver) {
            Map<Constant<O, ?>, Object> prev = constants;
            V ist = (V) prev.get(constant);
            if (ist == null) {
                V soll = deriver == null ? constant.getDefault(object) : derive(cch, object, constant, deriver);
                ist = set(cch, object, constant, prev, soll == null ? (V) NULL : soll, false);
            }
            return ist == NULL ? null : ist;
        }

        public <V> boolean isSet(Constant<O, V> constant) {
            return constants.get(constant) != null;
        }

        @SuppressWarnings("unchecked")
        public <V> V set(ConstantChangeHandler cch, O object, Constant<O, V> constant, V soll, boolean forced) {
            Map<Constant<O, ?>, Object> prev = constants;
            V ist = (V) prev.get(constant);
            if (ist == null || forced) {
                ist = set(cch, object, constant, prev, soll == null ? (V) NULL : soll, forced);
            }
            if (!Objects.equals(ist == NULL ? null : ist, soll)) {
                throw new NonDeterministicException(object, constant, "Constant is not consistent " + StringUtil.toString(object) + "." + constant + "=" + StringUtil.toString(ist) + "!=" + StringUtil.toString(soll));
            }
            return constant.getDefault(object);
        }

        @SuppressWarnings("unchecked")
        public <V, E> V set(ConstantChangeHandler cch, O object, Constant<O, V> constant, BiFunction<V, E, V> function, E element) {
            Map<Constant<O, ?>, Object> prev = constants;
            V ist = (V) prev.get(constant);
            V soll = function.apply(ist, element);
            if (ist == null) {
                ist = set(cch, object, constant, prev, soll == null ? (V) NULL : soll, false);
            }
            if (!Objects.equals(ist == NULL ? null : ist, soll)) {
                throw new NonDeterministicException(object, constant, "Constant is not consistent " + StringUtil.toString(object) + "." + constant + "=" + StringUtil.toString(ist) + "!=" + StringUtil.toString(soll));
            }
            return ist == NULL ? null : ist;
        }

        @Override
        public boolean equals(Object o) {
            return o == this;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            O o = ref.get();
            return "Constants:" + (o != null ? o.getClass().getSimpleName() + "@" + hash : "@" + System.identityHashCode(this));
        }

        @SuppressWarnings("unchecked")
        private <V> V set(ConstantChangeHandler cch, O object, Constant<O, V> constant, Map<Constant<O, ?>, Object> prev, V soll, boolean forced) {
            V ist;
            Map<Constant<O, ?>, Object> next = prev.put(constant, soll);
            while (!UPDATOR.compareAndSet(this, prev, next)) {
                prev = constants;
                ist = (V) prev.get(constant);
                if (!forced && ist != null) {
                    return ist;
                }
                next = prev.put(constant, soll);
            }
            V def = constant.getDefault(object);
            if (!forced && !Objects.equals(def, soll == NULL ? null : soll)) {
                cch.changed(object, constant, def, def, soll == NULL ? null : soll);
            }
            return soll;
        }

        @SuppressWarnings({"unchecked", "resource"})
        private <V> V derive(ConstantChangeHandler cch, O object, Constant<O, V> constant, Function<O, V> deriver) {
            List<Pair<Object, Constant>> list = List.of();
            while (true) {
                try {
                    if (!list.isEmpty()) {
                        boolean weak = WEAK.get();
                        WEAK.setOnThread(true);
                        try {
                            for (Pair<Object, Constant> lazy : list) {
                                if (constant.equals(lazy.b()) && object.equals(lazy.a())) {
                                    Pair<Object, Constant> me = Pair.of(object, constant);
                                    throw new NonDeterministicException(object, constant, "Circular constant definition: " + list.sublist(list.lastIndexOf(me), list.size()).add(me));
                                }
                                ConstantState.this.get(cch, lazy.a(), lazy.b());
                            }
                        } finally {
                            WEAK.setOnThread(weak);
                        }
                    }
                    return Constant.DERIVED.get(Pair.of(object, constant), () -> deriver.apply(object));
                } catch (StackOverflowError soe) {
                    throw new ConstantDepthOverflowException(object, constant);
                } catch (ConstantDepthOverflowException lce) {
                    if (Constant.DERIVED.get() != null) {
                        lce.addLazy(object, constant);
                        throw lce;
                    } else {
                        list = list.prependList(lce.list);
                    }
                }
            }
        }
    }

    public ConstantState(String name, Consumer<Throwable> errorHandler) {
        this.name = name;
        remover = new Thread(() -> {
            while (!stopRequested) {
                try {
                    removeConstants(((Ref<?>) queue.remove()).constants());
                } catch (InterruptedException e) {
                    if (!stopRequested) {
                        errorHandler.accept(new Error("unexpected exception in ConstantState.remover Thread", e));
                    }
                }
            }
        }, "ConstantState.remover");
        remover.setDaemon(true);
        remover.start();
    }

    @Override
    public String toString() {
        return name;
    }

    public void stop() {
        stopRequested = true;
        remover.interrupt();
    }

    public <O, V> V get(ConstantChangeHandler cch, O object, Constant<O, V> constant) {
        return getConstants(cch, object, referenceType(constant)).get(cch, object, constant, constant.deriver());
    }

    public <O, V> O object(ConstantChangeHandler cch, O object) {
        return getConstants(cch, object, ReferenceType.weak).object();
    }

    public <O, V> V get(ConstantChangeHandler cch, O object, Constant<O, V> constant, Function<O, V> deriver) {
        return getConstants(cch, object, referenceType(constant)).get(cch, object, constant, deriver);
    }

    public <O, V> boolean isSet(ConstantChangeHandler cch, O object, Constant<O, V> constant) {
        return getConstants(cch, object, referenceType(constant)).isSet(constant);
    }

    public <O, V> V set(ConstantChangeHandler cch, O object, Constant<O, V> constant, V value, boolean forced) {
        return getConstants(cch, object, referenceType(constant)).set(cch, object, constant, value, forced);
    }

    public <O, V, E> V set(ConstantChangeHandler cch, O object, Constant<O, V> constant, BiFunction<V, E, V> deriver, E element) {
        return getConstants(cch, object, referenceType(constant)).set(cch, object, constant, deriver, element);
    }

    private <O, V> ReferenceType referenceType(Constant<O, V> constant) {
        return constant.isDurable() ? ReferenceType.durable : WEAK.get() ? ReferenceType.weak : ReferenceType.soft;
    }

    @SuppressWarnings("unchecked")
    private <O> Constants<O> getConstants(ConstantChangeHandler cch, O object, ReferenceType referenceType) {
        QualifiedSet<Object, Constants> prev = state.get();
        Constants constants = prev.get(object);
        if (constants == null) {
            object = cch.state().canonical(object);
            constants = new Constants<>(object, referenceType, queue);
            prev = pruneEqualHashes(object, prev);
            QualifiedSet<Object, Constants> next = prev.add(constants);
            Constants<O> now;
            while (!state.compareAndSet(prev, next)) {
                prev = state.get();
                now = prev.get(object);
                if (now != null) {
                    constants.ref.clear();
                    if (referenceType.strongness > constants.referenceType().strongness) {
                        now.upgradeStrongness(referenceType, object);
                    }
                    return now;
                }
                prev = pruneEqualHashes(object, prev);
                next = prev.add(constants);
            }
        } else if (referenceType.strongness > constants.referenceType().strongness) {
            constants.upgradeStrongness(referenceType, object);
        }
        return constants;
    }

    private <O> QualifiedSet<Object, Constants> pruneEqualHashes(O object, QualifiedSet<Object, Constants> prev) {
        Set<Constants> allWithEqualhash = prev.allWithEqualhash(object);
        while (allWithEqualhash.size() >= HashCollectionImpl.EQUAL_HASHCODE_WARNING_LEVEL - 4) {
            int i = 0;
            for (Constants c : allWithEqualhash) {
                if (!(c.ref instanceof Constants.DurableRef)) {
                    prev = removeConstants(c);
                    if (++i >= HashCollectionImpl.EQUAL_HASHCODE_WARNING_LEVEL / 2) {
                        break;
                    }
                }
            }
            allWithEqualhash = prev.allWithEqualhash(object);
        }
        return prev;
    }

    private QualifiedSet<Object, Constants> removeConstants(Constants constants) {
        QualifiedSet<Object, Constants> prev = state.get();
        Object object = constants.object();
        constants = prev.get(object);
        if (constants != null) {
            QualifiedSet<Object, Constants> next = prev.removeKey(object);
            while (!state.compareAndSet(prev, next)) {
                prev = state.get();
                if (prev.get(object) == null) {
                    return prev;
                }
                next = prev.removeKey(object);
            }
            return next;
        }
        return prev;
    }

    private static enum ReferenceType {
        durable(2),
        soft(1),
        weak(0);

        int strongness;

        ReferenceType(int strongness) {
            this.strongness = strongness;
        }
    }

}
