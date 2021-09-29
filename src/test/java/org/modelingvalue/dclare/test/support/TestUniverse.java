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

package org.modelingvalue.dclare.test.support;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.modelingvalue.dclare.Direction;
import org.modelingvalue.dclare.ImperativeTransaction;
import org.modelingvalue.dclare.LeafTransaction;
import org.modelingvalue.dclare.Mutable;
import org.modelingvalue.dclare.Setable;
import org.modelingvalue.dclare.State;
import org.modelingvalue.dclare.Universe;
import org.modelingvalue.dclare.UniverseTransaction;

@SuppressWarnings("unused")
public class TestUniverse extends TestMutable implements Universe {

    public static final Direction INIT = Direction.of("INIT");

    public static TestUniverse of(Object id, TestMutableClass clazz, TestImperative scheduler) {
        return new TestUniverse(id, u -> {
        }, clazz, scheduler);
    }

    private static final Setable<TestUniverse, Long> DUMMY   = Setable.of("$DUMMY", 0l);

    private final TestImperative                     scheduler;
    private final AtomicInteger                      counter = new AtomicInteger(0);

    private UniverseTransaction                      universeTransaction;
    private ImperativeTransaction                    imperativeTransaction;

    protected TestUniverse(Object id, Consumer<Universe> init, TestMutableClass clazz, TestImperative scheduler) {
        super(id, clazz);
        this.scheduler = scheduler;
    }

    @Override
    public void init() {
        Universe.super.init();
        universeTransaction = LeafTransaction.getCurrent().universeTransaction();
        imperativeTransaction = universeTransaction.addImperative("$TEST_CONNECTOR", (pre, post, last) -> {
            pre.diff(post, o -> o instanceof TestNewable, s -> s == Mutable.D_PARENT_CONTAINING).forEach(e -> {
                if (e.getValue().get(Mutable.D_PARENT_CONTAINING).b() != null) {
                    TestNewable n = (TestNewable) e.getKey();
                    if (n.dDirectConstruction() == null) {
                        TestNewable.construct(n, TestUniverse.INIT, "init" + uniqueInt());
                    }
                }
            });
        }, scheduler, false);
        universeTransaction.dummy();
    }

    public int uniqueInt() {
        return counter.getAndIncrement();
    }

    public void schedule(Runnable action) {
        imperativeTransaction.schedule(() -> {
            DUMMY.set(this, Long::sum, 1l);
            action.run();
        });

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
