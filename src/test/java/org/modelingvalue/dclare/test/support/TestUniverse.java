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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.modelingvalue.dclare.ImperativeTransaction;
import org.modelingvalue.dclare.LeafTransaction;
import org.modelingvalue.dclare.Setable;
import org.modelingvalue.dclare.State;
import org.modelingvalue.dclare.Universe;
import org.modelingvalue.dclare.UniverseTransaction;

@SuppressWarnings("unused")
public class TestUniverse extends TestMutable implements Universe {

    public static TestUniverse of(Object id, TestMutableClass clazz, TestImperative scheduler) {
        return new TestUniverse(id, u -> {
        }, clazz, scheduler);
    }

    private static final Setable<TestUniverse, Long> DUMMY     = Setable.of("$DUMMY", 0l);

    private final TestImperative                     scheduler;
    private final BlockingQueue<Boolean>             idleQueue = new LinkedBlockingQueue<>(1);
    private final AtomicInteger                      counter   = new AtomicInteger(0);
    private Thread                                   waitForEndThread;
    private ImperativeTransaction                    imperativeTransaction;
    private Throwable                                uncaught;

    protected TestUniverse(Object id, Consumer<Universe> init, TestMutableClass clazz, TestImperative scheduler) {
        super(id, clazz);
        this.scheduler = scheduler;
    }

    @Override
    public void init() {
        Universe.super.init();
        UniverseTransaction utx = LeafTransaction.getCurrent().universeTransaction();
        imperativeTransaction = utx.addImperative("$TEST_CONNECTOR", null, (pre, post, last) -> {
            if (last) {
                idle();
            }
        }, scheduler, false);
        utx.dummy();
        waitForEndThread = new Thread(() -> {
            try {
                utx.waitForEnd();
            } catch (Throwable t) {
                uncaught = t;
                idle();
            }
        }, "TestUniverse.waitForEndThread");
        waitForEndThread.setDaemon(true);
        waitForEndThread.start();
        idle();
    }

    public int uniqueInt() {
        return counter.getAndIncrement();
    }

    public void schedule(Runnable action) {
        waitForIdle();
        if (!imperativeTransaction.universeTransaction().isKilled()) {
            imperativeTransaction.schedule(() -> {
                DUMMY.set(this, Long::sum, 1l);
                action.run();
            });
        }
    }

    private void idle() {
        try {
            idleQueue.put(Boolean.TRUE);
        } catch (InterruptedException e) {
            throw new Error(e);
        }
    }

    private void waitForIdle() {
        try {
            idleQueue.take();
        } catch (InterruptedException e) {
            throw new Error(e);
        }
    }

    public State waitForEnd() {
        return imperativeTransaction.waitForEnd();
    }

    public Throwable getUncaught() {
        return uncaught;
    }

    public State waitForEnd(UniverseTransaction universeTransaction) throws Throwable {
        try {
            State state = universeTransaction.waitForEnd();
            assertNull(uncaught);
            return state;
        } catch (Error e) {
            waitForEndThread.join(100);
            assertFalse(waitForEndThread.isAlive(), "the waitForEndThread probably hangs");
            assertNotNull(uncaught);
            assertEquals(e.getCause(), uncaught.getCause());
            throw e.getCause();
        }
    }

}
