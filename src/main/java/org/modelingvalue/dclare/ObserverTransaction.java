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
        if (!observer.atomic() && changed.get().equals(TRUE)) {
            checkTooManyChanges(pre, observeds);
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
            checkTooManyChanges(pre, observeds);
            trigger(mutable(), (Observer<Mutable>) observer, Priority.immediate);
        }
        DefaultMap preSources = super.set(mutable(), observer.observeds(), observeds);
        if (preSources.isEmpty() && !observeds.isEmpty()) {
            observer.addInstance();
        } else if (!preSources.isEmpty() && observeds.isEmpty()) {
            observer.removeInstance();
        }
        if (emptyMandatory.get().equals(TRUE) && throwable != null && throwable.b() instanceof NullPointerException) {
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
            if (((Observed) setable).mandatory() && !setable.isPlumbing() && !Objects.equals(pre, post) && ((Observed) setable).isEmpty(post) && emptyMandatory.merge().equals(TRUE)) {
                throw new NullPointerException(setable.toString());
            }
            observe(object, (Observed<O, T>) setable);
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
    @SuppressWarnings("unchecked")
    public <O extends Newable> O construct(Construction.Reason reason, Supplier<O> supplier) {
        if (Constant.DERIVED.get() != null) {
            return super.construct(reason, supplier);
        } else {
            Constructed constructed = observer().constructed();
            Map<Reason, Newable> cons = current(mutable(), constructed);
            cons = cons.flatMap(e -> e.getKey().actualize().map(r -> Entry.of(r, e.getValue()))).toMap(Function.identity());
            O result = (O) cons.get(reason);
            if (result == null) {
                result = (O) preOuterStartState().get(mutable(), constructed).get(reason);
                if (result == null) {
                    result = (O) outerStartState().get(mutable(), constructed).get(reason);
                    if (result == null) {
                        result = (O) midStartState().get(mutable(), constructed).get(reason);
                        if (result == null) {
                            result = (O) preInnerStartState().get(mutable(), constructed).get(reason);
                            if (result == null) {
                                result = (O) innerStartState().get(mutable(), constructed).get(reason);
                                if (result == null) {
                                    result = supplier.get();
                                }
                            }
                        }
                    }
                }
                constructed.set(mutable(), (map, e) -> map.put(reason, e), result);
            }
            constructions.set((map, e) -> map.put(reason, e), result);
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    private <O, T, E> T rippleOut(O object, Observed<O, T> observed, T pre, T post) {
        boolean forward = isForward(object, observed, pre, post);
        if (pre instanceof ContainingCollection && post instanceof ContainingCollection) {
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
                traceRippleOut(object, observed, post, result[0]);
            }
            return (T) result[0];
        } else {
            Concurrent<Set<Boolean>> delay = changed(object, observed, pre, post, forward);
            if (delay != null) {
                delay.set(TRUE);
                traceRippleOut(object, observed, post, pre);
                return pre;
            } else {
                return post;
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <O, T, E> boolean isForward(O outObject, Observed<O, T> outObserved, T pre, T post) {
        return observeds.get().anyMatch(e -> e.getValue().anyMatch(o -> {
            Observed inObserved = e.getKey();
            if (!inObserved.isPlumbing()) {
                Mutable inObject = o.dResolve(mutable());
                if (inObject.equals(outObject) && inObserved.equals(outObserved)) {
                    if (pre instanceof ContainingCollection && post instanceof ContainingCollection) {
                        return isChanged(outObject, outObserved, (ContainingCollection<E>) pre, (ContainingCollection<E>) post, preInnerStartState(), innerStartState());
                    }
                } else {
                    return !Objects.equals(preInnerStartState().get(inObject, inObserved), innerStartState().get(inObject, inObserved));
                }
            }
            return false;
        }));
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
        return added(object, observed, innerStartState(), state(), added, true) ? deferInner : //
                isDerived(observed, added, midStartState(), current()) ? deferMid : //
                        added(object, observed, preOuterStartState(), outerStartState(), added, forward) ? deferOuter : null;
    }

    private <O, T extends ContainingCollection<E>, E> Concurrent<Set<Boolean>> removed(O object, Observed<O, T> observed, E removed, boolean forward) {
        return removed(object, observed, innerStartState(), state(), removed, true) ? deferInner : //
                isContained(observed, removed, outerStartState(), innerStartState()) ? deferOuter : //
                        removed(object, observed, preOuterStartState(), outerStartState(), removed, forward) ? deferOuter : null;
    }

    private <O, T> Concurrent<Set<Boolean>> changed(O object, Observed<O, T> observed, T pre, T post, boolean forward) {
        return changed(object, observed, innerStartState(), state(), pre, post, true) ? deferInner : //
                isDerived(observed, post, midStartState(), current()) ? deferMid : //
                        isContained(observed, pre, outerStartState(), innerStartState()) ? deferOuter : //
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

    private <O, T, E> boolean isDerived(Observed<O, T> observed, E element, IState preState, IState postState) {
        return element instanceof Newable && //
                preState.get((Newable) element, Newable.D_DERIVED_CONSTRUCTIONS).isEmpty() && //
                !postState.get((Newable) element, Newable.D_DERIVED_CONSTRUCTIONS).isEmpty();
    }

    private <O, T, E> boolean isContained(Observed<O, T> observed, E element, IState preState, IState postState) {
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

    private <T, O> void traceRippleOut(O object, Observed<O, T> observed, Object post, Object result) {
        if (universeTransaction().getConfig().isTraceRippleOut()) {
            String level = deferInner.get().equals(TRUE) ? "INNER" : deferMid.get().equals(TRUE) ? "MID" : "OUTER";
            runNonObserving(() -> System.err.println(DclareTrace.getLineStart("DEFER") + mutable() + "." + observer() + " " + level + " (" + object + "." + observed + "=" + result + "<-" + post + ")"));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object singleMatch(Mutable object, Observed observed, Object before, Object after) {
        if (after instanceof Newable) {
            MatchInfo post = MatchInfo.of((Newable) after, this, object, observed);
            List<MatchInfo> pres;
            if (post.canBeReplaced() || post.canBeReplacing()) {
                pres = preInfos(object, observed, before);
                for (MatchInfo pre : pres) {
                    if (pre.canBeReplacing() && post.canBeReplaced() && pre.mustReplace(post)) {
                        replace(post, pre);
                        after = pre.newable();
                        break;
                    } else if (post.canBeReplacing() && pre.canBeReplaced() && post.mustReplace(pre)) {
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
                MatchInfo post = MatchInfo.of((Newable) after, this, object, observed);
                if (post.canBeReplaced() || post.canBeReplacing()) {
                    if (pres == null) {
                        pres = preInfos(object, observed, befores);
                    }
                    for (MatchInfo pre : pres) {
                        if (pre.canBeReplacing() && post.canBeReplaced() && pre.mustReplace(post)) {
                            replace(post, pre);
                            if (befores instanceof List && afters instanceof List) {
                                afters = replaceInList((List<Object>) befores, (List<Object>) afters, post.newable(), pre.newable());
                            } else {
                                afters = afters.replace(post.newable(), pre.newable());
                            }
                            break;
                        } else if (post.canBeReplacing() && pre.canBeReplaced() && post.mustReplace(pre)) {
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

    private List<Object> replaceInList(List<Object> befores, List<Object> afters, Newable post, Newable pre) {
        int ib = befores.firstIndexOf(pre);
        int ia = afters.firstIndexOf(post);
        if (ib >= 0 && ib != ia && ib < afters.size()) {
            return afters.removeIndex(ia).insert(ib, pre);
        } else {
            return afters.replace(ia, pre);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<MatchInfo> preInfos(Mutable object, Observed observed, Object before) {
        if (observed.equalSemantics()) {
            Collection<Newable> befores = observed.collection(before).filter(Newable.class);
            Collection<Newable> equalSemantics = equalSemantics(object, observed).filter(Newable.class);
            return Collection.concat(befores.map(n -> MatchInfo.of(n, this, object, observed)), //
                    equalSemantics.map(n -> MatchInfo.of(n, this, object, observed)).filter(m -> m.identity() != null)).toList();
        } else {
            Collection<Mutable> collection = observed.collection(before);
            return collection.filter(Newable.class).map(n -> MatchInfo.of(n, this, object, observed)).toList();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Collection<Object> equalSemantics(Mutable object, Observed excluded) {
        return MutableClass.D_OBSERVEDS.get(object.dClass()).filter(Setable::equalSemantics).exclude(excluded::equals).flatMap(c -> c.getCollection(object));
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantSuppression"})
    private void replace(MatchInfo replaced, MatchInfo replacing) {
        if (!replacing.newable().equals(replaced.replacing())) {
            replacing.replace(replaced);
            if (universeTransaction().getConfig().isTraceMatching()) {
                runNonObserving(() -> System.err.println(DclareTrace.getLineStart("MATCH") + mutable() + "." + observer() + " (" + replacing + "==" + replaced + ")"));
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

    @Override
    protected String getCurrentTypeForTrace() {
        return "OB";
    }
}
