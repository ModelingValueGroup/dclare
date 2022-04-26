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

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.modelingvalue.collections.ContainingCollection;
import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.QualifiedSet;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Concurrent;
import org.modelingvalue.collections.util.Context;
import org.modelingvalue.collections.util.Mergeable;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.Construction.Reason;
import org.modelingvalue.dclare.ex.ConsistencyError;
import org.modelingvalue.dclare.ex.NonDeterministicException;
import org.modelingvalue.dclare.ex.TooManyChangesException;
import org.modelingvalue.dclare.ex.TooManyObservedException;

public class ObserverTransaction extends ActionTransaction {
    private static final Set<Boolean>                            FALSE          = Set.of();
    private static final Set<Boolean>                            TRUE           = Set.of(true);
    public static final Context<Boolean>                         OBSERVE        = Context.of(true);
    @SuppressWarnings("rawtypes")
    private final Concurrent<DefaultMap<Observed, Set<Mutable>>> gets           = Concurrent.of();
    @SuppressWarnings("rawtypes")
    private final Concurrent<DefaultMap<Observed, Set<Mutable>>> sets           = Concurrent.of();
    @SuppressWarnings({"rawtypes", "RedundantSuppression"})
    private final Concurrent<Map<Construction.Reason, Newable>>  constructions  = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       emptyMandatory = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       changed        = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       deferred       = Concurrent.of();
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

    @Override
    protected State merge() {
        gets.merge();
        sets.merge();
        constructions.merge();
        emptyMandatory.merge();
        changed.merge();
        deferred.merge();
        backwards.merge();
        return super.merge();
    }

    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    @Override
    protected final void run(State pre, UniverseTransaction universeTransaction) {
        Observer<?> observer = observer();
        Pair<Instant, Throwable> throwable = null;
        // System.err.println("!!!!!!!!RUN!!!!!!!! " + mutable() + "." + observer);
        // check if the universe is still in the same transaction, if not: reset my state
        observer.startTransaction(universeTransaction.stats());
        // check if we should do the work...
        if (!observer.isStopped() && !universeTransaction.isKilled()) {
            gets.init(Observed.OBSERVED_MAP);
            sets.init(Observed.OBSERVED_MAP);
            constructions.init(Map.of());
            emptyMandatory.init(FALSE);
            changed.init(FALSE);
            deferred.init(FALSE);
            backwards.init(FALSE);
            try {
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
                merge();
                observe(pre, observer, Observed.OBSERVED_MAP.merge(gets.get(), sets.get()));
                Map<Reason, Newable> cons = constructions.get();
                if (throwable == null) {
                    observer.constructed().set(mutable(), cons);
                }
                if (emptyMandatory.get().equals(TRUE) && throwable != null && throwable.b() instanceof NullPointerException) {
                    throwable = null;
                }
                observer.exception().set(mutable(), throwable);
                changed.clear();
                deferred.clear();
                backwards.clear();
                gets.clear();
                sets.clear();
                constructions.clear();
                emptyMandatory.clear();
            }
        }
    }

    protected void doRun(State pre, UniverseTransaction universeTransaction) {
        observe(mutable(), observer().constructed(), gets);
        super.run(pre, universeTransaction);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected Priority triggerPriority(Priority priority) {
        return priority;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void observe(State pre, Observer<?> observer, DefaultMap<Observed, Set<Mutable>> observeds) {
        checkTooManyObserved(observeds);
        if (changed.get().equals(TRUE)) {
            checkTooManyChanges(pre, observeds);
            trigger(mutable(), (Observer<Mutable>) observer, Priority.forward);
        } else if (deferred.get().equals(TRUE)) {
            trigger(mutable(), (Observer<Mutable>) observer, Priority.deferred);
        } else if (backwards.get().equals(TRUE)) {
            trigger(mutable(), (Observer<Mutable>) observer, Priority.backward);
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
                    observeds.filter(e -> !e.getKey().isPlumbing()).flatMap(e -> e.getValue().map(m -> {
                        m = m.dResolve(mutable);
                        return Entry.of(ObservedInstance.of(m, e.getKey()), pre.get(m, e.getKey()));
                    })).toMap(e -> e), //
                    pre.diff(result, o -> o instanceof Mutable, s -> s instanceof Observed && !s.isPlumbing()).flatMap(e1 -> e1.getValue().map(e2 -> Entry.of(ObservedInstance.of((Mutable) e1.getKey(), (Observed) e2.getKey()), e2.getValue().b()))).toMap(e -> e));
            observer.traces.set(mutable, traces.add(trace));
            if (changesPerInstance > stats.maxNrOfChanges() * 2) {
                handleTooManyChanges(universeTransaction, mutable, observer, changesPerInstance);
            } else if (totalChanges > stats.maxTotalNrOfChanges() + stats.maxNrOfChanges()) {
                handleTooManyChanges(universeTransaction, mutable, observer, totalChanges);
            }
        }
    }

    private void handleTooManyChanges(UniverseTransaction universeTransaction, Mutable mutable, Observer<?> observer, int changes) {
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
            //noinspection ConstantConditions
            observe(object, (Observed<O, T>) getable, gets);
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
            observe(object, (Observed<O, T>) getable, gets);
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
            observe(object, (Observed<O, T>) getable, gets);
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
        T result = post;
        if (observing(object, setable)) {
            if (((Observed) setable).mandatory() && !setable.isPlumbing() && !Objects.equals(pre, post) && ((Observed) setable).isEmpty(post) && emptyMandatory.merge().equals(TRUE)) {
                throw new NullPointerException(setable.toString());
            }
            observe(object, (Observed<O, T>) setable, sets);
            if (!Objects.equals(pre, post) && !setable.isPlumbing()) {
                merge();
                if (pre instanceof Newable && post instanceof Newable) {
                    result = (T) singleMatch((Mutable) object, (Observed) setable, pre, post);
                } else if (isNewableCollection(pre) && isNewableCollection(post)) {
                    result = (T) manyMatch((Mutable) object, (Observed) setable, (ContainingCollection<Object>) pre, (ContainingCollection<Object>) post);
                } else {
                    result = rippleOut(object, (Observed<O, T>) setable, pre, post);
                }
            }
        }
        super.set(object, setable, pre, result);
    }

    @SuppressWarnings("rawtypes")
    private static <T> boolean isNewableCollection(T val) {
        return val instanceof ContainingCollection && !((ContainingCollection) val).isEmpty() && ((ContainingCollection) val).get(0) instanceof Newable;
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantSuppression"})
    private <O, T> void observe(O object, Observed<O, T> observed, Concurrent<DefaultMap<Observed, Set<Mutable>>> observeds) {
        observeds.change(o -> o.add(observed.entry((Mutable) object, mutable()), Set::addAll));
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void handleMergeConflict(Object object, Setable property, Object pre, Object... branches) {
    }

    @Override
    public void runNonObserving(Runnable action) {
        if (gets.isInitialized()) {
            OBSERVE.run(false, action);
        } else {
            super.runNonObserving(action);
        }
    }

    @Override
    public <T> T getNonObserving(Supplier<T> action) {
        if (gets.isInitialized()) {
            return OBSERVE.get(false, action);
        } else {
            return super.getNonObserving(action);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantSuppression"})
    @Override
    protected <O, T> void changed(O object, Setable<O, T> setable, T preValue, T postValue) {
        if (observing(object, setable)) {
            changed.set(TRUE);
            // runNonObserving(() -> System.err.println("!!!!!!!!!!! " + this + "   " + object + "." + setable + "=" + postValue));
        }
        runNonObserving(() -> super.changed(object, setable, preValue, postValue));
    }

    private <O, T> boolean observing(O object, Getable<O, T> setable) {
        return object instanceof Mutable && setable instanceof Observed && gets.isInitialized() && OBSERVE.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <O extends Newable> O construct(Construction.Reason reason, Supplier<O> supplier) {
        if (Constant.DERIVED.get() != null) {
            return super.construct(reason, supplier);
        } else {
            O result = (O) current(mutable(), observer().constructed()).get(reason);
            if (result == null) {
                result = (O) startState().get(mutable(), observer().constructed()).get(reason);
                if (result == null) {
                    result = (O) postDeltaState().get(mutable(), observer().constructed()).get(reason);
                    if (result == null) {
                        result = supplier.get();
                    }
                }
                if (universeTransaction().getConfig().isTraceMatching()) {
                    O finalResult = result;
                    runNonObserving(() -> {
                        System.err.println("MATCH:  " + parent().indent("    ") + ((Observer<Mutable>) observer()).direction(mutable()) + "::" + mutable() + //
                                "." + observer() + " (" + reason.direction() + "::" + reason + "=>" + finalResult + ")");
                    });
                }
            }
            observer().constructed().set(mutable(), (map, e) -> map.put(reason, e), result);
            if (!(LeafTransaction.getCurrent() instanceof DerivationTransaction)) {
                constructions.set((map, e) -> map.put(reason, e), result);
            }
            return result;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantSuppression"})
    private <T, O> T rippleOut(O object, Observed<O, T> observed, T pre, T post) {
        TransactionId changedId = universeTransaction().subTransactionId();
        post = rippleOut(object, observed, pre, post, startState(), state(), changedId, deferred);
        if (!Objects.equals(pre, post)) {
            changedId = postDeltaState().get(universeTransaction().universe(), Mutable.D_CHANGE_ID);
            if (changedId != null) {
                changedId = changedId.superTransactionId();
                post = rippleOut(object, observed, pre, post, preDeltaState(), postDeltaState(), changedId, backwards);
            }
        }
        return post;
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantSuppression"})
    private <T, O> T rippleOut(O object, Observed<O, T> observed, T pre, T post, State preState, State postState, TransactionId changedId, Concurrent<Set<Boolean>> delay) {
        if (observed.containment() && !Setable.MOVING.get()) {
            if (pre instanceof ContainingCollection && post instanceof ContainingCollection) {
                ContainingCollection<Object> pres = (ContainingCollection<Object>) pre;
                ContainingCollection<Object> posts = (ContainingCollection<Object>) post;
                T result = (T) posts.addAll(pres.filter(o -> o instanceof Mutable && !posts.contains(o) && isChildChanged((Mutable) o, preState, postState, changedId)));
                if (!result.equals(post)) {
                    delay.set(TRUE);
                    post = result;
                }
            } else if (pre instanceof Mutable && isChildChanged((Mutable) pre, preState, postState, changedId)) {
                delay.set(TRUE);
                post = pre;

            }
        }
        if (!Objects.equals(pre, post)) {
            T preDelta = preState.get(object, observed);
            T postDelta = postState.get(object, observed);
            if (!Objects.equals(preDelta, postDelta)) {
                if (preDelta instanceof Mergeable && postDelta instanceof Mergeable && post instanceof Mergeable) {
                    T result = ((Mergeable<T>) preDelta).merge(postDelta, post);
                    if (!result.equals(post)) {
                        delay.set(TRUE);
                        post = result;
                    }
                } else {
                    delay.set(TRUE);
                    post = pre;
                }
            }
        }
        return post;
    }

    @SuppressWarnings({"rawtypes", "RedundantSuppression"})
    private boolean isChildChanged(Mutable mutable, State preState, State postState, TransactionId changedId) {
        if (preState.get(mutable, Mutable.D_PARENT_CONTAINING) != null) {
            TransactionId txid = postState.get(mutable, Mutable.D_CHANGE_ID);
            return txid != null && (changedId.isSub() ? txid == changedId : txid.superTransactionId() == changedId);
        } else {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object singleMatch(Mutable object, Observed observed, Object before, Object after) {
        MatchInfo pre = MatchInfo.of((Newable) before, this);
        MatchInfo post = MatchInfo.of((Newable) after, this);
        if (pre.mustBeTheSame(post)) {
            MatchInfo dir = matchDirection(pre, post);
            if (dir == pre) {
                return before;
            } else if (dir == post) {
                return after;
            }
        }
        return rippleOut(object, observed, before, after);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ContainingCollection<Object> manyMatch(Mutable object, Observed observed, ContainingCollection<Object> before, ContainingCollection<Object> after) {
        List<MatchInfo> preList = before.exclude(after::contains).filter(Newable.class).map(n -> MatchInfo.of(n, this)).toList();
        List<MatchInfo> postList = after.exclude(before::contains).filter(Newable.class).map(n -> MatchInfo.of(n, this)).toList();
        if (!(after instanceof List)) {
            preList = preList.sortedBy(MatchInfo::sortKey).toList();
            postList = postList.sortedBy(MatchInfo::sortKey).toList();
        }
        for (MatchInfo post : postList) {
            for (MatchInfo pre : preList) {
                if (pre.mustBeTheSame(post)) {
                    MatchInfo dir = matchDirection(pre, post);
                    if (dir == pre) {
                        after = after.replace(post.newable(), pre.newable());
                        break;
                    } else if (dir == post) {
                        before = before.replace(pre.newable(), post.newable());
                        break;
                    }
                }
            }
        }
        return Objects.equals(before, after) ? after : rippleOut(object, observed, before, after);
    }

    @SuppressWarnings("unchecked")
    private MatchInfo matchDirection(MatchInfo pre, MatchInfo post) {
        if (pre.newable().equals(post.newable().dReplacing())) {
            return pre;
        } else if (post.newable().equals(pre.newable().dReplacing())) {
            return post;
        } else if (!post.isCarvedInStone() && !pre.isCarvedInStone()) {
            if (pre.newable().dSortKey().compareTo(post.newable().dSortKey()) < 0) {
                makeTheSame(pre, post);
                return pre;
            } else {
                makeTheSame(post, pre);
                return post;
            }
        } else if (!post.isCarvedInStone()) {
            makeTheSame(pre, post);
            return pre;
        } else if (!pre.isCarvedInStone()) {
            makeTheSame(post, pre);
            return post;
        } else {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantSuppression"})
    private void makeTheSame(MatchInfo to, MatchInfo from) {
        if (universeTransaction().getConfig().isTraceMatching()) {
            runNonObserving(() -> System.err.println("MATCH:  " + parent().indent("    ") + ((Observer<Mutable>) observer()).direction(mutable()) + "::" + mutable() + "." + observer() + //
                    " (" + to.asString() + "==" + from.asString() + ")"));
        }
        super.set(from.newable(), Newable.D_REPLACING, Newable.D_REPLACING.getDefault(), to.newable());
        QualifiedSet<Direction, Construction> fromCons = current().get(from.newable(), Newable.D_DERIVED_CONSTRUCTIONS);
        QualifiedSet<Direction, Construction> toCons = current().get(to.newable(), Newable.D_DERIVED_CONSTRUCTIONS);
        super.set(to.newable(), Newable.D_DERIVED_CONSTRUCTIONS, toCons, toCons.putAll(fromCons));
        super.set(from.newable(), Newable.D_DERIVED_CONSTRUCTIONS, fromCons, Newable.D_DERIVED_CONSTRUCTIONS.getDefault());
        constructions.set((map, n) -> {
            Optional<Entry<Reason, Newable>> found = map.filter(e -> e.getValue().equals(n)).findAny();
            return found.isPresent() ? map.put(found.get().getKey(), to.newable()) : map;
        }, from.newable());
        to.mergeIn(from);
    }

    private State preDeltaState() {
        return universeTransaction().preDeltaState();
    }

    private State postDeltaState() {
        return universeTransaction().postDeltaState();
    }

    private State startState() {
        return universeTransaction().startState();
    }

}
