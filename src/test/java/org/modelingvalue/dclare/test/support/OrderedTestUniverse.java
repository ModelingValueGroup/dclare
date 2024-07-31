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

package org.modelingvalue.dclare.test.support;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.ImperativeTransaction;
import org.modelingvalue.dclare.LeafTransaction;
import org.modelingvalue.dclare.Mutable;
import org.modelingvalue.dclare.Setable;
import org.modelingvalue.dclare.State;
import org.modelingvalue.dclare.Universe;
import org.modelingvalue.dclare.UniverseTransaction;

@SuppressWarnings("unused")
public class OrderedTestUniverse extends TestMutable implements Universe {
    public static OrderedTestUniverse of(Object id, TestMutableClass clazz, int size, Set<Pair<Integer, Integer>> expectedEdges, Set<Pair<Integer, Integer>> actualEdges) {
        return new OrderedTestUniverse(id, clazz, size, expectedEdges, actualEdges);
    }

    private static final Setable<OrderedTestUniverse, Long> DUMMY     = Setable.of("$DUMMY", 0l);

    private final TestScheduler                      scheduler = TestScheduler.of();
    private final AtomicInteger                      counter   = new AtomicInteger(0);

    private UniverseTransaction                      universeTransaction;

    private List<ImperativeTransaction>              imperativeTransactions = List.of();
    private final int                                size;
    private Set<Pair<Integer, Integer>>              expectedEdges;
    private Set<Pair<Integer, Integer>>              actualEdges;
    private Map<Integer, Set<Integer>> incoming = Map.of();
    AtomicReference<Map<Integer, Boolean>> passed = new AtomicReference<>(Map.of());
    private AtomicBoolean                            flag = new AtomicBoolean(true);

    private OrderedTestUniverse(Object id, TestMutableClass clazz, int size, Set<Pair<Integer, Integer>> expectedEdges, Set<Pair<Integer, Integer>> actualEdges) {
        super(id, clazz);
        this.size = size;
        this.expectedEdges = expectedEdges;
        this.actualEdges = actualEdges;
    }

    @Override
    public void init() {
        scheduler.start();
        Universe.super.init();
        universeTransaction = LeafTransaction.getCurrent().universeTransaction();

        for (int i = 0; i < size; i++) {
            int finalI = i;
            passed.getAndUpdate(map -> map.put(finalI, false));
        }

        for (int i = 0; i < size; i++) {
            int finalI = i;
            imperativeTransactions = imperativeTransactions.add(universeTransaction.addImperative("TEST" + i, (pre, post, last, setted) -> {
                passed.getAndUpdate(list -> {
                    if (incoming.containsKey(finalI)) {
                        for (Integer inc : incoming.get((Integer) finalI)) {
                            if (!list.get(inc)) {
                                flag.set(false);
                            }
                        }
                    }

                    return list.put(finalI, true);
                });

                pre.diff(post, o -> o instanceof TestNewable, s -> s == Mutable.D_PARENT_CONTAINING).forEach(e -> {
                    if (e.getValue().get(Mutable.D_PARENT_CONTAINING).b() != null) {
                        TestNewable n = (TestNewable) e.getKey();
                        if (n.dInitialConstruction().isDerived()) {
                            TestNewable.construct(n, "init" + uniqueInt());
                        }
                    }
                });
            }, scheduler, false));
        }

        for (var edge : expectedEdges) {
            incoming = incoming.put(edge.b(), incoming.getOrDefault(edge.b(), Set.of()).add(edge.a()));
        }
        for (var edge : actualEdges) {
            universeTransaction.orderImperatives("TEST" + edge.a(), "TEST" + edge.b());
        }
    }

    @Override
    public void exit() {
        scheduler.stop();
        Universe.super.exit();
    }

    public boolean passed() {
        return flag.get() && passed.get().toValues().allMatch(b -> b);
    }

    public int uniqueInt() {
        return counter.getAndIncrement();
    }

    public void schedule(Set<Pair<Integer, Runnable>> actions) {
        actions.forEach(action -> imperativeTransactions.get(action.a()).schedule(() -> {
            DUMMY.set(this, Long::sum, 1L);
            action.b().run();
        }));
    }

    public State waitForEnd(UniverseTransaction universeTransaction) throws Throwable {
        try {
            return universeTransaction.waitForEnd();
        } catch (Error e) {
            throw e.getCause();
        }
    }

    @Override
    public boolean dIsOrphan(State state) {
        return Universe.super.dIsOrphan(state);
    }

}
