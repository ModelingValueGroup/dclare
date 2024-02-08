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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.modelingvalue.collections.Collection;
import org.modelingvalue.dclare.Mutable;
import org.modelingvalue.dclare.MutableClass;
import org.modelingvalue.dclare.Observed;
import org.modelingvalue.dclare.Observer;
import org.modelingvalue.dclare.OneShot;
import org.modelingvalue.dclare.Setable;
import org.modelingvalue.dclare.StateMap;
import org.modelingvalue.dclare.Universe;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.modelingvalue.dclare.test.support.OneShotTests.TestUniverse.BASE;
import static org.modelingvalue.dclare.test.support.OneShotTests.TestUniverse.ELSE;
import static org.modelingvalue.dclare.test.support.OneShotTests.TestUniverse.STAR;

public class OneShotTests {
    static {
        System.setProperty("TRACE_ONE_SHOT", "false");
    }

    private static final String MARKER = "xyzzy";
    private static final String SEP    = "-";

    @Test
    public void reuseInOneTest() {
        AtomicInteger invokes1 = new AtomicInteger();
        AtomicInteger invokes2 = new AtomicInteger();

        for (int i = 0; i < 100; i++) {
            boolean          starred  = new Random().nextBoolean();
            TestOneShotInOne oneShot2 = new TestOneShotInOne(starred, i, invokes1, invokes2);
            StateMap         map2     = oneShot2.getEndStateMap();

            Observed<TestUniverse, String> updated    = starred ? STAR : ELSE;
            Observed<TestUniverse, String> notUpdated = starred ? ELSE : STAR;

            Assertions.assertEquals(MARKER, map2.get(TEST_UNIVERSE, BASE), "at " + i);
            Assertions.assertEquals(MARKER + SEP + i, map2.get(TEST_UNIVERSE, updated), "at " + i);
            Assertions.assertNull(map2.get(TEST_UNIVERSE, notUpdated), "at " + i);
        }
        Assertions.assertEquals(1, invokes1.get());
        Assertions.assertEquals(100, invokes2.get());
    }

    private static final TestUniverse TEST_UNIVERSE = new TestUniverse();

    public static class TestOneShotInOne extends OneShot<TestUniverse> {
        private final boolean       starred;
        private final int           end;
        private final AtomicInteger invokes1;
        private final AtomicInteger invokes2;

        public TestOneShotInOne(boolean starred, int end, AtomicInteger invokes1, AtomicInteger invokes2) {
            super(TEST_UNIVERSE);
            this.starred  = starred;
            this.end      = end;
            this.invokes1 = invokes1;
            this.invokes2 = invokes2;
        }

        @OneShotAction(caching = true)
        @SuppressWarnings("unused")
        public void action_00() {
            BASE.set(getUniverse(), MARKER);
            invokes1.incrementAndGet();
        }

        @OneShotAction
        @SuppressWarnings("unused")
        public void action_10() {
            Observed<TestUniverse, String> obs = starred ? STAR : ELSE;
            obs.set(getUniverse(), BASE.get(getUniverse()) + SEP + end);
            invokes2.incrementAndGet();
        }
    }

    public static class TestUniverse implements Universe {
        public static final Observed<TestUniverse, String> BASE    = Observed.of("BASE", null);
        public static final Observed<TestUniverse, String> STAR    = Observed.of("STAR", null);
        public static final Observed<TestUniverse, String> ELSE    = Observed.of("ELSE", null);
        public static final MutableClass                   D_CLASS = new MutableClass() {
            @Override
            public Collection<? extends Observer<?>> dObservers() {
                return Collection.of();
            }

            @Override
            public Collection<? extends Setable<? extends Mutable, ?>> dSetables() {
                return Collection.of();
            }
        };

        @Override
        public MutableClass dClass() {
            return D_CLASS;
        }
    }
}
