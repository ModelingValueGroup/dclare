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

import static org.modelingvalue.dclare.Priority.ALL;

import java.util.Comparator;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Context;

@SuppressWarnings("unused")
public abstract class LeafTransaction extends Transaction {

    private static final Context<LeafTransaction> CURRENT = Context.of();

    protected LeafTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    public final Leaf leaf() {
        return (Leaf) cls();
    }

    public static int sizeForConsistency(DefaultMap<?, Set<Mutable>> map) {
        return map.reduce(0, (a, e) -> a + (ignoreForConsistency(e.getKey()) ? 0 : e.getValue().size()), Integer::sum);
    }

    @SuppressWarnings("rawtypes")
    private static boolean ignoreForConsistency(Object o) {
        return o instanceof Observed && !((Observed) o).checkConsistency();
    }

    public static String condenseForConsistencyTrace(DefaultMap<?, Set<Mutable>> map) {
        boolean noSignulars = map //
                .filter(e -> !ignoreForConsistency(e.getKey())) //
                .filter(e -> 1 == e.getValue().size()) //
                .isEmpty();
        return map //
                .filter(e -> !ignoreForConsistency(e.getKey())) //
                .filter(e -> 1 < e.getValue().size()) //
                .sortedByDesc(e -> e.getValue().size()) //
                .map(e -> { //
                    java.util.Map<String, Long> counts = e.getValue() //
                            .map(m -> m.toString().replaceAll("[^a-zA-Z0-9]+.*", "")) //
                            .collect(Collectors.groupingBy(i -> i, Collectors.counting()));
                    String condensedRepresentation = counts.entrySet().stream() //
                            .sorted(Comparator.comparingLong(Map.Entry::getValue)) //
                            .map(ee -> String.format("%d x %s", ee.getValue(), ee.getKey())) //
                            .collect(Collectors.joining(", ", "[", "]"));
                    return String.format("%s=%s", e.getKey(), condensedRepresentation);
                }) //
                .collect(Collectors.joining(", ", "[", noSignulars ? "]" : ",...]"));
    }

    public static LeafTransaction getCurrent() {
        return CURRENT.get();
    }

    public static Context<LeafTransaction> getContext() {
        return CURRENT;
    }

    public abstract State state();

    public State current() {
        return state();
    }

    public abstract <O, T, E> T set(O object, Setable<O, T> property, BiFunction<T, E, T> function, E element);

    public abstract <O, T> T set(O object, Setable<O, T> property, UnaryOperator<T> oper);

    public abstract <O, T> T set(O object, Setable<O, T> property, T post);

    public <O, T> T setDefault(O object, Setable<O, T> property) {
        return set(object, property, property.getDefault(object));
    }

    public <O, T> T get(O object, Getable<O, T> property) {
        return state().get(object, property);
    }

    protected <O, T> T current(O object, Getable<O, T> property) {
        return current().get(object, property);
    }

    public <O, T> T pre(O object, Getable<O, T> property) {
        return universeTransaction().preState().get(object, property);
    }

    protected <O, T> void changed(O object, Setable<O, T> setable, T preValue, T postValue) {
        setable.changed(this, object, preValue, postValue);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void clear(Mutable object) {
        for (Setable setable : toBeCleared(object)) {
            set(object, setable, setable.getDefault(object));
        }
    }

    protected final void clearOrphan(Mutable orphan) {
        orphan.dDeactivate();
        clear(orphan);
        orphan.dChildren().forEach(this::clearOrphan);
    }

    @SuppressWarnings("rawtypes")
    protected Collection<Setable> toBeCleared(Mutable object) {
        return state().getProperties(object).map(Entry::getKey).exclude(Setable::doNotClear);
    }

    protected <O extends Mutable> void trigger(O target, Action<O> action, Priority priority) {
        Mutable object = target;
        set(object, priority.actions, Set::add, action);
        for (int i = priority.ordinal() + 1; i < ALL.length; i++) {
            set(object, ALL[i].actions, Set::remove, action);
        }
        Mutable container = dParent(object);
        while (container != null && !ancestorEqualsMutable(object)) {
            set(container, priority.children, Set::add, object);
            for (int i = priority.ordinal() + 1; i < ALL.length; i++) {
                if (current(object, ALL[i].actions).isEmpty() && current(object, ALL[i].children).isEmpty()) {
                    set(container, ALL[i].children, Set::remove, object);
                }
            }
            object = container;
            container = dParent(object);
        }
    }

    private boolean ancestorEqualsMutable(Mutable object) {
        MutableTransaction mt = parent();
        while (mt != null && !mt.mutable().equals(object)) {
            mt = mt.parent();
        }
        return mt != null;
    }

    protected Mutable dParent(Mutable object) {
        return state().getA(object, Mutable.D_PARENT_CONTAINING);
    }

    public void runNonObserving(Runnable action) {
        action.run();
    }

    public <T> T getNonObserving(Supplier<T> action) {
        return action.get();
    }

    @Override
    public Mutable mutable() {
        return parent().mutable();
    }

    public abstract ActionInstance actionInstance();

    @SuppressWarnings("unchecked")
    public <O extends Newable> O construct(Construction.Reason reason, Supplier<O> supplier) {
        Newable result = null;
        if (supplier != null) {
            result = constantState().get(this, reason, Construction.CONSTRUCTED, c -> supplier.get());
        } else if (constantState().isSet(this, reason, Construction.CONSTRUCTED)) {
            result = constantState().get(this, reason, Construction.CONSTRUCTED);
        }
        if (result != null) {
            constantState().set(this, result, Newable.D_INITIAL_CONSTRUCTION, Construction.of(reason), true);
        }
        return (O) result;
    }

    protected ConstantState constantState() {
        return universeTransaction().constantState();
    }

    public <O extends Newable> O directConstruct(Construction.Reason reason, Supplier<O> supplier) {
        return construct(reason, supplier);
    }

    public abstract Direction direction();
}
