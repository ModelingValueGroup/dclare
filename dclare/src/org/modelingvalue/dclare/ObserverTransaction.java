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
import org.modelingvalue.dclare.Observed.ToBeMatched;
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
        observer.startTransaction(universeTransaction.stats());
        // check if we should do the work...
        if (!observer.isStopped() && !universeTransaction.isKilled()) {
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
        DefaultMap preSources = super.set(mutable(), observer.observeds(), observeds);
        if (preSources.isEmpty() && !observeds.isEmpty()) {
            observer.addInstance();
        } else if (!preSources.isEmpty() && observeds.isEmpty()) {
            observer.removeInstance();
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
        UniverseStatistics stats = universeTransaction.stats();
        int totalChanges = stats.bumpAndGetTotalChanges();
        int changesPerInstance = observer.countChangesPerInstance();
        if (changesPerInstance > stats.maxNrOfChanges() || totalChanges > stats.maxTotalNrOfChanges()) {
            stats.setDebugging(true);
        }
        if (stats.debugging()) {
            State result = merge();
            Set<ObserverTrace> traces = observer.traces.get(mutable);
            ObserverTrace trace = new ObserverTrace(mutable, observer, traces.sorted().findFirst().orElse(null), observer.changesPerInstance(), //
                    observeds.filter(e -> e.getKey().checkConsistency).flatMap(e -> e.getValue().map(m -> {
                        m = m.resolve(mutable);
                        return Entry.of(ObservedInstance.of(m, e.getKey()), pre.get(m, e.getKey()));
                    })).toMap(e -> e), //
                    pre.diff(result, o -> o instanceof Mutable, s -> s instanceof Observed && s.checkConsistency).flatMap(e1 -> e1.getValue().map(e2 -> {
                        return Entry.of(ObservedInstance.of((Mutable) e1.getKey(), (Observed) e2.getKey()), e2.getValue().b());
                    })).toMap(e -> e));
            observer.traces.set(mutable, traces.add(trace));
            if (changesPerInstance > stats.maxNrOfChanges() * 2) {
                hadleTooManyChanges(universeTransaction, mutable, observer, changesPerInstance);
            } else if (totalChanges > stats.maxTotalNrOfChanges() + stats.maxNrOfChanges()) {
                hadleTooManyChanges(universeTransaction, mutable, observer, totalChanges);
            }
        }
    }

    private void hadleTooManyChanges(UniverseTransaction universeTransaction, Mutable mutable, Observer<?> observer, int changes) {
        State result = merge();
        ObserverTrace last = result.get(mutable, observer.traces).sorted().findFirst().orElse(null);
        if (last != null && last.done().size() >= (changes > universeTransaction.stats().maxTotalNrOfChanges() ? 1 : universeTransaction.stats().maxNrOfChanges())) {
            observer.stop();
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
            T start = universeTransaction().startState().get(object, setable);
            if (setable.hasNewables()) {
                if (setable.isMany()) {
                    post = (T) manyMatch((Observed) setable, (ContainingCollection<Object>) start, (ContainingCollection<Object>) pre, (ContainingCollection<Object>) post);
                } else {
                    post = (T) singleMatch((Mutable) object, (Observed) setable, start, pre, post);
                }
            } else {
                post = rippleOut((Observed<O, T>) setable, start, pre, post);
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
                result = supplier.get();
            }
            constructions.set((map, e) -> map.put(reason, e), result);
            return result;
        }
    }

    @Override
    public <O extends Newable> O directConstruct(Construction.Reason reason, Supplier<O> supplier) {
        return super.construct(reason, supplier);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T, O> T rippleOut(Observed<O, T> observed, T start, T pre, T post) {
        if (!Objects.equals(pre, post)) {
            if (!Objects.equals(pre, start)) {
                if (Objects.equals(start, post)) {
                    backwards.set(TRUE);
                    return pre;
                } else if (start instanceof Mergeable) {
                    T result = ((Mergeable<T>) start).merge(pre, post);
                    if (!result.equals(post)) {
                        backwards.set(TRUE);
                        return result;
                    }
                }
            }
            if (observed.containment()) {
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
            if (observer().forwardChangesPerInstance() > universeTransaction().stats().maxNrOfForwardChanges()) {
                backwards.set(TRUE);
                return pre;
            }
        }
        return post;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object singleMatch(Mutable object, Observed observed, Object start, Object before, Object after) {
        if (before instanceof Newable || after instanceof Newable) {
            ToBeMatched<Mutable, Newable> toBeMatched = observed.toBeMatched();
            Set<Newable> startSet = universeTransaction().startState().get(object, toBeMatched);
            Set<Newable> startTotal = start instanceof Newable ? startSet.add((Newable) start) : startSet;
            Set<Newable> preSet = toBeMatched.get(object);
            Set<Newable> preTotal = before instanceof Newable ? preSet.add((Newable) before) : preSet;
            Set<Newable> postTotal = after instanceof Newable ? Set.of((Newable) after) : Set.of();
            Set<Newable> postResult = (Set<Newable>) manyMatch(toBeMatched, (Set) startTotal, (Set) preTotal, (Set) postTotal);
            List<Newable> sorted = postResult.sortedBy(Newable::dSortKey).toList();
            Object afterResult = observed.containment() ? sorted.first() : sorted.last();
            super.set(object, toBeMatched, preSet, afterResult != null ? postResult.remove(afterResult) : postResult);
            return afterResult;
        } else {
            return rippleOut(observed, start, before, after);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ContainingCollection<Object> manyMatch(Observed observed, ContainingCollection<Object> start, ContainingCollection<Object> before, ContainingCollection<Object> after) {
        if (Objects.equals(before, after)) {
            return after;
        } else {
            merge();
            Map<Reason, Newable> constructed = constructions.merge();
            List<MatchInfo> preList = before == null ? List.of() : before.filter(Newable.class).exclude(after::contains).map(n -> MatchInfo.of(n, constructed)).toList();
            List<MatchInfo> postList = after == null ? List.of() : after.filter(Newable.class).map(n -> MatchInfo.of(n, constructed)).toList();
            ContainingCollection<Object> beforeResult = before;
            ContainingCollection<Object> afterResult = after;
            for (MatchInfo pre : preList) {
                Newable matched = pre.newable().dMatched();
                if (matched != null) {
                    preList = preList.remove(pre);
                    if (after.contains(matched)) {
                        beforeResult = beforeResult.replace(pre.newable(), matched);
                    } else {
                        beforeResult = beforeResult.remove(pre.newable());
                    }
                }
            }
            if (!(after instanceof List) && !postList.isEmpty() && !preList.isEmpty()) {
                preList = preList.sortedBy(i -> i.newable().dSortKey()).toList();
                postList = postList.sortedBy(i -> i.sourcesSortKeys().findFirst().orElse(i.newable().dSortKey())).toList();
            }
            for (MatchInfo post : postList) {
                Newable matched = post.newable().dMatched();
                if (matched != null) {
                    afterResult = afterResult.replace(post.newable(), matched);
                    beforeResult = beforeResult.remove(post.newable());
                    beforeResult = beforeResult.addUnique(matched);
                } else {
                    for (MatchInfo pre : preList) {
                        if (pre.haveSameType(post)) {
                            if (!post.isCarvedInStone() && pre.shouldBeTheSame(post)) {
                                makeTheSame(pre, post);
                                afterResult = afterResult.replace(post.newable(), pre.newable());
                                beforeResult = beforeResult.remove(post.newable());
                                beforeResult = beforeResult.addUnique(pre.newable());
                            } else if (!pre.isCarvedInStone() && post.shouldBeTheSame(pre)) {
                                makeTheSame(post, pre);
                                beforeResult = beforeResult.remove(pre.newable());
                                beforeResult = beforeResult.addUnique(post.newable());
                            }
                        }
                    }
                }
            }
            return rippleOut(observed, start, beforeResult, afterResult);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void makeTheSame(MatchInfo pre, MatchInfo post) {
        super.set(post.newable(), Newable.D_MATCHED, (Newable) null, pre.newable());
        if (TRACE_MATCHING) {
            runNonObserving(() -> System.err.println("MATCH:  " + parent().indent("    ") + mutable() + "." + observer() + " (" + pre.newable() + pre.sourcesAndAncestors().toString().substring(3) + "==" + post.newable() + post.sourcesAndAncestors().toString().substring(3) + ")"));
        }
        Set<Construction> preCons = state().get(pre.newable(), Newable.D_DERIVED_CONSTRUCTIONS);
        Set<Construction> postCons = state().get(post.newable(), Newable.D_DERIVED_CONSTRUCTIONS);
        super.set(pre.newable(), Newable.D_DERIVED_CONSTRUCTIONS, preCons, preCons.addAll(postCons));
        super.set(post.newable(), Newable.D_DERIVED_CONSTRUCTIONS, postCons, Set.of());
        constructions.set((map, n) -> {
            Optional<Entry<Reason, Newable>> found = map.filter(e -> e.getValue().equals(n)).findAny();
            return found.isPresent() ? map.put(found.get().getKey(), pre.newable()) : map;
        }, post.newable());
    }

}
