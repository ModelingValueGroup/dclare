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

import org.modelingvalue.collections.*;
import org.modelingvalue.collections.util.Concurrent;
import org.modelingvalue.collections.util.Context;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.Construction.Reason;
import org.modelingvalue.dclare.Observer.Constructed;
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
    private final Concurrent<Set<Boolean>>                       defer       = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       setBack      = Concurrent.of();

    private Pair<Instant, Throwable>                             throwable;

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
        emptyMandatory.merge();
        changed.merge();
        defer.merge();
        setBack.merge();
        Map<Reason, Newable> cons = constructions.merge();
        if (throwable == null) {
            Set<Boolean> ch = changed.get();
            observer().constructed().set(mutable(), cons);
            changed.set(ch);
        }
        return super.merge();
    }

    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    @Override
    protected final void run(State pre, UniverseTransaction universeTransaction) {
        Observer<?> observer = observer();
        // check if the universe is still in the same transaction, if not: reset my state
        observer.startTransaction(universeTransaction.stats());
        // check if we should do the work...
        if (!observer.isStopped() && !universeTransaction.isKilled()) {
            gets.init(Observed.OBSERVED_MAP);
            sets.init(Observed.OBSERVED_MAP);
            constructions.init(Map.of());
            emptyMandatory.init(FALSE);
            changed.init(FALSE);
            defer.init(FALSE);
            setBack.init(FALSE);
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
                if (emptyMandatory.get().equals(TRUE) && throwable != null && throwable.b() instanceof NullPointerException) {
                    throwable = null;
                }
                observer.exception().set(mutable(), throwable);
                changed.clear();
                defer.clear();
                setBack.clear();
                gets.clear();
                sets.clear();
                constructions.clear();
                emptyMandatory.clear();
                throwable = null;
            }
        }
    }

    protected void doRun(State pre, UniverseTransaction universeTransaction) {
        observe(mutable(), observer().constructed(), gets);
        super.run(pre, universeTransaction);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void observe(State pre, Observer<?> observer, DefaultMap<Observed, Set<Mutable>> observeds) {
        checkTooManyObserved(observeds);
        if (changed.get().equals(TRUE)) {
            checkTooManyChanges(pre, observeds);
            trigger(mutable(), (Observer<Mutable>) observer, Priority.forward);
        } else if (defer.get().equals(TRUE)) {
            trigger(mutable(), (Observer<Mutable>) observer, Priority.deferred);
        } else if (setBack.get().equals(TRUE)) {
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
        if (observing(object, setable)) {
            if (((Observed) setable).mandatory() && !setable.isPlumbing() && !Objects.equals(pre, post) && ((Observed) setable).isEmpty(post) && emptyMandatory.merge().equals(TRUE)) {
                throw new NullPointerException(setable.toString());
            }
            observe(object, (Observed<O, T>) setable, sets);
            if (!setable.isPlumbing() && !Objects.equals(pre, post)) {
                merge();
                if (pre instanceof Newable || post instanceof Newable) {
                    post = (T) singleMatch((Mutable) object, (Observed) setable, pre, post);
                } else if (isCollection(pre) && isCollection(post) && (isNewableCollection(pre) || isNewableCollection(post))) {
                    post = (T) manyMatch((Mutable) object, (Observed) setable, (ContainingCollection<Object>) pre, (ContainingCollection<Object>) post);
                } else {
                    post = rippleOut(object, (Observed<O, T>) setable, pre, post);
                }
            }
        }
        super.set(object, setable, pre, post);
    }

    @SuppressWarnings("rawtypes")
    private static <T> boolean isCollection(T val) {
        return val == null || val instanceof ContainingCollection;
    }

    @SuppressWarnings("rawtypes")
    private static <T> boolean isNewableCollection(T val) {
        return val != null && !((ContainingCollection) val).isEmpty() && ((ContainingCollection) val).get(0) instanceof Newable;
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
    protected void setChanged(Mutable changed) {
        if (OBSERVE.get()) {
            super.setChanged(changed);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <O extends Newable> O construct(Construction.Reason reason, Supplier<O> supplier) {
        if (Constant.DERIVED.get() != null) {
            return super.construct(reason, supplier);
        } else {
            Constructed constructed = observer().constructed();
            O result = (O) current(mutable(), constructed).get(reason);
            if (result == null) {
                result = (O) prevOuterStartState().get(mutable(), constructed).get(reason);
                if (result == null) {
                    result = (O) innerStartState().get(mutable(), constructed).get(reason);
                    if (result == null) {
                        result = supplier.get();
                    }
                }
                constructed.set(mutable(), (map, e) -> map.put(reason, e), result);
            }
            constructions.set((map, e) -> map.put(reason, e), result);
            return result;
        }
    }

    @Override
    public <O extends Newable> O directConstruct(Construction.Reason reason, Supplier<O> supplier) {
        return super.construct(reason, supplier);
    }

    @SuppressWarnings("unchecked")
    private <T, O> T rippleOut(O object, Observed<O, T> observed, T pre, T post) {
        post = rippleOut(object, observed, pre, post, innerStartState(), state(), defer);
        if (!Objects.equals(pre, post)) {
            post = rippleOut(object, observed, pre, post, prevOuterStartState(), outerStartState(), setBack);
        }
        return post;
    }

    @SuppressWarnings("unchecked")
    private <T, O> T rippleOut(O object, Observed<O, T> observed, T pre, T post, IState preState, IState postState, Concurrent<Set<Boolean>> delay) {
        if (pre instanceof ContainingCollection && post instanceof ContainingCollection) {
            ContainingCollection<Object>[] result = new ContainingCollection[]{(ContainingCollection<Object>) post};
            Observed<O, ContainingCollection<Object>> many = (Observed<O, ContainingCollection<Object>>) observed;
            Setable.<T, Object> diff(pre, post, added -> {
                if (isChildChanged(observed, added, preState, postState) || isChangedBack(object, many, added, preState, postState) || isNewlyCreated(added, preState)) {
                    delay.set(TRUE);
                    result[0] = result[0].remove(added);
                }
            }, removed -> {
                if (isChildChanged(observed, removed, preState, postState) || isChangedBack(object, many, removed, postState, preState)) {
                    delay.set(TRUE);
                    if (pre instanceof List && post instanceof List) {
                        int i = Math.min(((List<Object>) pre).firstIndexOf(removed), result[0].size());
                        result[0] = ((List<Object>) result[0]).insert(i, removed);
                    } else {
                        result[0] = result[0].add(removed);
                    }
                }
            });
            return (T) result[0];
        } else if (isChildChanged(observed, pre, preState, postState) || isChildChanged(observed, post, preState, postState) || //
                isChangedBack(object, observed, pre, post, preState, postState) || isNewlyCreated(post, preState)) {
            delay.set(TRUE);
            return pre;
        } else {
            return post;
        }
    }

    private <O, T, E> boolean isChildChanged(Observed<O, T> observed, E element, IState preState, IState postState) {
        if (observed.containment() && element instanceof Mutable && preState.get((Mutable) element, Mutable.D_PARENT_CONTAINING) != null) {
            TransactionId txid = postState.get((Mutable) element, Mutable.D_CHANGE_ID);
            return txid != null && txid.number() > preState.get(universeTransaction().universe(), Mutable.D_CHANGE_ID).number();
        }
        return false;
    }

    private <O, E> boolean isChangedBack(O object, Observed<O, ContainingCollection<E>> many, E element, IState state1, IState state2) {
        return state1.get(object, many).contains(element) && !state2.get(object, many).contains(element);
    }

    private <O, T> boolean isChangedBack(O object, Observed<O, T> observed, T pre, T post, IState preState, IState postState) {
        T before = preState.get(object, observed);
        T after = postState.get(object, observed);
        return !Objects.equals(before, after) && Objects.equals(before, post);
    }

    private <E> boolean isNewlyCreated(E element, IState preState) {
        return element instanceof Newable && preState == innerStartState() && //
                ((Newable) element).dDerivedConstructions().anyMatch(c -> preState.get(c.object(), Mutable.D_PARENT_CONTAINING) == null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object singleMatch(Mutable object, Observed observed, Object before, Object after) {
        if (after instanceof Newable) {
            MatchInfo post = MatchInfo.of((Newable) after, this);
            List<MatchInfo> pres;
            if (post.isOnlyDerived()) {
                pres = preInfos(object, observed, before);
                for (MatchInfo pre : pres) {
                    if (pre.isDirect() && pre.mustReplace(post)) {
                        replace(post, pre);
                        after = pre.newable();
                        break;
                    }
                }
            } else if (post.isDirect()) {
                pres = preInfos(object, observed, before);
                for (MatchInfo pre : pres) {
                    if (pre.isOnlyDerived() && post.mustReplace(pre)) {
                        replace(pre, post);
                        before = post.newable();
                        break;
                    }
                }
            }
        }
        return !Objects.equals(before, after) ? rippleOut(object, observed, before, after) : after;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object manyMatch(Mutable object, Observed observed, ContainingCollection<Object> befores, ContainingCollection<Object> afters) {
        befores = befores != null ? befores : afters.clear();
        afters = afters != null ? afters : befores.clear();
        List<MatchInfo> pres = null;
        for (Object after : afters) {
            if (after instanceof Newable) {
                MatchInfo post = MatchInfo.of((Newable) after, this);
                if (post.isOnlyDerived()) {
                    if (pres == null) {
                        pres = preInfos(object, observed, befores);
                    }
                    for (MatchInfo pre : pres) {
                        if (pre.isDirect() && pre.mustReplace(post)) {
                            replace(post, pre);
                            afters = afters.replace(post.newable(), pre.newable());
                            break;
                        }
                    }
                } else if (post.isDirect()) {
                    if (pres == null) {
                        pres = preInfos(object, observed, befores);
                    }
                    for (MatchInfo pre : pres) {
                        if (pre.isOnlyDerived() && post.mustReplace(pre)) {
                            replace(pre, post);
                            befores = befores.replace(pre.newable(), post.newable());
                            break;
                        }
                    }
                }
            }
        }
        return !Objects.equals(befores, afters) ? rippleOut(object, observed, befores, afters) : afters;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<MatchInfo> preInfos(Mutable object, Observed observed, Object before) {
        Collection<Mutable> collection = observed.containment() ? (Collection<Mutable>) object.dChildren() : observed.collection(before);
        return collection.filter(Newable.class).map(n -> MatchInfo.of(n, this)).toList();
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantSuppression"})
    private void replace(MatchInfo replaced, MatchInfo replacing) {
        if (!replacing.newable().equals(replaced.replacing())) {
            replacing.replace(replaced);
            if (universeTransaction().getConfig().isTraceMatching()) {
                runNonObserving(() -> System.err.println("MATCH:  " + parent().indent("    ") + mutable() + "." + observer() + " (" + replacing + "==" + replaced + ")"));
            }
            super.set(replaced.newable(), Newable.D_REPLACING, Newable.D_REPLACING.getDefault(), replacing.newable());
            QualifiedSet<Direction, Construction> fromCons = current().get(replaced.newable(), Newable.D_DERIVED_CONSTRUCTIONS);
            QualifiedSet<Direction, Construction> toCons = current().get(replacing.newable(), Newable.D_DERIVED_CONSTRUCTIONS);
            super.set(replacing.newable(), Newable.D_DERIVED_CONSTRUCTIONS, toCons, toCons.putAll(fromCons));
            super.set(replaced.newable(), Newable.D_DERIVED_CONSTRUCTIONS, fromCons, Newable.D_DERIVED_CONSTRUCTIONS.getDefault());
            constructions.set((map, n) -> {
                Optional<Entry<Reason, Newable>> found = map.filter(e -> e.getValue().equals(n)).findAny();
                return found.isPresent() ? map.put(found.get().getKey(), replacing.newable()) : map;
            }, replaced.newable());
        }
    }

}
