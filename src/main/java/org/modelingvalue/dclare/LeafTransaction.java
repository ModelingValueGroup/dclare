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
public abstract class LeafTransaction extends Transaction implements ConstantChangeHandler {

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

    public State current() {
        return state();
    }

    public abstract <O, T, E> T set(O object, Setable<O, T> property, BiFunction<T, E, T> function, E element);

    public abstract <O, T> T set(O object, Setable<O, T> property, UnaryOperator<T> oper);

    public <O, T> T setDefault(O object, Setable<O, T> property) {
        return set(object, property, property.getDefault(object));
    }

    public <O, T> T get(O object, Getable<O, T> property) {
        return state().get(object, property);
    }

    public <O, T> T getRaw(O object, Getable<O, T> property) {
        return state().getRaw(object, property);
    }

    protected <O, T> T current(O object, Getable<O, T> property) {
        return current().get(object, property);
    }

    public <O, T> T pre(O object, Getable<O, T> property) {
        return universeTransaction().preState().get(object, property);
    }

    @Override
    public <O, T> void changed(O object, Setable<O, T> setable, T preValue, T rawPreValue, T postValue) {
        setable.changed(this, object, rawPreValue, postValue);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void clear(Mutable object) {
        for (Setable setable : toBeCleared(object)) {
            set(object, setable, setable.getDefault(object));
        }
    }

    protected final void clearOrphan(Mutable orphan) {
        orphan.dDeactivate(this);
        clear(orphan);
        orphan.dChildren().forEach(this::clearOrphan);
    }

    @SuppressWarnings("rawtypes")
    protected Collection<Setable> toBeCleared(Mutable object) {
        return state().getProperties(object).map(Entry::getKey).exclude(Setable::doNotClear);
    }

    protected <O extends Mutable> void trigger(O target, Action<O> action, Priority priority) {
        Mutable object = target;
        set(object, state().actions(priority), Set::add, action);
        for (int i = priority.ordinal() + 1; i < ALL.length; i++) {
            set(object, state().actions(ALL[i]), Set::remove, action);
        }
        Mutable container = dParent(object);
        while (container != null && !ancestorEqualsMutable(object)) {
            set(container, state().children(priority), Set::add, object);
            for (int i = priority.ordinal() + 1; i < ALL.length; i++) {
                if (current(object, state().actions(ALL[i])).isEmpty() && current(object, state().children(ALL[i])).isEmpty()) {
                    set(container, state().children(ALL[i]), Set::remove, object);
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
    public <O extends Mutable> O construct(Construction.Reason reason, Supplier<O> supplier) {
        Mutable result = null;
        if (supplier != null) {
            result = constantState().get(this, reason, Construction.CONSTRUCTED, c -> supplier.get());
        } else if (constantState().isSet(this, reason, Construction.CONSTRUCTED)) {
            result = constantState().get(this, reason, Construction.CONSTRUCTED);
        }
        if (result != null) {
            constantState().set(this, result, Mutable.D_INITIAL_CONSTRUCTION, Construction.of(reason), true);
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

    public final FixpointGroup fixpointGroup() {
        return direction().fixpointGroup();
    }

    public IState preStartState(Priority priority) {
        return universeTransaction().preStartState(priority);
    }

    public IState startState(Priority priority) {
        return universeTransaction().startState(priority);
    }

    protected State startState() {
        return universeTransaction().startState();
    }

    protected Collection<IState> longHistory() {
        return universeTransaction().longHistory();
    }

}
