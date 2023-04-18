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

import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;
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
    public static final Context<Boolean>                         RIPPLE_OUT     = Context.of(false);

    @SuppressWarnings("rawtypes")
    private final Concurrent<DefaultMap<Observed, Set<Mutable>>> observeds      = Concurrent.of();
    @SuppressWarnings({"rawtypes", "RedundantSuppression"})
    private final Concurrent<Map<Construction.Reason, Newable>>  constructions  = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       emptyMandatory = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       changed        = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       deferInner     = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       deferMid       = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       deferOuter     = Concurrent.of();

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
        observeds.merge();
        emptyMandatory.merge();
        changed.merge();
        deferInner.merge();
        deferOuter.merge();
        deferMid.merge();
        Map<Reason, Newable> cons = constructions.merge();
        if (throwable == null) {
            Set<Boolean> ch = changed.get();
            observer().constructed().set(mutable(), cons);
            changed.set(ch);
        }
        return super.merge();
    }

    private void rollback(boolean atomic) {
        if (atomic) {
            rollback();
            if (throwable == null) {
                observer().constructed().set(mutable(), constructions.get());
            }
        }
    }

    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    @Override
    protected final void run(State pre, UniverseTransaction universeTransaction) {
        Observer<?> observer = observer();
        // check if the universe is still in the same transaction, if not: reset my state
        observer.startTransaction(universeTransaction.stats());
        // check if we should do the work...
        if (!observer.isStopped() && !universeTransaction.isKilled()) {
            observeds.init(Observed.OBSERVED_MAP);
            constructions.init(Map.of());
            emptyMandatory.init(FALSE);
            changed.init(FALSE);
            deferInner.init(FALSE);
            deferMid.init(FALSE);
            deferOuter.init(FALSE);
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
                finish(pre, observer);
                changed.clear();
                deferInner.clear();
                deferMid.clear();
                deferOuter.clear();
                observeds.clear();
                constructions.clear();
                emptyMandatory.clear();
                throwable = null;
            }
        }
    }

    protected void doRun(State pre, UniverseTransaction universeTransaction) {
        observe(mutable(), observer().constructed());
        super.run(pre, universeTransaction);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void finish(State pre, Observer<?> observer) {
        DefaultMap<Observed, Set<Mutable>> observeds = this.observeds.get();
        checkTooManyObserved(observeds);
        boolean traced = false;
        if (!observer.atomic() && changed.get().equals(TRUE)) {
            traced = checkTooManyChanges(pre, observeds);
            trigger(mutable(), (Observer<Mutable>) observer, Priority.immediate);
        } else if (deferInner.get().equals(TRUE)) {
            rollback(observer.atomic());
            trigger(mutable(), (Observer<Mutable>) observer, Priority.inner);
        } else if (deferMid.get().equals(TRUE)) {
            rollback(observer.atomic());
            trigger(mutable(), (Observer<Mutable>) observer, Priority.mid);
        } else if (deferOuter.get().equals(TRUE)) {
            rollback(observer.atomic());
            trigger(mutable(), (Observer<Mutable>) observer, Priority.outer);
        } else if (observer.atomic() && changed.get().equals(TRUE)) {
            traced = checkTooManyChanges(pre, observeds);
            trigger(mutable(), (Observer<Mutable>) observer, Priority.immediate);
        }
        if (!traced && (observer().isTracing() || (universeTransaction().stats().debugging() && changed.get().equals(TRUE)))) {
            trace(pre, observeds);
        }
        DefaultMap preSources = super.set(mutable(), observer.observeds(), observeds);
        if (preSources.isEmpty() && !observeds.isEmpty()) {
            observer.addInstance();
        } else if (!preSources.isEmpty() && observeds.isEmpty()) {
            observer.removeInstance();
        }
        if (throwable != null && universeTransaction().getConfig().isTraceActions()) {
            runNonObserving(() -> {
                System.err.println(DclareTrace.getLineStart("DCLARE", this) + mutable() + "." + observer() + " (" + throwable.b() + ")");
            });
        }
        if (throwable != null && throwable.b() instanceof NullPointerException && emptyMandatory.get().equals(TRUE)) {
            throwable = null;
        }
        observer.exception().set(mutable(), throwable);
    }

    @SuppressWarnings("rawtypes")
    protected void checkTooManyObserved(DefaultMap<Observed, Set<Mutable>> observeds) {
        if (universeTransaction().stats().maxNrOfObserved() < size(observeds)) {
            throw new TooManyObservedException(mutable(), observer(), observeds, universeTransaction());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected boolean checkTooManyChanges(State pre, DefaultMap<Observed, Set<Mutable>> observeds) {
        UniverseStatistics stats = universeTransaction().stats();
        int totalChanges = stats.bumpAndGetTotalChanges();
        int changesPerInstance = observer().countChangesPerInstance();
        if (changesPerInstance > stats.maxNrOfChanges() || totalChanges > stats.maxTotalNrOfChanges()) {
            stats.setDebugging(true);
            ObserverTrace last = trace(pre, observeds);
            if (changesPerInstance > stats.maxNrOfChanges() * 2) {
                handleTooManyChanges(last, changesPerInstance);
            } else if (totalChanges > stats.maxTotalNrOfChanges() + stats.maxNrOfChanges()) {
                handleTooManyChanges(last, totalChanges);
            }
            return true;
        } else {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected ObserverTrace trace(State pre, DefaultMap<Observed, Set<Mutable>> observeds) {
        List<ObserverTrace> traces = observer().traces().get(mutable());
        ObserverTrace trace = new ObserverTrace(mutable(), observer(), traces.last(), observer().changesPerInstance(), //
                observeds.filter(e -> !e.getKey().isPlumbing()).flatMap(e -> e.getValue().map(m -> {
                    m = m.dResolve(mutable());
                    return Entry.of(ObservedInstance.of(m, e.getKey()), pre.get(m, e.getKey()));
                })).toMap(e -> e), //
                pre.diff(current(), o -> o instanceof Mutable, s -> s instanceof Observed && !s.isPlumbing()).flatMap(e1 -> {
                    return e1.getValue().map(e2 -> Entry.of(ObservedInstance.of((Mutable) e1.getKey(), (Observed) e2.getKey()), e2.getValue().b()));
                }).toMap(e -> e));
        observer().traces().set(mutable(), traces.append(trace));
        return trace;
    }

    @SuppressWarnings("rawtypes")
    private void handleTooManyChanges(ObserverTrace last, int changes) {
        if (last.done().size() >= (changes > universeTransaction().stats().maxTotalNrOfChanges() ? 1 : universeTransaction().stats().maxNrOfChanges())) {
            observer().stop();
            throw new TooManyChangesException(current(), last, changes);
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
        T result = post;
        if (observing(object, setable)) {
            if (((Observed) setable).mandatory() && !setable.isPlumbing() && !Objects.equals(pre, post) && ((Observed) setable).isEmpty(post) && emptyMandatory.merge().equals(TRUE)) {
                throw new NullPointerException(setable.toString());
            }
            observe(object, (Observed<O, T>) setable);
            if (!setable.isPlumbing() && !Objects.equals(pre, post)) {
                merge();
                result = getNonObserving(() -> {
                    if (pre instanceof Newable || post instanceof Newable) {
                        return (T) singleMatch((Mutable) object, (Observed) setable, pre, post);
                    } else if (isCollection(pre) && isCollection(post) && (isNewableCollection(pre) || isNewableCollection(post))) {
                        return (T) manyMatch((Mutable) object, (Observed) setable, (ContainingCollection<Object>) pre, (ContainingCollection<Object>) post);
                    } else {
                        return rippleOut(object, (Observed<O, T>) setable, pre, post);
                    }
                });
            }
        }
        super.set(object, setable, pre, result);
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
    private <O, T> void observe(O object, Observed<O, T> observed) {
        observeds.change(o -> o.add(observed.entry((Mutable) object, mutable()), Set::addAll));
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void handleMergeConflict(Object object, Setable property, Object pre, Object... branches) {
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

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantSuppression"})
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
    public <O extends Newable> O directConstruct(Construction.Reason reason, Supplier<O> supplier) {
        return super.construct(reason, supplier);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public <O extends Newable> O construct(Construction.Reason reason, Supplier<O> supplier) {
        if (Constant.DERIVED.get() != null) {
            return super.construct(reason, supplier);
        } else {
            Constructed constructed = observer().constructed();
            Construction cons = Construction.of(mutable(), observer(), reason);
            O result = (O) actualize(get(mutable(), constructed)).get(reason);
            if (result == null) {
                for (IState state : longHistory()) {
                    Newable found = actualize(state.get(mutable(), constructed)).get(reason);
                    if (found != null && state().get(found, Newable.D_ALL_DERIVATIONS).get(reason.direction()) == null) {
                        result = (O) found;
                        break;
                    }
                }
                if (result == null) {
                    result = supplier.get();
                    Newable.D_INITIAL_CONSTRUCTION.force(result, cons);
                }
            } else {
                O post = (O) actualize(midStartState().get(mutable(), constructed)).get(reason);
                if (post != null && !post.equals(result)) {
                    setConstructed(reason, cons, result);
                    deferMid.set(TRUE);
                    traceRippleOut(mutable(), observer(), result, post, false);
                    return post;
                }
            }
            setConstructed(reason, cons, result);
            return result;
        }

    }

    private void setConstructed(Construction.Reason reason, Construction cons, Newable result) {
        Newable.D_ALL_DERIVATIONS.set(result, QualifiedSet::put, cons);
        constructions.set((m, e) -> m.put(reason, e), result);
    }

    private Map<Reason, Newable> actualize(Map<Reason, Newable> map) {
        return map.flatMap(e -> e.getKey().actualize().map(r -> Entry.of(r, e.getValue()))).toMap(Function.identity());
    }

    @SuppressWarnings("unchecked")
    private <O, T, E> T rippleOut(O object, Observed<O, T> observed, T pre, T post) {
        return RIPPLE_OUT.get(true, () -> {
            boolean forward = isForward(object, observed, pre, post);
            if (isNonMapCollection(pre) && isNonMapCollection(post)) {
                ContainingCollection<E>[] result = new ContainingCollection[]{(ContainingCollection<E>) post};
                Observed<O, ContainingCollection<E>> many = (Observed<O, ContainingCollection<E>>) observed;
                Setable.<T, E> diff(pre, post, added -> {
                    Concurrent<Set<Boolean>> delay = added(object, many, added, forward);
                    if (delay != null) {
                        delay.set(TRUE);
                        result[0] = result[0].remove(added);
                    }
                }, removed -> {
                    Concurrent<Set<Boolean>> delay = removed(object, many, removed, forward);
                    if (delay != null) {
                        delay.set(TRUE);
                        if (pre instanceof List && post instanceof List) {
                            int i = Math.min(((List<E>) pre).firstIndexOf(removed), result[0].size());
                            result[0] = ((List<E>) result[0]).insert(i, removed);
                        } else {
                            result[0] = result[0].add(removed);
                        }
                    }
                });
                if (!Objects.equals(post, result[0])) {
                    traceRippleOut(object, observed, post, result[0], forward);
                }
                return (T) result[0];
            } else {
                Concurrent<Set<Boolean>> delay = changed(object, observed, pre, post, forward);
                if (delay != null) {
                    delay.set(TRUE);
                    traceRippleOut(object, observed, post, pre, forward);
                    return pre;
                } else {
                    return post;
                }
            }
        });
    }

    private <T> boolean isNonMapCollection(T t) {
        return t instanceof ContainingCollection && !(t instanceof Map) && !(t instanceof DefaultMap);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <O, T, E> boolean isForward(O outObject, Observed<O, T> outObserved, T pre, T post) {
        if (!Objects.equals(preInnerStartState().get(mutable(), Mutable.D_PARENT_CONTAINING), innerStartState().get(mutable(), Mutable.D_PARENT_CONTAINING)) || //
                !Objects.equals(innerStartState().get(mutable(), Mutable.D_PARENT_CONTAINING), state().get(mutable(), Mutable.D_PARENT_CONTAINING))) {
            return true;
        } else {
            return observeds.get().anyMatch(e -> e.getValue().anyMatch(o -> {
                Observed inObserved = e.getKey();
                if (!inObserved.isPlumbing()) {
                    Mutable inObject = o.dResolve(mutable());
                    if (inObject.equals(outObject) && inObserved.equals(outObserved)) {
                        if (pre instanceof ContainingCollection && post instanceof ContainingCollection) {
                            return isChanged(outObject, outObserved, (ContainingCollection<E>) pre, (ContainingCollection<E>) post, preInnerStartState(), innerStartState()) || //
                                    isChanged(outObject, outObserved, (ContainingCollection<E>) pre, (ContainingCollection<E>) post, innerStartState(), state());
                        }
                    } else {
                        return !Objects.equals(preInnerStartState().get(inObject, inObserved), innerStartState().get(inObject, inObserved)) || //
                                !Objects.equals(innerStartState().get(inObject, inObserved), state().get(inObject, inObserved));
                    }
                }
                return false;
            }));
        }
    }

    private <O, T, E> boolean isChanged(O object, Observed<O, T> many, ContainingCollection<E> pre, ContainingCollection<E> post, IState preState, IState postState) {
        boolean[] result = new boolean[1];
        Setable.<T, E> diff(preState.get(object, many), postState.get(object, many), added -> {
            result[0] = pre.contains(added) == post.contains(added);
        }, removed -> {
            result[0] = pre.contains(removed) == post.contains(removed);
        });
        return result[0];
    }

    private <O, T extends ContainingCollection<E>, E> Concurrent<Set<Boolean>> added(O object, Observed<O, T> observed, E added, boolean forward) {
        return added(object, observed, innerStartState(), state(), added, forward) ? deferInner : //
                becameDerived(observed, added, midStartState(), current()) ? deferMid : //
                        added(object, observed, preOuterStartState(), outerStartState(), added, forward) ? deferOuter : null;
    }

    private <O, T extends ContainingCollection<E>, E> Concurrent<Set<Boolean>> removed(O object, Observed<O, T> observed, E removed, boolean forward) {
        return removed(object, observed, innerStartState(), state(), removed, forward) ? deferInner : //
                becameContained(observed, removed, outerStartState(), innerStartState()) ? deferOuter : //
                        removed(object, observed, preOuterStartState(), outerStartState(), removed, forward) ? deferOuter : null;
    }

    private <O, T> Concurrent<Set<Boolean>> changed(O object, Observed<O, T> observed, T pre, T post, boolean forward) {
        return changed(object, observed, innerStartState(), state(), pre, post, forward) ? deferInner : //
                becameDerived(observed, post, midStartState(), current()) ? deferMid : //
                        becameContained(observed, pre, outerStartState(), innerStartState()) ? deferOuter : //
                                changed(object, observed, preOuterStartState(), outerStartState(), pre, post, forward) ? deferOuter : null;
    }

    private <O, T extends ContainingCollection<E>, E> boolean added(O object, Observed<O, T> observed, IState preState, IState postState, E added, boolean forward) {
        return isChildChanged(observed, added, preState, postState) || isRemoved(object, observed, added, preState, postState, forward);
    }

    private <O, T extends ContainingCollection<E>, E> boolean removed(O object, Observed<O, T> observed, IState preState, IState postState, E removed, boolean forward) {
        return isChildChanged(observed, removed, preState, postState) || isAdded(object, observed, removed, preState, postState, forward);
    }

    private <O, T> boolean changed(O object, Observed<O, T> observed, IState preState, IState postState, T pre, T post, boolean forward) {
        return isChildChanged(observed, pre, preState, postState) || isChildChanged(observed, post, preState, postState) || //
                isChangedBack(object, observed, pre, post, preState, postState, forward);
    }

    private <O, T, E> boolean becameDerived(Observed<O, T> observed, E element, IState preState, IState postState) {
        return element instanceof Newable && ((Newable) element).dInitialConstruction().isDerived() && //
                preState.get((Newable) element, Newable.D_ALL_DERIVATIONS).isEmpty() && //
                !postState.get((Newable) element, Newable.D_ALL_DERIVATIONS).isEmpty();
    }

    private <O, T, E> boolean becameContained(Observed<O, T> observed, E element, IState preState, IState postState) {
        return element instanceof Newable && //
                preState.get((Newable) element, Mutable.D_PARENT_CONTAINING) == null && //
                postState.get((Newable) element, Mutable.D_PARENT_CONTAINING) != null;
    }

    private <O, T, E> boolean isChildChanged(Observed<O, T> observed, E element, IState preState, IState postState) {
        if (observed.containment() && element instanceof Mutable && preState.get((Mutable) element, Mutable.D_PARENT_CONTAINING) != null) {
            TransactionId txid = postState.get((Mutable) element, Mutable.D_CHANGE_ID);
            return txid != null && txid.number() > preState.transactionId().number();
        }
        return false;
    }

    private <O, T extends ContainingCollection<E>, E> boolean isAdded(O object, Observed<O, T> observed, E element, IState preState, IState postState, boolean forward) {
        return !observed.collection(preState.get(object, observed)).contains(element) && (!forward || observed.collection(postState.get(object, observed)).contains(element));
    }

    private <O, T extends ContainingCollection<E>, E> boolean isRemoved(O object, Observed<O, T> observed, E element, IState preState, IState postState, boolean forward) {
        return observed.collection(preState.get(object, observed)).contains(element) && (!forward || !observed.collection(postState.get(object, observed)).contains(element));
    }

    private <O, T> boolean isChangedBack(O object, Observed<O, T> observed, T pre, T post, IState preState, IState postState, boolean forward) {
        T before = preState.get(object, observed);
        return Objects.equals(before, post) && (!forward || !Objects.equals(before, postState.get(object, observed)));
    }

    private <T, O> void traceRippleOut(O object, Feature feature, Object post, Object result, boolean forward) {
        if (universeTransaction().getConfig().isTraceRippleOut()) {
            String level = deferInner.get().equals(TRUE) ? "INNER" : deferMid.get().equals(TRUE) ? "MID" : forward ? "OUTER" : "BACKWARD";
            runNonObserving(() -> System.err.println(DclareTrace.getLineStart("DEFER", this) + mutable() + "." + observer() + " " + level + " (" + object + "." + feature + "=" + result + "<-" + post + ")"));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object singleMatch(Mutable object, Observed observed, Object before, Object after) {
        if (after instanceof Newable && before instanceof Newable && ((Newable) after).dNewableType().equals(((Newable) before).dNewableType())) {
            MatchInfo preInfo = MatchInfo.of((Newable) before, this, object, observed);
            MatchInfo postInfo = MatchInfo.of((Newable) after, this, object, observed);
            if (preInfo.mustReplace(postInfo)) {
                replace(postInfo, preInfo);
                after = preInfo.newable();
            } else if (postInfo.mustReplace(preInfo)) {
                replace(preInfo, postInfo);
                before = postInfo.newable();
            } else if (observed.containment()) {
                boolean found = false;
                for (Observed cont : MutableClass.D_CONTAINMENTS.get(object.dClass()).filter(Observed.class).exclude(observed::equals)) {
                    Object val = cont.current(object);
                    if (val instanceof Newable && ((Newable) after).dNewableType().equals(((Newable) val).dNewableType())) {
                        if (after.equals(val)) {
                            found = true;
                            break;
                        }
                        MatchInfo valInfo = MatchInfo.of((Newable) val, this, object, cont);
                        if (valInfo.identity() != null && valInfo.mustReplace(postInfo)) {
                            found = true;
                            replace(postInfo, valInfo);
                            after = val;
                            break;
                        }
                    }
                }
                if (!found && universeTransaction().getConfig().isTraceMatching()) {
                    MatchInfo finalPostInfo = postInfo;
                    runNonObserving(() -> System.err.println(DclareTrace.getLineStart("MATCH", this) + mutable() + "." + observer() + " (" + preInfo + "!=" + finalPostInfo + ")"));
                }
            }
        }
        return !Objects.equals(before, after) ? rippleOut(object, observed, before, after) : after;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object manyMatch(Mutable object, Observed observed, ContainingCollection<Object> bef, ContainingCollection<Object> aft) {
        ContainingCollection<Object> befores = bef != null ? bef : aft.clear();
        ContainingCollection<Object> afters = aft != null ? aft : bef.clear();
        QualifiedSet<Newable, MatchInfo> infos = null;
        ContainingCollection<Object> pres = befores;
        ContainingCollection<Object> results = afters.clear();
        Set<Object> befAdd = Set.of();
        Set<Object> befRem = Set.of();
        for (Object after : afters) {
            if (after instanceof Newable) {
                MatchInfo postInfo = infos != null ? infos.get((Newable) after) : null;
                for (Object pre : pres) {
                    if (pre instanceof Newable && ((Newable) after).dNewableType().equals(((Newable) pre).dNewableType())) {
                        if (after.equals(pre)) {
                            if (pres instanceof List) {
                                pres = pres.remove(pre);
                                break;
                            } else {
                                continue;
                            }
                        }
                        if (infos == null) {
                            infos = Collection.concat(befores, afters).distinct().filter(Newable.class).map(n -> MatchInfo.of(n, this, object, observed)).toQualifiedSet(MatchInfo::newable);
                            postInfo = infos.get((Newable) after);
                        }
                        MatchInfo preInfo = infos.get((Newable) pre);
                        if (preInfo.mustReplace(postInfo)) {
                            replace(postInfo, preInfo);
                            after = preInfo.newable();
                            pres = pres.remove(pre);
                            break;
                        } else if (postInfo.mustReplace(preInfo)) {
                            replace(preInfo, postInfo);
                            befRem = befRem.add(preInfo.newable());
                            befAdd = befAdd.add(postInfo.newable());
                            pres = pres.remove(pre);
                            break;
                        } else if (observed.containment() && universeTransaction().getConfig().isTraceMatching()) {
                            MatchInfo finalPostInfo = postInfo;
                            runNonObserving(() -> System.err.println(DclareTrace.getLineStart("MATCH", this) + mutable() + "." + observer() + " (" + preInfo + "!=" + finalPostInfo + ")"));
                        }
                    }
                }
            }
            results = results.addUnique(after);
        }
        if (bef instanceof List && aft instanceof List && !results.equals(aft)) {
            results = results.clear().addAll(bef.filter(results::contains)).addAll(results.exclude(bef::contains));
        }
        befores = befores.removeAll(befRem).addAll(befAdd);
        return !befores.equals(results) ? rippleOut(object, observed, befores, results) : results;
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantSuppression"})
    private void replace(MatchInfo replaced, MatchInfo replacing) {
        if (universeTransaction().getConfig().isTraceMatching()) {
            runNonObserving(() -> System.err.println(DclareTrace.getLineStart("MATCH", this) + mutable() + "." + observer() + " (" + replacing + "==" + replaced + ")"));
        }
        for (Construction cons : replaced.allDerivations()) {
            super.set(replacing.newable(), Newable.D_ALL_DERIVATIONS, QualifiedSet::put, cons);
            if (cons.object().equals(mutable()) && cons.observer().equals(observer())) {
                constructions.set((map, c) -> map.put(c.reason(), replacing.newable()), cons);
            }
        }
    }

    @Override
    protected String getCurrentTypeForTrace() {
        return "OB";
    }

}
