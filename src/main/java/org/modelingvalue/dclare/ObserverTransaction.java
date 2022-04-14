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
    private static final Set<Boolean>                            FALSE               = Set.of();
    private static final Set<Boolean>                            TRUE                = Set.of(true);
    public static final Context<Boolean>                         OBSERVE             = Context.of(true);
    @SuppressWarnings("rawtypes")
    private final Concurrent<DefaultMap<Observed, Set<Mutable>>> gets                = Concurrent.of();
    @SuppressWarnings("rawtypes")
    private final Concurrent<DefaultMap<Observed, Set<Mutable>>> sets                = Concurrent.of();
    @SuppressWarnings({"rawtypes", "RedundantSuppression"})
    private final Concurrent<Map<Construction.Reason, Newable>>  constructions       = Concurrent.of();
    private final Concurrent<Map<Construction.Reason, Newable>>  directConstructions = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       emptyMandatory      = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       changed             = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       backwards           = Concurrent.of();
    //
    private State                                                startState;

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
            directConstructions.init(Map.of());
            emptyMandatory.init(FALSE);
            changed.init(FALSE);
            backwards.init(FALSE);
            startState = universeTransaction.startState();
            try {
                observe(mutable(), observer().constructed(), gets);
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
                observe(pre, observer, Observed.OBSERVED_MAP.merge(gets.result(), sets.result()));
                directConstructions.clear();
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
                startState = null;
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
            trigger(mutable(), (Observer<Mutable>) observer, Priority.forward);
        } else if (backwards.result().equals(TRUE)) {
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
                result = (O) current(mutable(), observer().preConstructed()).get(reason);
                if (result == null) {
                    result = (O) startState.get(mutable(), observer().constructed()).get(reason);
                    if (result == null) {
                        result = supplier.get();
                    }
                }
                if (universeTransaction().getConfig().isTraceMatching()) {
                    O finalResult = result;
                    runNonObserving(() -> {
                        Set<Reason> reasons = mutable() instanceof Newable ? ((Newable) mutable()).dConstructions().map(Construction::reason).toSet() : Set.of();
                        System.err.println("MATCH:  " + parent().indent("    ") + ((Observer<Mutable>) observer()).direction(mutable()) + "::" + mutable() + //
                                reasons.toString().substring(3) + "." + observer() + " (" + reason.direction() + "::" + reason + "=>" + finalResult + ")");
                    });
                }
            }
            set(mutable(), observer().preConstructed(), (m, r) -> m.put(reason, r), result);
            constructions.set((map, e) -> map.put(reason, e), result);
            return result;
        }
    }

    @Override
    public <O extends Newable> O directConstruct(Construction.Reason reason, Supplier<O> supplier) {
        O result = super.construct(reason, supplier);
        directConstructions.set((map, e) -> map.put(reason, e), result);
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantSuppression"})
    private <T, O> T rippleOut(O object, Observed<O, T> observed, T pre, T post) {
        if (observed.containment() && !Setable.MOVING.get()) {
            if (pre instanceof ContainingCollection && post instanceof ContainingCollection) {
                ContainingCollection<Object> pres = (ContainingCollection<Object>) pre;
                ContainingCollection<Object> posts = (ContainingCollection<Object>) post;
                T result = (T) posts.addAll(pres.filter(o -> o instanceof Mutable && !posts.contains(o) && isChildChanged((Mutable) o)));
                if (!result.equals(post)) {
                    backwards.set(TRUE);
                    post = result;
                }
            } else if (pre instanceof Mutable && isChildChanged((Mutable) pre)) {
                backwards.set(TRUE);
                return pre;

            }
        }
        T start = startState.get(object, observed);
        if (!Objects.equals(pre, start)) {
            if (start instanceof Mergeable && pre instanceof Mergeable && post instanceof Mergeable) {
                T result = ((Mergeable<T>) start).merge(pre, post);
                if (!result.equals(post)) { // && post.equals(((Mergeable<T>) pre).merge(start, post)) && !inputIsChanged()) {
                    backwards.set(TRUE);
                    post = result;
                }
            } else if (Objects.equals(start, post) && !inputIsChanged()) {
                backwards.set(TRUE);
                return pre;
            }
        }
        return post;
    }

    @Override
    protected void setChanged(Mutable changed) {
        // Do nothing
    }

    @SuppressWarnings({"rawtypes", "RedundantSuppression"})
    private boolean isChildChanged(Mutable mutable) {
        if (startState.get(mutable, Mutable.D_PARENT_CONTAINING) != null) {
            return changeNr(startState, mutable) != changeNr(state(), mutable) && !(derivations(startState, mutable) > 0 && derivations(state(), mutable) == 0);
        } else {
            return false;
        }
    }

    private static byte changeNr(State state, Mutable mutable) {
        return state.get(mutable, Mutable.D_CHANGE_NR);
    }

    private static int derivations(State state, Mutable mutable) {
        return mutable instanceof Newable ? state.get((Newable) mutable, Newable.D_DERIVED_CONSTRUCTIONS).size() : -1;
    }

    @SuppressWarnings("unchecked")
    private boolean inputIsChanged() {
        return gets.merge().removeAll(sets.merge(), Set::removeAll).filter(e -> !e.getKey().isPlumbing()).anyMatch(e -> e.getValue().anyMatch(m -> {
            Mutable object = m.dResolve(mutable());
            return !Objects.equals(state().get(object, e.getKey()), startState.get(object, e.getKey()));
        }));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object singleMatch(Mutable object, Observed observed, Object before, Object after) {
        Map<Reason, Newable> cons = constructions.merge();
        MatchInfo pre = MatchInfo.of((Newable) before, cons, this);
        MatchInfo post = MatchInfo.of((Newable) after, cons, this);
        if (pre.haveSameType(post)) {
            if (!post.hasConstruction()) {
                return before;
            } else if (!pre.hasConstruction()) {
                return after;
            } else if (pre.mustBeTheSame(post)) {
                MatchInfo dir = matchDirection(pre, post);
                if (dir == pre) {
                    return before;
                } else if (dir == post) {
                    return after;
                }
            }
        }
        return rippleOut(object, observed, before, after);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ContainingCollection<Object> manyMatch(Mutable object, Observed observed, ContainingCollection<Object> before, ContainingCollection<Object> after) {
        Map<Reason, Newable> cons = constructions.merge();
        List<MatchInfo> pres = before.exclude(after::contains).filter(Newable.class).map(n -> MatchInfo.of(n, cons, this)).toList();
        List<MatchInfo> posts = after.exclude(before::contains).filter(Newable.class).map(n -> MatchInfo.of(n, cons, this)).toList();
        if (!(after instanceof List)) {
            pres = pres.sortedBy(MatchInfo::sortKey).toList();
            posts = posts.sortedBy(MatchInfo::sortKey).toList();
        }
        for (MatchInfo post : posts) {
            for (MatchInfo pre : pres) {
                if (pre.haveSameType(post)) {
                    if (!post.hasConstruction()) {
                        after = after.remove(post.newable());
                        break;
                    } else if (!pre.hasConstruction()) {
                        before = before.remove(pre.newable());
                        pres = pres.remove(pre);
                    } else if (pre.mustBeTheSame(post)) {
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
        }
        return Objects.equals(before, after) ? after : rippleOut(object, observed, before, after);
    }

    @SuppressWarnings("unchecked")
    private MatchInfo matchDirection(MatchInfo pre, MatchInfo post) {
        if (!post.isCarvedInStone() && !pre.isCarvedInStone()) {
            if (pre.newable().dSortKey().compareTo(post.newable().dSortKey()) < 0) {
                return makeTheSame(pre, post);
            } else {
                return makeTheSame(post, pre);
            }
        } else if (!post.isCarvedInStone()) {
            return makeTheSame(pre, post);
        } else if (!pre.isCarvedInStone()) {
            return makeTheSame(post, pre);
        } else {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantSuppression"})
    private MatchInfo makeTheSame(MatchInfo to, MatchInfo from) {
        if (universeTransaction().getConfig().isTraceMatching()) {
            runNonObserving(() -> System.err.println("MATCH:  " + parent().indent("    ") + ((Observer<Mutable>) observer()).direction(mutable()) + "::" + mutable() + "." + observer() + //
                    " (" + to.asString() + "==" + from.asString() + ")"));
        }
        QualifiedSet<Direction, Construction> toCons = current().get(to.newable(), Newable.D_DERIVED_CONSTRUCTIONS);
        QualifiedSet<Direction, Construction> fromCons = current().get(from.newable(), Newable.D_DERIVED_CONSTRUCTIONS);
        super.set(to.newable(), Newable.D_DERIVED_CONSTRUCTIONS, toCons, toCons.putAll(fromCons));
        super.set(from.newable(), Newable.D_DERIVED_CONSTRUCTIONS, fromCons, Newable.D_DERIVED_CONSTRUCTIONS.getDefault());
        constructions.set((map, n) -> {
            Optional<Entry<Reason, Newable>> found = map.filter(e -> e.getValue().equals(n)).findAny();
            return found.isPresent() ? map.put(found.get().getKey(), to.newable()) : map;
        }, from.newable());
        to.mergeIn(from);
        return to;
    }

}
