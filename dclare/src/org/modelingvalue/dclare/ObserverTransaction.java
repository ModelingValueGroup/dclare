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

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.modelingvalue.collections.ContainingCollection;
import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Concurrent;
import org.modelingvalue.collections.util.Context;
import org.modelingvalue.collections.util.Mergeable;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.collections.util.Quintuple;
import org.modelingvalue.collections.util.Triple;
import org.modelingvalue.dclare.ex.ConsistencyError;
import org.modelingvalue.dclare.ex.NonDeterministicException;
import org.modelingvalue.dclare.ex.TooManyChangesException;
import org.modelingvalue.dclare.ex.TooManyObservedException;

public class ObserverTransaction extends ActionTransaction {

    private static final boolean                                 TRACE_MATCHING  = Boolean.getBoolean("TRACE_MATCHING");

    private static final Set<Boolean>                            FALSE           = Set.of();
    private static final Set<Boolean>                            TRUE            = Set.of(true);

    private static final boolean                                 TRACE_OBSERVERS = Boolean.getBoolean("TRACE_OBSERVERS");

    public static final Context<Boolean>                         OBSERVE         = Context.of(true);

    @SuppressWarnings("rawtypes")
    private final Concurrent<DefaultMap<Observed, Set<Mutable>>> getted          = Concurrent.of();
    @SuppressWarnings("rawtypes")
    private final Concurrent<DefaultMap<Observed, Set<Mutable>>> setted          = Concurrent.of();

    @SuppressWarnings("rawtypes")
    private final Concurrent<Map<Construction, Newable>>         constructions   = Concurrent.of();

    private final Concurrent<Set<Boolean>>                       emptyMandatory  = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       changed         = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       backwards       = Concurrent.of();

    protected ObserverTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    public Observer<?> observer() {
        return (Observer<?>) action();
    }

    @Override
    protected String traceId() {
        return "observer";
    }

    @SuppressWarnings("unchecked")
    @Override
    protected final void run(State pre, UniverseTransaction universeTransaction) {
        Observer<?> observer = observer();
        Pair<Instant, Throwable> throwable = null;
        // check if the universe is still in the same transaction, if not: reset my state
        long rootCount = universeTransaction.stats().runCount();
        if (observer.runCount != rootCount) {
            observer.runCount = rootCount;
            observer.changes = 0;
            observer.stopped = false;
        }
        // check if we should do the work...
        if (!observer.stopped && !universeTransaction.isKilled()) {
            getted.init(Observed.OBSERVED_MAP);
            setted.init(Observed.OBSERVED_MAP);
            constructions.init(Map.of());
            emptyMandatory.init(FALSE);
            changed.init(FALSE);
            backwards.init(FALSE);
            try {
                doRun(pre, universeTransaction);
            } catch (Throwable t) {
                do {
                    if (t instanceof ConsistencyError) {
                        throw (ConsistencyError) t;
                    } else if (t instanceof NullPointerException) {
                        if (emptyMandatory.result().equals(FALSE)) {
                            throwable = Pair.of(Instant.now(), t);
                        }
                        return;
                    } else if (t.getCause() != null) {
                        t = t.getCause();
                    } else {
                        throwable = Pair.of(Instant.now(), t);
                        return;
                    }
                } while (true);
            } finally {
                observe(pre, observer, setted.result(), getted.result());
                observer.exception().set(mutable(), throwable);
                observer.constructed().set(mutable(), constructions.result());
                emptyMandatory.clear();
                changed.clear();
                backwards.clear();
            }
        }
    }

    protected void doRun(State pre, UniverseTransaction universeTransaction) {
        super.run(pre, universeTransaction);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void observe(State pre, Observer<?> observer, DefaultMap<Observed, Set<Mutable>> sets, DefaultMap<Observed, Set<Mutable>> gets) {
        gets = gets.removeAll(sets, Set::removeAll);
        if (changed.result().equals(TRUE)) {
            checkTooManyChanges(pre, sets, gets);
            trigger(mutable(), (Observer<Mutable>) observer, Direction.forward);
        } else if (backwards.result().equals(TRUE)) {
            trigger(mutable(), (Observer<Mutable>) observer, Direction.backward);
        }
        DefaultMap<Observed, Set<Mutable>> all = gets.addAll(sets, Set::addAll);
        DefaultMap preAll = super.set(mutable(), observer.observeds(), all);
        if (preAll.isEmpty() && !all.isEmpty()) {
            observer.instances++;
        } else if (!preAll.isEmpty() && all.isEmpty()) {
            observer.instances--;
        }
        checkTooManyObserved(all);
    }

    @SuppressWarnings("rawtypes")
    protected void checkTooManyObserved(DefaultMap<Observed, Set<Mutable>> all) {
        if (universeTransaction().stats().maxNrOfObserved() < size(all)) {
            throw new TooManyObservedException(mutable(), observer(), all, universeTransaction());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void checkTooManyChanges(State pre, DefaultMap<Observed, Set<Mutable>> sets, DefaultMap<Observed, Set<Mutable>> gets) {
        UniverseTransaction universeTransaction = universeTransaction();
        Observer<?> observer = observer();
        Mutable mutable = mutable();
        if (TRACE_OBSERVERS) {
            State result = resultState();
            System.err.println("DCLARE: " + parent().indent("    ") + mutable + "." + observer() + " ("//
                    + toString(gets, mutable, (m, o) -> pre.get(m, o)) + " " + toString(sets, mutable, (m, o) -> pre.get(m, o) + "->" + result.get(m, o)) + ")");
        }
        if (universeTransaction.stats().debugging()) {
            State result = resultState();
            Set<ObserverTrace> traces = observer.traces.get(mutable);
            ObserverTrace trace = new ObserverTrace(mutable, observer, traces.sorted().findFirst().orElse(null), observer.changesPerInstance(), //
                    gets.addAll(sets, Set::addAll).filter(e -> !(e.getKey() instanceof NonCheckingObserved)).flatMap(e -> e.getValue().map(m -> {
                        m = m.resolve(mutable);
                        return Entry.of(ObservedInstance.of(m, e.getKey()), pre.get(m, e.getKey()));
                    })).toMap(e -> e), //
                    sets.filter(e -> !(e.getKey() instanceof NonCheckingObserved)).flatMap(e -> e.getValue().map(m -> {
                        m = m.resolve(mutable);
                        return Entry.of(ObservedInstance.of(m, e.getKey()), result.get(m, e.getKey()));
                    })).toMap(e -> e));
            observer.traces.set(mutable, traces.add(trace));
        }
        int totalChanges = universeTransaction.stats().bumpAndGetTotalChanges();
        int changesPerInstance = observer.countChangesPerInstance();
        if (changesPerInstance > universeTransaction.stats().maxNrOfChanges()) {
            universeTransaction.stats().setDebugging(true);
            if (changesPerInstance > universeTransaction.stats().maxNrOfChanges() * 2) {
                hadleTooManyChanges(universeTransaction, mutable, observer, changesPerInstance);
            }
        } else if (totalChanges > universeTransaction.stats().maxTotalNrOfChanges()) {
            universeTransaction.stats().setDebugging(true);
            if (totalChanges > universeTransaction.stats().maxTotalNrOfChanges() + universeTransaction.stats().maxNrOfChanges()) {
                hadleTooManyChanges(universeTransaction, mutable, observer, totalChanges);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static String toString(DefaultMap<Observed, Set<Mutable>> sets, Mutable self, BiFunction<Mutable, Observed, Object> value) {
        return sets.reduce("", (r1, e) -> (r1.isEmpty() ? "" : r1 + " ") + e.getValue().reduce("", (r2, m) -> //
        (m != Mutable.THIS ? m + "." : "") + e.getKey() + "=" + value.apply(m.resolve(self), e.getKey()), //
                (a, b) -> a + " " + b), (a, b) -> a + " " + b);
    }

    private void hadleTooManyChanges(UniverseTransaction universeTransaction, Mutable mutable, Observer<?> observer, int changes) {
        State result = resultState();
        ObserverTrace last = result.get(mutable, observer.traces).sorted().findFirst().orElse(null);
        if (last != null && last.done().size() >= (changes > universeTransaction.stats().maxTotalNrOfChanges() ? 1 : universeTransaction.stats().maxNrOfChanges())) {
            observer.stopped = true;
            throw new TooManyChangesException(result, last, changes);
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <O, T> T get(O object, Getable<O, T> getable) {
        if (getable instanceof Observed && Constant.DERIVED.get() != null && ObserverTransaction.OBSERVE.get()) {
            throw new NonDeterministicException(Constant.DERIVED.get().a(), Constant.DERIVED.get().b(), "Reading observed '" + object + "." + getable + //
                    "' while initializing constant '" + Constant.DERIVED.get().a() + "." + Constant.DERIVED.get().b() + "'");
        }
        if (observing(object, getable)) {
            observe(object, (Observed<O, T>) getable, false);
        }
        T result = super.get(object, getable);
        if (result == null && getable instanceof Observed && ((Observed) getable).mandatory() && !((Observed) getable).checkConsistency) {
            emptyMandatory.set(TRUE);
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <O, T> T pre(O object, Getable<O, T> getable) {
        if (observing(object, getable)) {
            observe(object, (Observed<O, T>) getable, false);
        }
        T result = super.pre(object, getable);
        if (result == null && getable instanceof Observed && ((Observed) getable).mandatory()) {
            result = super.get(object, getable);
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <O, T> T current(O object, Getable<O, T> getable) {
        if (observing(object, getable)) {
            observe(object, (Observed<O, T>) getable, false);
        }
        T result = super.current(object, getable);
        if (result == null && getable instanceof Observed && ((Observed) getable).mandatory() && !((Observed) getable).checkConsistency) {
            emptyMandatory.set(TRUE);
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected <T, O> void set(O object, Setable<O, T> setable, T pre, T post) {
        if (observing(object, setable)) {
            observe(object, (Observed<O, T>) setable, true);
            if (!Objects.equals(pre, post)) {
                post = matchNewables(setable, pre, post, rippleOut(object, (Observed<O, T>) setable, pre, post));
            }
        }
        super.set(object, setable, pre, post);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T, O> T rippleOut(O object, Observed<O, T> observed, T pre, T post) {
        T old = universeTransaction().oldState().get(object, observed);
        if (!Objects.equals(pre, old)) {
            if (Objects.equals(old, post)) {
                backwards.set(TRUE);
                return pre;
            } else if (old instanceof Mergeable) {
                T result = ((Mergeable<T>) old).merge(pre, post);
                if (!result.equals(post)) {
                    backwards.set(TRUE);
                    return result;
                }
            }
        } else if (observed.containment()) {
            if (pre instanceof Mutable && isActive((Mutable) pre)) {
                backwards.set(TRUE);
                return pre;
            } else if (pre instanceof ContainingCollection && post instanceof ContainingCollection) {
                ContainingCollection<Object> pres = (ContainingCollection<Object>) pre;
                ContainingCollection<Object> posts = (ContainingCollection<Object>) post;
                T result = (T) posts.addAll(pres.filter(o -> o instanceof Mutable && !posts.contains(o) && isActive((Mutable) o)));
                if (!result.equals(post)) {
                    backwards.set(TRUE);
                    return result;
                }
            }
        }
        return post;
    }

    @SuppressWarnings("rawtypes")
    private boolean isActive(Mutable mutable) {
        byte old = universeTransaction().oldState().get(mutable, Mutable.D_CHANGE_NR);
        byte pre = state().get(mutable, Mutable.D_CHANGE_NR);
        return pre != old;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <O, T> void observe(O object, Observed<O, T> observed, boolean set) {
        (set ? setted : getted).change(o -> o.add(observed.entry((Mutable) object, mutable()), Set::addAll));
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void handleMergeConflict(Object object, Setable property, Object pre, Object... branches) {
    }

    @Override
    public void runNonObserving(Runnable action) {
        if (getted.isInitialized()) {
            OBSERVE.run(false, action);
        } else {
            super.runNonObserving(action);
        }
    }

    @Override
    public <T> T getNonObserving(Supplier<T> action) {
        if (getted.isInitialized()) {
            return OBSERVE.get(false, action);
        } else {
            return super.getNonObserving(action);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    protected <O, T> void changed(O object, Setable<O, T> setable, T preValue, T postValue) {
        if (observing(object, setable)) {
            changed.set(TRUE);
        }
        runNonObserving(() -> super.changed(object, setable, preValue, postValue));
    }

    private <O, T> boolean observing(O object, Getable<O, T> setable) {
        return object instanceof Mutable && setable instanceof Observed && getted.isInitialized() && OBSERVE.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <O extends Newable> O construct(Construction.Reason reason, Supplier<O> supplier) {
        if (!mutable().isIdentified() || !reason.completelyIdentified()) {
            return null;
        } else if (Constant.DERIVED.get() != null) {
            return super.construct(reason, supplier);
        } else {
            Construction cons = Construction.of(mutable(), observer(), reason);
            O result = (O) current(mutable(), observer().constructed()).get(cons);
            if (result == null) {
                result = supplier.get();
            }
            if (TRACE_MATCHING) {
                O finalRsult = result;
                runNonObserving(() -> System.err.println("MATCHING " + reason + " -> " + finalRsult));
            }
            set(mutable(), observer().constructed(), (map, e) -> map.put(cons, e), result);
            constructions.set((map, e) -> map.put(cons, e), result);
            return result;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <O, T> T matchNewables(Setable<O, T> setable, T pre, T post, T rippleOut) {
        if (!setable.containment()) {
            return rippleOut;
        } else if (rippleOut instanceof Newable) {
            return (T) singleMatch((Newable) pre, (Newable) post, (Newable) rippleOut);
        } else if (containsNewable(rippleOut)) {
            return (T) manyMatch((ContainingCollection<Newable>) pre, (ContainingCollection<Newable>) post, (ContainingCollection<Newable>) rippleOut);
        } else {
            return rippleOut;
        }
    }

    @SuppressWarnings("rawtypes")
    private static <T> boolean containsNewable(T v) {
        return v instanceof ContainingCollection && !((ContainingCollection) v).isEmpty() && ((ContainingCollection) v).get(0) instanceof Newable;
    }

    private Newable singleMatch(Newable before, Newable after, Newable result) {
        if (before != null && after != null) {
            Triple<Newable, Set<Construction>, Set<Object>> pre = preInfo(before);
            Quintuple<Newable, Set<Construction>, Map<Newable, Set<Construction>>, Optional<Newable>, Set<Object>> post = postInfo(after);
            if (pre.b().isEmpty() && result.equals(before)) {
                return after;
            } else if (post.b().isEmpty() && result.equals(after)) {
                return before;
            } else if (pre.b().anyMatch(Construction::isNotObserved) && post.b().allMatch(Construction::isObserved) && sameTypeDifferentReason(pre, post)) {
                makeTheSame(pre, post);
                return before;
            }
        }
        return result;
    }

    private ContainingCollection<Newable> manyMatch(ContainingCollection<Newable> preColl, ContainingCollection<Newable> postColl, ContainingCollection<Newable> rippleOut) {
        ContainingCollection<Newable> result = rippleOut.clear().addAll(rippleOut.filter(n -> !n.dConstructions().isEmpty()));
        List<Triple<Newable, Set<Construction>, Set<Object>>> preList = //
                preColl.map(ObserverTransaction::preInfo).filter(p -> p.b().anyMatch(Construction::isNotObserved)).toList();
        if (!preList.isEmpty()) {
            List<Quintuple<Newable, Set<Construction>, Map<Newable, Set<Construction>>, Optional<Newable>, Set<Object>>> postList = //
                    postColl.map(ObserverTransaction::postInfo).filter(p -> !p.b().isEmpty() && p.b().allMatch(Construction::isObserved)).toList();
            if (!postList.isEmpty()) {
                if (!(result instanceof List)) {
                    preList = preList.sortedBy(p -> p.a().dSortKey()).toList();
                    postList = postList.sortedBy(q -> q.d().orElse(q.a()).dSortKey()).toList();
                }
                for (int i = 0; i < postList.size(); i++) {
                    Quintuple<Newable, Set<Construction>, Map<Newable, Set<Construction>>, Optional<Newable>, Set<Object>> post = postList.get(i);
                    for (int ii = 0; ii < preList.size(); ii++) {
                        Triple<Newable, Set<Construction>, Set<Object>> pre = preList.get(ii);
                        if (sameTypeDifferentReason(pre, post) && areTheSame(pre, post)) {
                            makeTheSame(pre, post);
                            result = result.remove(post.a());
                            preList = preList.removeIndex(ii);
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }

    private static Triple<Newable, Set<Construction>, Set<Object>> preInfo(Newable before) {
        Set<Construction> cons = before.dConstructions();
        return Triple.of(before, cons, Construction.reasonTypes(cons));
    }

    private static Quintuple<Newable, Set<Construction>, Map<Newable, Set<Construction>>, Optional<Newable>, Set<Object>> postInfo(Newable after) {
        Set<Construction> cons = after.dConstructions();
        Map<Newable, Set<Construction>> srcs = Construction.sources(cons, Map.of());
        return Quintuple.of(after, cons, srcs, Construction.notObservedSource(srcs), Construction.reasonTypes(cons));
    }

    private static boolean sameTypeDifferentReason(Triple<Newable, Set<Construction>, Set<Object>> pre, Quintuple<Newable, Set<Construction>, Map<Newable, Set<Construction>>, Optional<Newable>, Set<Object>> post) {
        return pre.a().dNewableType().equals(post.a().dNewableType()) && !pre.c().anyMatch(post.e()::contains);
    }

    private static boolean areTheSame(Triple<Newable, Set<Construction>, Set<Object>> pre, Quintuple<Newable, Set<Construction>, Map<Newable, Set<Construction>>, Optional<Newable>, Set<Object>> post) {
        if (TRACE_MATCHING) {
            LeafTransaction.getCurrent().runNonObserving(() -> System.err.println("MATCHING " + pre.a() + " <> " + post.a()));
        }
        if (post.c().containsKey(pre.a())) {
            return true;
        } else {
            Object preId = pre.a().dIdentity();
            Object postId = post.a().dIdentity();
            if (preId != null && postId != null && preId.equals(postId)) {
                return true;
            } else if (preId == null && post.d().map(Newable::dIdentity).orElse(postId) == null) {
                return true;
            } else {
                return false;
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void makeTheSame(Triple<Newable, Set<Construction>, Set<Object>> pre, Quintuple<Newable, Set<Construction>, Map<Newable, Set<Construction>>, Optional<Newable>, Set<Object>> post) {
        if (TRACE_MATCHING) {
            runNonObserving(() -> System.err.println("MATCHING " + pre.a() + " == " + post.a()));
        }
        for (Construction cons : post.b()) {
            set(cons.object(), cons.observer().constructed(), (map, e) -> map.put(cons, e), pre.a());
            if (mutable().equals(cons.object()) && observer().equals(cons.observer())) {
                constructions.set((map, e) -> map.put(cons, e), pre.a());
            }
        }
    }

}
