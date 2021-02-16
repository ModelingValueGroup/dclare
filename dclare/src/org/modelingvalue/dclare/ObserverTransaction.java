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
import org.modelingvalue.dclare.Construction.MatchInfo;
import org.modelingvalue.dclare.Construction.Reason;
import org.modelingvalue.dclare.Observer.Constructed;
import org.modelingvalue.dclare.ex.ConsistencyError;
import org.modelingvalue.dclare.ex.NonDeterministicException;
import org.modelingvalue.dclare.ex.TooManyChangesException;
import org.modelingvalue.dclare.ex.TooManyObservedException;

public class ObserverTransaction extends ActionTransaction {

    protected static final boolean                               TRACE_MATCHING = Boolean.getBoolean("TRACE_MATCHING");

    private static final Set<Boolean>                            FALSE          = Set.of();
    private static final Set<Boolean>                            TRUE           = Set.of(true);

    public static final Context<Boolean>                         OBSERVE        = Context.of(true);

    @SuppressWarnings("rawtypes")
    private final Concurrent<DefaultMap<Observed, Set<Mutable>>> observeds      = Concurrent.of();

    @SuppressWarnings("rawtypes")
    private final Concurrent<Map<Construction.Reason, Newable>>  constructions  = Concurrent.of();

    private final Concurrent<Set<Boolean>>                       emptyMandatory = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       changed        = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       backwards      = Concurrent.of();

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
            observeds.init(Observed.OBSERVED_MAP);
            constructions.init(Map.of());
            emptyMandatory.init(FALSE);
            changed.init(FALSE);
            backwards.init(FALSE);
            try {
                observe(mutable(), observer().constructed());
                doRun(pre, universeTransaction);
            } catch (Throwable t) {
                do {
                    if (t instanceof ConsistencyError) {
                        throw (ConsistencyError) t;
                    } else if (t instanceof NullPointerException) {
                        throwable = Pair.of(Instant.now(), t);
                        return;
                    } else if (t.getCause() != null) {
                        t = t.getCause();
                    } else {
                        throwable = Pair.of(Instant.now(), t);
                        return;
                    }
                } while (true);
            } finally {
                observe(pre, observer, observeds.result());
                Map<Reason, Newable> cons = constructions.result();
                if (throwable == null) {
                    observer.constructed().set(mutable(), cons);
                }
                if (emptyMandatory.result().equals(TRUE) && throwable != null && throwable.b() instanceof NullPointerException) {
                    throwable = null;
                }
                observer.exception().set(mutable(), throwable);
                changed.clear();
                backwards.clear();
            }
        }
    }

    protected void doRun(State pre, UniverseTransaction universeTransaction) {
        super.run(pre, universeTransaction);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void observe(State pre, Observer<?> observer, DefaultMap<Observed, Set<Mutable>> observeds) {
        checkTooManyObserved(observeds);
        if (changed.result().equals(TRUE)) {
            checkTooManyChanges(pre, observeds);
            trigger(mutable(), (Observer<Mutable>) observer, Direction.forward);
        } else if (backwards.result().equals(TRUE)) {
            trigger(mutable(), (Observer<Mutable>) observer, Direction.backward);
        }
        DefaultMap preAll = super.set(mutable(), observer.observeds(), observeds);
        if (preAll.isEmpty() && !observeds.isEmpty()) {
            observer.instances++;
        } else if (!preAll.isEmpty() && observeds.isEmpty()) {
            observer.instances--;
        }
    }

    @SuppressWarnings("rawtypes")
    protected void checkTooManyObserved(DefaultMap<Observed, Set<Mutable>> observeds) {
        if (universeTransaction().stats().maxNrOfObserved() < size(observeds)) {
            throw new TooManyObservedException(mutable(), observer(), observeds, universeTransaction());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void checkTooManyChanges(State pre, DefaultMap<Observed, Set<Mutable>> observeds) {
        UniverseTransaction universeTransaction = universeTransaction();
        Observer<?> observer = observer();
        Mutable mutable = mutable();
        int totalChanges = universeTransaction.stats().bumpAndGetTotalChanges();
        int changesPerInstance = observer.countChangesPerInstance();
        if (changesPerInstance > universeTransaction.stats().maxNrOfChanges() || totalChanges > universeTransaction.stats().maxTotalNrOfChanges()) {
            universeTransaction.stats().setDebugging(true);
        }
        if (universeTransaction.stats().debugging()) {
            State result = merge();
            Set<ObserverTrace> traces = observer.traces.get(mutable);
            ObserverTrace trace = new ObserverTrace(mutable, observer, traces.sorted().findFirst().orElse(null), observer.changesPerInstance(), //
                    observeds.filter(e -> e.getKey().checkConsistency()).flatMap(e -> e.getValue().map(m -> {
                        m = m.resolve(mutable);
                        return Entry.of(ObservedInstance.of(m, e.getKey()), pre.get(m, e.getKey()));
                    })).toMap(e -> e), //
                    pre.diff(result, o -> o instanceof Mutable, s -> s instanceof Observed && s.checkConsistency).flatMap(e1 -> e1.getValue().map(e2 -> {
                        return Entry.of(ObservedInstance.of((Mutable) e1.getKey(), (Observed) e2.getKey()), e2.getValue().b());
                    })).toMap(e -> e));
            observer.traces.set(mutable, traces.add(trace));
            if (changesPerInstance > universeTransaction.stats().maxNrOfChanges() * 2) {
                hadleTooManyChanges(universeTransaction, mutable, observer, changesPerInstance);
            } else if (totalChanges > universeTransaction.stats().maxTotalNrOfChanges() + universeTransaction.stats().maxNrOfChanges()) {
                hadleTooManyChanges(universeTransaction, mutable, observer, totalChanges);
            }
        }
    }

    private void hadleTooManyChanges(UniverseTransaction universeTransaction, Mutable mutable, Observer<?> observer, int changes) {
        State result = merge();
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
            observe(object, (Observed<O, T>) getable);
        }
        T result = super.get(object, getable);
        if (result == null && getable instanceof Observed && ((Observed) getable).mandatory()) {
            emptyMandatory.set(TRUE);
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <O, T> T pre(O object, Getable<O, T> getable) {
        if (observing(object, getable)) {
            observe(object, (Observed<O, T>) getable);
        }
        T result = super.pre(object, getable);
        if (result == null && getable instanceof Observed && ((Observed) getable).mandatory()) {
            result = super.get(object, getable);
            if (result == null) {
                emptyMandatory.set(TRUE);
            }
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <O, T> T current(O object, Getable<O, T> getable) {
        if (observing(object, getable)) {
            observe(object, (Observed<O, T>) getable);
        }
        T result = super.current(object, getable);
        if (result == null && getable instanceof Observed && ((Observed) getable).mandatory()) {
            emptyMandatory.set(TRUE);
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    protected <T, O> void set(O object, Setable<O, T> setable, T pre, T post) {
        if (observing(object, setable)) {
            observe(object, (Observed<O, T>) setable);
            if (!Objects.equals(pre, post)) {
                T start = universeTransaction().startState().get(object, setable);
                if (setable.hasNewables()) {
                    post = matchNewables((Observed<O, T>) setable, start, pre, post);
                } else {
                    post = rippleOut((Observed<O, T>) setable, start, pre, post);
                }
            }
        }
        super.set(object, setable, pre, post);
    }

    @SuppressWarnings("rawtypes")
    private boolean isActive(Mutable mutable) {
        byte start = universeTransaction().startState().get(mutable, Mutable.D_CHANGE_NR);
        byte current = state().get(mutable, Mutable.D_CHANGE_NR);
        return current != start;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <O, T> void observe(O object, Observed<O, T> observed) {
        observeds.change(o -> o.add(observed.entry((Mutable) object, mutable()), Set::addAll));
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void handleMergeConflict(Object object, Setable property, Object pre, Object... branches) {
    }

    @Override
    public boolean isChanged() {
        return changed.merge().equals(TRUE);
    }

    @Override
    public void runNonObserving(Runnable action) {
        if (observeds.isInitialized()) {
            OBSERVE.run(false, action);
        } else {
            super.runNonObserving(action);
        }
    }

    @Override
    public <T> T getNonObserving(Supplier<T> action) {
        if (observeds.isInitialized()) {
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
        return object instanceof Mutable && setable instanceof Observed && observeds.isInitialized() && OBSERVE.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <O extends Newable> O construct(Construction.Reason reason, Supplier<O> supplier) {
        if (Constant.DERIVED.get() != null) {
            return super.construct(reason, supplier);
        } else {
            O result = (O) current(mutable(), observer().constructed()).get(reason);
            if (result == null) {
                O start = (O) universeTransaction().startState().get(mutable(), observer().constructed()).get(reason);
                result = start != null ? start : supplier.get();
                if (TRACE_MATCHING) {
                    O finalResult = result;
                    runNonObserving(() -> System.err.println("MATCH:  " + parent().indent("    ") + mutable() + "." + observer() + " (" + reason + "=>" + finalResult + ")"));
                }
            }
            constructions.set((map, e) -> map.put(reason, e), result);
            return result;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T, O> T rippleOut(Observed<O, T> observed, T start, T pre, T post) {
        if (!Objects.equals(pre, start)) {
            if (Objects.equals(start, post)) {
                backwards.set(TRUE);
                post = pre;
            } else if (start instanceof Mergeable) {
                T result = ((Mergeable<T>) start).merge(pre, post);
                if (!result.equals(post)) {
                    backwards.set(TRUE);
                    post = result;
                }
            }
        }
        if (observed.containment()) {
            if (pre instanceof Mutable && !pre.equals(post) && isActive((Mutable) pre)) {
                backwards.set(TRUE);
                post = pre;
            } else if (pre instanceof ContainingCollection && post instanceof ContainingCollection) {
                ContainingCollection<Object> pres = (ContainingCollection<Object>) pre;
                ContainingCollection<Object> posts = (ContainingCollection<Object>) post;
                T result = (T) posts.addAll(pres.filter(o -> o instanceof Mutable && !posts.contains(o) && isActive((Mutable) o)));
                if (!result.equals(post)) {
                    backwards.set(TRUE);
                    post = result;
                }
            }
        }
        return post;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <O, T> T matchNewables(Observed<O, T> observed, T start, T pre, T post) {
        merge();
        if (observed.isMany()) {
            return (T) manyMatch(observed, (ContainingCollection<Object>) start, (ContainingCollection<Object>) pre, (ContainingCollection<Object>) post);
        } else {
            return (T) singleMatch(observed, start, pre, post);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object singleMatch(Observed observed, Object start, Object before, Object after) {
        if (after instanceof Newable) {
            Newable matched = ((Newable) after).dMatched();
            if (matched instanceof Newable) {
                after = matched;
            } else if (before instanceof Newable && !((Newable) before).dIsObsolete()) {
                MatchInfo pre = MatchInfo.of((Newable) before);
                MatchInfo post = MatchInfo.of((Newable) after);
                if (pre.haveSameType(post)) {
                    if (!(Objects.equals(after, start) || post.hasDirectReasonToExist()) && (!Objects.equals(before, start) || pre.haveCyclicReason(post))) {
                        makeTheSame(pre, post);
                        after = before;
                    } else if (!(Objects.equals(before, start) || pre.hasDirectReasonToExist()) && (!Objects.equals(after, start) || post.haveCyclicReason(pre))) {
                        makeTheSame(post, pre);
                    }
                }
            }
        }
        return after;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ContainingCollection<Object> manyMatch(Observed observed, ContainingCollection<Object> start, ContainingCollection<Object> before, ContainingCollection<Object> after) {
        List<MatchInfo> preList = before == null ? List.of() : before.filter(Newable.class).exclude(after::contains).map(MatchInfo::of).toList();
        List<MatchInfo> postList = after == null ? List.of() : after.filter(Newable.class).map(MatchInfo::of).toList();
        for (MatchInfo pre : preList) {
            if (pre.newable().dIsObsolete()) {
                before = before.remove(pre.newable());
                preList = preList.remove(pre);
            }
        }
        if (!(after instanceof List) && !postList.isEmpty() && !preList.isEmpty()) {
            preList = preList.sortedBy(i -> i.newable().dSortKey()).toList();
            postList = postList.sortedBy(i -> i.sourcesSortKeys().findFirst().orElse(i.newable().dSortKey())).toList();
        }
        for (MatchInfo post : postList) {
            Newable matched = post.newable().dMatched();
            if (matched != null) {
                after = after.replace(post.newable(), matched);
                before = before.remove(post.newable());
                before = before.addUnique(matched);
            } else {
                for (MatchInfo pre : preList) {
                    if (pre.haveSameType(post)) {
                        if (!(start.contains(post.newable()) || post.hasDirectReasonToExist()) && ((!start.contains(pre.newable()) && pre.haveSameIdentity(post)) || pre.haveCyclicReason(post))) {
                            makeTheSame(pre, post);
                            after = after.replace(post.newable(), pre.newable());
                            before = before.remove(post.newable());
                            before = before.addUnique(pre.newable());
                        } else if (!(start.contains(pre.newable()) || pre.hasDirectReasonToExist()) && ((!start.contains(post.newable()) && post.haveSameIdentity(pre)) || post.haveCyclicReason(pre))) {
                            makeTheSame(post, pre);
                            before = before.remove(pre.newable());
                            before = before.addUnique(post.newable());
                        }
                    }
                }
            }
        }
        return Objects.equals(before, after) ? before : rippleOut(observed, start, before, after);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void makeTheSame(MatchInfo pre, MatchInfo post) {
        super.set(post.newable(), Newable.D_MATCHED, (Newable) null, pre.newable());
        if (TRACE_MATCHING) {
            runNonObserving(() -> System.err.println("MATCH:  " + parent().indent("    ") + mutable() + "." + observer() + " (" + pre.newable() + "==" + post.newable() + ")"));
        }
        for (Construction cons : post.constructions()) {
            Mutable obj = cons.object();
            Constructed set = cons.observer().constructed();
            super.set(obj, set, state().get(obj, set), current().get(obj, set).put(cons.reason(), pre.newable()));
        }
        constructions.set((map, n) -> {
            Optional<Entry<Reason, Newable>> found = map.filter(e -> e.getValue().equals(n)).findAny();
            return found.isPresent() ? map.put(found.get().getKey(), pre.newable()) : map;
        }, post.newable());
    }

}
