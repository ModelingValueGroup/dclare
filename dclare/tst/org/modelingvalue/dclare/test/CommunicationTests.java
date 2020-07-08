//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2019 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

package org.modelingvalue.dclare.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.modelingvalue.dclare.test.Shared.*;

import org.junit.jupiter.api.Test;
import org.modelingvalue.dclare.Observed;
import org.modelingvalue.dclare.Observer;
import org.modelingvalue.dclare.Setable;
import org.modelingvalue.dclare.State;
import org.modelingvalue.dclare.UniverseTransaction;

public class CommunicationTests {
    @Test
    public void source2target() {
        final boolean IS_IMPLEMENTED = false;

        final int initialSourceValueA = 100;
        final int initialSourceValueB = 200;

        final int initialTargetValueA = 300;
        final int initialTargetValueB = 400;

        final int setValueA = 900;

        TestEnvironment a = new TestEnvironment("A", initialSourceValueA, initialTargetValueA, setValueA);
        TestEnvironment b = new TestEnvironment("B", initialSourceValueB, initialTargetValueB);

        UniverseTransaction txA = UniverseTransaction.of(a.universe, THE_POOL);
        UniverseTransaction txB = UniverseTransaction.of(b.universe, THE_POOL);

        if (IS_IMPLEMENTED) {
            // TODO: connect a <=> b
        }

        txA.put("stepA1", a.getRule1());
        txB.put("stepB1", b.getRule1());

        txA.put("stepA2", a.getRule2());
        // not on txB !!

        txA.stop();
        txB.stop();

        State resultA = txA.waitForEnd();
        State resultB = txB.waitForEnd();

        printState(txA, resultA);
        printState(txB, resultB);

        assertEquals(setValueA, (int) resultA.get(a.object, a.source));
        assertEquals(setValueA, (int) resultA.get(a.object, a.target));

        assertEquals(IS_IMPLEMENTED ? setValueA : initialSourceValueB, (int) resultB.get(b.object, b.source));
        assertEquals(IS_IMPLEMENTED ? setValueA : initialSourceValueB, (int) resultB.get(b.object, b.target));
    }

    private static class TestEnvironment {
        final         Observed<DUniverse, DObject> child;
        final         Observed<DObject, Integer>   source;
        final         Setable<DObject, Integer>    target;
        final         DClass                       dClass;
        final         DObject                      object;
        final         DUniverse                    universe;
        private final Integer                      targetValue;

        TestEnvironment(String name, int initialSourceValue, int initialTargetValue) {
            this(name, initialSourceValue, initialTargetValue, null);
        }

        TestEnvironment(String name, int initialSourceValue, int initialTargetValue, Integer targetValue) {
            this.targetValue = targetValue;

            child = Observed.of("child", null, true);
            source = Observed.of("source", initialSourceValue);
            target = Setable.of("target", initialTargetValue);
            dClass = DClass.of("Object", Observer.of("observer", o -> target.set(o, source.get(o))));
            object = DObject.of("object", dClass);
            universe = DUniverse.of("test-env-" + name, DClass.of("Universe", child));
        }

        Runnable getRule1() {
            return () -> child.set(universe, object);
        }

        Runnable getRule2() {
            assertNotEquals(targetValue, null);
            return () -> source.set(object, targetValue);
        }
    }
}
