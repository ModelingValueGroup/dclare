//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2022 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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
import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Context;
import org.modelingvalue.collections.util.ContextThread;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

@SuppressWarnings("unused")
public abstract class LeafTransaction extends Transaction {

    private static final Context<LeafTransaction> CURRENT = Context.of();

    protected LeafTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    public final Leaf leaf() {
        return (Leaf) cls();
    }

    @SuppressWarnings("rawtypes")
    public static int size(DefaultMap<?, Set<Mutable>> map) {
        return map.reduce(0, (a, e) -> a + (!(e.getKey() instanceof Observed) || ((Observed) e.getKey()).checkConsistency() ? e.getValue().size() : 0), Integer::sum);
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

    public <O, T> T get(O object, Getable<O, T> property) {
        return state().get(object, property);
    }

    protected <O, T> T current(O object, Getable<O, T> property) {
        return current().get(object, property);
    }

    public <O, T> T pre(O object, Getable<O, T> property) {
        return universeTransaction().preState().get(object, property);
    }

    protected <O, T> void changed(O object, Setable<O, T> property, T preValue, T postValue) {
        property.changed(this, object, preValue, postValue);
        if (property instanceof Observed) {
            trigger((Observed<O, T>) property, object);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void clear(Mutable object) {
        for (Setable setable : toBeCleared(object)) {
            set(object, setable, setable.getDefault());
        }
    }

    @SuppressWarnings("rawtypes")
    protected Collection<Setable> toBeCleared(Mutable object) {
        return state().getProperties(object).map(Entry::getKey);
    }

    protected <O extends Mutable> void trigger(O target, Action<O> action, Priority priority) {
        Mutable object = target;
        set(object, priority.actions, Set::add, action);
        for (int i = priority.ordinal() + 1; i < Priority.values().length; i++) {
            set(object, Priority.values()[i].actions, Set::remove, action);
        }
        Mutable container = dParent(object);
        while (container != null && !ancestorEqualsMutable(object)) {
            set(container, priority.children, Set::add, object);
            for (int i = priority.ordinal() + 1; i < Priority.values().length; i++) {
                if (current(object, Priority.values()[i].actions).isEmpty() && current(object, Priority.values()[i].children).isEmpty()) {
                    set(container, Priority.values()[i].children, Set::remove, object);
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
        Newable result = constantState().get(this, reason, Construction.CONSTRUCTED, c -> supplier.get());
        if (result != null) {
            constantState().set(this, result, Newable.D_DIRECT_CONSTRUCTION, Construction.of(reason), true);
        }
        return (O) result;
    }

    protected ConstantState constantState() {
        return universeTransaction().constantState();
    }

    public <O extends Newable> O directConstruct(Construction.Reason reason, Supplier<O> supplier) {
        return construct(reason, supplier);
    }

    protected <O> void trigger(Observed<O, ?> observed, O o) {
    }

    public abstract Direction direction();

    ////////////////////////// only used for tracing: ///////////////////////////////////////////////
    private static final int      TAG_LENGTH        = 8;
    private static final int      TX_TYPE_LENGTH    = 2;
    private static final int      THREAD_NUM_LENGTH = 2;
    private static final int      ONE_INDENT_LENGTH = 2;
    private static final String[] INDENT_ARRAY      = IntStream.range(0, 50).mapToObj(LeafTransaction::makeIndent).toArray(String[]::new);
    private static final String[] THREAD_NAME_ARRAY = IntStream.range(0, 99).mapToObj(i -> String.format("%02d", i)).toArray(String[]::new);
    private static final String   TRACE_BASE        = fill(TAG_LENGTH + 1 + THREAD_NUM_LENGTH + 1 + TX_TYPE_LENGTH, '_');

    protected abstract String getCurrentTypeForTrace();

    //  produces a string like: "DCLARE__06_OB            "
    public static String getTraceLineStart(String tag, int indent) {
        StringBuilder b = new StringBuilder(TRACE_BASE);
        superImpose(b, tag.length() <= TAG_LENGTH ? tag : tag.substring(0, TAG_LENGTH), 0);
        superImpose(b, getThreadNum(ContextThread.getNr()), TAG_LENGTH + 1);
        if (getCurrent() != null) {
            superImpose(b, getCurrent().getCurrentTypeForTrace(), TAG_LENGTH + 1 + THREAD_NUM_LENGTH + 1);
        }
        b.append(' ').append(getIndent(indent));
        return b.toString();
    }

    private static String getIndent(int i) {
        if (0 <= i && i <= 2000) {
            return i < INDENT_ARRAY.length ? INDENT_ARRAY[i] : makeIndent(i);
        } else {
            return "...(indent=" + i + "???)...";
        }
    }

    private static String getThreadNum(int i) {
        return 0 <= i && i <= 99 ? THREAD_NAME_ARRAY[i] : "??";
    }

    private static String makeIndent(int i) {
        return fill(i * ONE_INDENT_LENGTH, ' ');
    }

    private static void superImpose(StringBuilder base, String over, int at) {
        base.replace(at, at + over.length(), over);
    }

    private static String fill(int i, char c) {
        char[] a = new char[i];
        Arrays.fill(a, c);
        return new String(a);
    }
}
