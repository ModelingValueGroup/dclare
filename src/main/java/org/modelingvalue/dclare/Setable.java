//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2024 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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
import static org.modelingvalue.dclare.Priority.one;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.ContainingCollection;
import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Context;
import org.modelingvalue.collections.util.Internable;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.collections.util.QuadConsumer;
import org.modelingvalue.dclare.ex.ConsistencyError;
import org.modelingvalue.dclare.ex.OutOfScopeException;
import org.modelingvalue.dclare.ex.ReferencedOrphanException;

public class Setable<O, T> extends Getable<O, T> {
    private static final boolean          DANGER_ALWAYS_ALLOW_ORPHANS = Boolean.getBoolean("DANGER_ALWAYS_ALLOW_ORPHANS");

    private static final Context<Boolean> MOVING                      = Context.of(false);

    public static <C, V> Setable<C, V> of(Object id, V def, SetableModifier<?>... modifiers) {
        return new Setable<>(id, c -> def, null, null, null, modifiers);
    }

    public static <C, V> Setable<C, V> of(Object id, V def, QuadConsumer<LeafTransaction, C, V, V> changed, SetableModifier<?>... modifiers) {
        return new Setable<>(id, c -> def, null, null, changed, modifiers);
    }

    public static <C, V> Setable<C, V> of(Object id, V def, Supplier<Setable<?, ?>> opposite, SetableModifier<?>... modifiers) {
        return new Setable<>(id, c -> def, opposite, null, null, modifiers);
    }

    public static <C, V> Setable<C, V> of(Object id, V def, QuadConsumer<LeafTransaction, C, V, V> changed, Supplier<Setable<C, Set<?>>> scope, SetableModifier<?>... modifiers) {
        return new Setable<>(id, c -> def, null, scope, changed, modifiers);
    }

    public static <C, V> Setable<C, V> of(Object id, V def, Supplier<Setable<?, ?>> opposite, Supplier<Setable<C, Set<?>>> scope, SetableModifier<?>... modifiers) {
        return new Setable<>(id, c -> def, opposite, scope, null, modifiers);
    }

    public static <C, V> Setable<C, V> of(Object id, Function<C, V> def, SetableModifier<?>... modifiers) {
        return new Setable<>(id, def, null, null, null, modifiers);
    }

    public static <C, V> Setable<C, V> of(Object id, Function<C, V> def, QuadConsumer<LeafTransaction, C, V, V> changed, SetableModifier<?>... modifiers) {
        return new Setable<>(id, def, null, null, changed, modifiers);
    }

    public static <C, V> Setable<C, V> of(Object id, Function<C, V> def, Supplier<Setable<?, ?>> opposite, SetableModifier<?>... modifiers) {
        return new Setable<>(id, def, opposite, null, null, modifiers);
    }

    public static <C, V> Setable<C, V> of(Object id, Function<C, V> def, QuadConsumer<LeafTransaction, C, V, V> changed, Supplier<Setable<C, Set<?>>> scope, SetableModifier<?>... modifiers) {
        return new Setable<>(id, def, null, scope, changed, modifiers);
    }

    public static <C, V> Setable<C, V> of(Object id, Function<C, V> def, Supplier<Setable<?, ?>> opposite, Supplier<Setable<C, Set<?>>> scope, SetableModifier<?>... modifiers) {
        return new Setable<>(id, def, opposite, scope, null, modifiers);
    }

    private final QuadConsumer<LeafTransaction, O, T, T> changed;
    private final boolean                                containment;
    private final Supplier<Setable<?, ?>>                opposite;
    private final Supplier<Setable<O, Set<?>>>           scope;
    @SuppressWarnings("rawtypes")
    private final Constant<T, Entry<Setable, Object>>    internal;
    @SuppressWarnings("rawtypes")
    private final Entry<Setable, Object>                 nullEntry;
    private final Set<SetableModifier<?>>                modifierSet;
    private final boolean                                plumbing;
    private final boolean                                synthetic;
    private final boolean                                doNotMerge;
    private final boolean                                orphansAllowed;
    private final boolean                                preserved;
    private final boolean                                doNotClear;
    private final Direction                              direction;

    private Boolean                                      isReference;
    private Constant<O, T>                               constant;

    protected Setable(Object id, Function<O, T> def, Supplier<Setable<?, ?>> opposite, Supplier<Setable<O, Set<?>>> scope, QuadConsumer<LeafTransaction, O, T, T> changed, SetableModifier<?>... modifiers) {
        super(id, def);
        this.modifierSet = Collection.of(modifiers).notNull().asSet();
        this.plumbing = hasModifier(CoreSetableModifier.plumbing);
        this.containment = hasModifier(CoreSetableModifier.containment);
        this.synthetic = hasModifier(CoreSetableModifier.synthetic);
        this.changed = changed;
        if (hasModifier(symmetricOpposite) && opposite != null) {
            throw new Error("The setable " + this + " is already a symmetric-opposite");
        }
        this.opposite = hasModifier(symmetricOpposite) ? () -> this : opposite;
        this.scope = scope;
        this.nullEntry = Entry.of(this, null);
        this.internal = this instanceof Constant ? null : Constant.of(Pair.of(this, "internalEntry"), v -> Entry.of(this, v));
        this.doNotMerge = hasModifier(CoreSetableModifier.doNotMerge);
        this.orphansAllowed = hasModifier(CoreSetableModifier.orphansAllowed);
        this.preserved = hasModifier(CoreSetableModifier.preserved);
        this.doNotClear = hasModifier(CoreSetableModifier.doNotClear);
        Direction dir = getModifier(Direction.class);
        this.direction = dir == null ? Direction.DEFAULT : dir;
    }

    public boolean hasModifier(SetableModifier<?> modifier) {
        return modifierSet.contains(modifier);
    }

    @SuppressWarnings({"unused", "unchecked"})
    public <SM extends SetableModifier<?>> SM getModifier(Class<SM> modifierClass) {
        return (SM) FeatureModifier.ofClass(modifierClass, modifierSet);
    }

    @SuppressWarnings("rawtypes")
    protected Entry<Setable, Object> entry(T value, DefaultMap<Setable, Object> properties) {
        if (value == null) {
            return nullEntry;
        } else if (Internable.isInternable(value)) {
            return internal.get(value);
        } else {
            Entry<Setable, Object> e = Entry.of(this, value);
            if (deduplicate(value)) {
                State.deduplicate(e);
            }
            return e;
        }
    }

    protected boolean deduplicate(T value) {
        return value instanceof ContainingCollection;
    }

    public Direction direction() {
        return direction;
    }

    public boolean preserved() {
        return preserved;
    }

    public boolean doNotMerge() {
        return doNotMerge;
    }

    public boolean doNotClear() {
        return doNotClear;
    }

    public boolean orphansAllowed() {
        return orphansAllowed;
    }

    public boolean isReference() {
        return isReference != null && isReference;
    }

    public Constant<O, T> constant() {
        if (constant == null) {
            constant = new Constant<O, T>(this, defaultFunction(), null, null, null, null);
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
                Pair<Mutable, Setable<Mutable, ?>> prePair = tx.getRaw(added, Mutable.D_PARENT_CONTAINING);
                if (prePair != null) {
                    MOVING.run(true, () -> prePair.b().remove(prePair.a(), added));
                }
                Mutable.D_PARENT_CONTAINING.set(added, Pair.of((Mutable) object, (Setable<Mutable, ?>) this));
                if (prePair == null) {
                    added.dActivate();
                } else {
                    tx.set((Mutable) object, tx.state().children(one), Set::add, added);
                }
            }, removed -> {
                for (Priority prio : Priority.ALL) {
                    tx.set((Mutable) object, tx.state().children(prio), Set::remove, removed);
                }
                if (!MOVING.get()) {
                    Mutable.D_PARENT_CONTAINING.setDefault(removed);
                    removed.dHandleRemoved((Mutable) object);
                }
            });
        }
        if (opposite != null) {
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
        return set(object, getDefault(object));
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
    public <E> T add(O obj, E e) {
        return set(obj, (v, a) -> {
            if (v instanceof ContainingCollection) {
                return (T) ((ContainingCollection<E>) v).addUnique(a);
            } else if (!a.equals(v)) {
                return (T) a;
            }
            return v;
        }, e);
    }

    @SuppressWarnings({"unchecked", "unlikely-arg-type"})
    public <E> T remove(O obj, E e) {
        return set(obj, (v, r) -> {
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
                if (d[1] != null) {
                    for (E a : d[1]) {
                        added.accept(a);
                    }
                }
                if (d[0] != null) {
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
        return !plumbing && !direction().isLazy() && (scope != null || checkForOrphans());
    }

    private boolean checkForOrphans() {
        return !orphansAllowed() && isReference();
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
        if (checkForOrphans()) {
            for (Mutable m : mutables(post)) {
                if (m.dIsOrphan(state)) {
                    ReferencedOrphanException oe = new ReferencedOrphanException(object, this, m);
                    if (DANGER_ALWAYS_ALLOW_ORPHANS) {
                        System.err.println("DANGER: suppressed orphan exception: " + oe.getMessage());
                    } else {
                        errors = errors.add(oe);
                    }
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
