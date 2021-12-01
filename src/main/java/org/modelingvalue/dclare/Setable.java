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

package org.modelingvalue.dclare;

import static org.modelingvalue.dclare.CoreSetableModifier.symmetricOpposite;

import java.util.function.*;

import org.modelingvalue.collections.*;
import org.modelingvalue.collections.util.*;
import org.modelingvalue.dclare.ex.*;

public class Setable<O, T> extends Getable<O, T> {

    private static final Context<Boolean> MOVING = Context.of(false);

    public static <C, V> Setable<C, V> of(Object id, V def, SetableModifier... modifiers) {
        return new Setable<>(id, def, null, null, null, modifiers);
    }

    public static <C, V> Setable<C, V> of(Object id, V def, QuadConsumer<LeafTransaction, C, V, V> changed, SetableModifier... modifiers) {
        return new Setable<>(id, def, null, null, changed, modifiers);
    }

    public static <C, V> Setable<C, V> of(Object id, V def, Supplier<Setable<?, ?>> opposite, SetableModifier... modifiers) {
        return new Setable<>(id, def, opposite, null, null, modifiers);
    }

    public static <C, V> Setable<C, V> of(Object id, V def, QuadConsumer<LeafTransaction, C, V, V> changed, Supplier<Setable<C, Set<?>>> scope, SetableModifier... modifiers) {
        return new Setable<>(id, def, null, scope, changed, modifiers);
    }

    public static <C, V> Setable<C, V> of(Object id, V def, Supplier<Setable<?, ?>> opposite, Supplier<Setable<C, Set<?>>> scope, SetableModifier... modifiers) {
        return new Setable<>(id, def, opposite, scope, null, modifiers);
    }

    private final QuadConsumer<LeafTransaction, O, T, T> changed;
    private final boolean                                containment;
    private final Supplier<Setable<?, ?>>                opposite;
    private final Supplier<Setable<O, Set<?>>>           scope;
    @SuppressWarnings("rawtypes")
    private final Constant<T, Entry<Setable, Object>>    internal;
    private final boolean                                plumbing;
    private final boolean                                synthetic;

    private Boolean                                      isReference;
    private Constant<O, T>                               constant;

    protected Setable(Object id, T def, Supplier<Setable<?, ?>> opposite, Supplier<Setable<O, Set<?>>> scope, QuadConsumer<LeafTransaction, O, T, T> changed, SetableModifier... modifiers) {
        super(id, def);
        this.plumbing = CoreSetableModifier.plumbing.in(modifiers);
        this.containment = CoreSetableModifier.containment.in(modifiers);
        this.synthetic = CoreSetableModifier.synthetic.in(modifiers);
        this.changed = changed;
        if (symmetricOpposite.in(modifiers)) {
            if (opposite != null) {
                throw new Error("The setable " + this + " is already a symetric-opposite");
            } else {
                this.opposite = () -> this;
            }
        } else {
            this.opposite = opposite;
        }
        this.scope = scope;
        if (containment && opposite != null) {
            throw new Error("The containment setable " + this + " has an opposite");
        }
        this.internal = this instanceof Constant ? null : Constant.of(Pair.of(this, "internalEntry"), v -> Entry.of(this, v));
    }

    @SuppressWarnings("rawtypes")
    protected Entry<Setable, Object> entry(T value, DefaultMap<Setable, Object> properties) {
        if (value != null && Internable.isInternable(value)) {
            return internal.get(value);
        } else {
            Entry<Setable, Object> entry = Entry.of(this, value);
            if (properties != null) {
                deduplicate(entry, properties);
            }
            return entry;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void deduplicate(Entry e1, DefaultMap<?, ?> map2) {
        TraceTimer.traceBegin("deduplicate");
        try {
            Object v1 = e1.getValue();
            if (v1 instanceof DefaultMap) {
                if (((DefaultMap<?, ?>) v1).size() < 100) {
                    for (Entry e3 : (DefaultMap<?, ?>) v1) {
                        deduplicate(e3, map2);
                    }
                }
            } else if (map2.size() < 100) {
                for (Entry e2 : map2) {
                    Object v2 = e2.getValue();
                    if (v2 instanceof DefaultMap) {
                        deduplicate(e1, (DefaultMap) v2);
                    } else {
                        e1.setValueIfEqual(v2);
                    }
                }
            }
        } finally {
            TraceTimer.traceEnd("deduplicate");
        }

    }

    public boolean isReference() {
        return isReference != null && isReference;
    }

    protected Constant<O, T> constant() {
        if (constant == null) {
            constant = Constant.of(this, def);
        }
        return constant;
    }

    @Override
    public boolean synthetic() {
        return synthetic;
    }

    @Override
    public boolean containment() {
        return containment;
    }

    @Override
    public Setable<?, ?> opposite() {
        return opposite != null ? opposite.get() : null;
    }

    @Override
    public Setable<O, Set<?>> scope() {
        return scope != null ? scope.get() : null;
    }

    protected boolean isHandlingChange() {
        return changed != null || containment || opposite != null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected final void changed(LeafTransaction tx, O object, T preValue, T postValue) {
        init(postValue);
        if (changed != null) {
            changed.accept(tx, object, preValue, postValue);
        }
        if (containment) {
            Setable.<T, Mutable> diff(preValue, postValue, added -> {
                Pair<Mutable, Setable<Mutable, ?>> prePair = Mutable.D_PARENT_CONTAINING.get(added);
                if (prePair != null) {
                    MOVING.run(true, () -> prePair.b().remove(prePair.a(), added));
                }
                Mutable.D_PARENT_CONTAINING.set(added, Pair.of((Mutable) object, (Setable<Mutable, ?>) this));
                if (prePair == null) {
                    added.dActivate();
                } else {
                    Priority.forward.children.set((Mutable) object, Set::add, added);
                }
            }, removed -> {
                for (Priority dir : Priority.values()) {
                    dir.children.set((Mutable) object, Set::remove, removed);
                }
                if (!MOVING.get()) {
                    Mutable.D_PARENT_CONTAINING.setDefault(removed);
                    removed.dHandleRemoved((Mutable) object);
                }
            });
        } else if (opposite != null) {
            Setable<Object, ?> opp = (Setable<Object, ?>) opposite.get();
            Setable.diff(preValue, postValue, //
                    added -> opp.add(added, object), //
                    removed -> opp.remove(removed, object));
        }
    }

    protected void init(T postValue) {
        if (isReference == null) {
            Object element = postValue;
            if (element instanceof ContainingCollection) {
                element = ((ContainingCollection<?>) element).isEmpty() ? null : ((ContainingCollection<?>) element).get(0);
            }
            if (element != null) {
                isReference = element instanceof Mutable && !containment && this != Mutable.D_PARENT_CONTAINING;
            }
        }
    }

    public T setDefault(O object) {
        return currentLeaf(object).set(object, this, getDefault());
    }

    public T set(O object, T value) {
        return currentLeaf(object).set(object, this, value);
    }

    public <E> T set(O object, BiFunction<T, E, T> function, E element) {
        return currentLeaf(object).set(object, this, function, element);
    }

    public <E> T set(O object, UnaryOperator<T> oper) {
        return currentLeaf(object).set(object, this, oper);
    }

    @SuppressWarnings({"unchecked", "unlikely-arg-type"})
    public <E> void add(O obj, E e) {
        set(obj, (v, a) -> {
            if (v instanceof ContainingCollection) {
                return (T) ((ContainingCollection<E>) v).addUnique(a);
            } else if (!a.equals(v)) {
                return (T) a;
            }
            return v;
        }, e);
    }

    @SuppressWarnings({"unchecked", "unlikely-arg-type"})
    public <E> void remove(O obj, E e) {
        set(obj, (v, r) -> {
            if (v instanceof ContainingCollection) {
                return (T) ((ContainingCollection<E>) v).remove(r);
            } else if (r.equals(v)) {
                return null;
            }
            return v;
        }, e);
    }

    @SuppressWarnings("unchecked")
    public static <T, E> void diff(T pre, T post, Consumer<E> added, Consumer<E> removed) {
        if (pre instanceof ContainingCollection && post instanceof ContainingCollection) {
            ((ContainingCollection<E>) pre).compare((ContainingCollection<E>) post).forEachOrdered(d -> {
                if (d[0] == null) {
                    for (E a : d[1]) {
                        added.accept(a);
                    }
                }
                if (d[1] == null) {
                    for (E e : d[0]) {
                        removed.accept(e);
                    }
                }
            });
        } else {
            if (pre instanceof ContainingCollection) {
                for (E e : (ContainingCollection<E>) pre) {
                    removed.accept(e);
                }
            } else if (pre != null) {
                removed.accept((E) pre);
            }
            if (post instanceof ContainingCollection) {
                for (E e : (ContainingCollection<E>) post) {
                    added.accept(e);
                }
            } else if (post != null) {
                added.accept((E) post);
            }
        }
    }

    public boolean checkConsistency() {
        return !plumbing && (scope != null || isReference());
    }

    public boolean isTraced() {
        return !plumbing && !synthetic;
    }

    public boolean isPlumbing() {
        return plumbing;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public Set<ConsistencyError> checkConsistency(State state, O object, T post) {
        Set<ConsistencyError> errors = Set.of();
        if (isReference()) {
            for (Mutable m : mutables(post)) {
                if (m.dIsOrphan(state)) {
                    errors = errors.add(new ReferencedOrphanException(object, this, m));
                }
            }
        }
        if (scope != null) {
            Set s = state.get(object, scope.get());
            if (post instanceof ContainingCollection) {
                if (!s.containsAll((ContainingCollection) post)) {
                    errors = errors.add(new OutOfScopeException(object, this, post, s));
                }
            } else if (post != null) {
                if (!s.contains(post)) {
                    errors = errors.add(new OutOfScopeException(object, this, post, s));
                }
            }
        }
        return errors;
    }

}
