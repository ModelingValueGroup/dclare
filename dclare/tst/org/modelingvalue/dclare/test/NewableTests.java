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

package org.modelingvalue.dclare.test;

import static org.modelingvalue.dclare.test.support.Shared.THE_POOL;
import static org.modelingvalue.dclare.test.support.TestNewable.create;

import org.junit.jupiter.api.Test;
import org.modelingvalue.collections.Set;
import org.modelingvalue.dclare.Observed;
import org.modelingvalue.dclare.SetableModifier;
import org.modelingvalue.dclare.State;
import org.modelingvalue.dclare.UniverseTransaction;
import org.modelingvalue.dclare.test.support.TestMutable;
import org.modelingvalue.dclare.test.support.TestMutableClass;
import org.modelingvalue.dclare.test.support.TestNewable;
import org.modelingvalue.dclare.test.support.TestNewableClass;
import org.modelingvalue.dclare.test.support.TestUniverse;

public class NewableTests {

    static {
        System.setProperty("TRACE_MATCHING", "true");
    }

    @Test
    public void simpleBidirectional() {
        Observed<TestMutable, Set<TestNewable>> cs = Observed.of("cs", Set.of(), SetableModifier.containment);
        TestMutableClass U = TestMutableClass.of("Universe", cs);

        Observed<TestMutable, String> n = Observed.of("n", null);
        Observed<TestMutable, Set<TestNewable>> acs = Observed.of("acs", Set.of(), SetableModifier.containment);
        Observed<TestMutable, Set<TestNewable>> bcs = Observed.of("bcs", Set.of(), SetableModifier.containment);
        Observed<TestMutable, TestNewable> ar = Observed.of("a", null);
        Observed<TestMutable, TestNewable> br = Observed.of("b", null);

        TestNewableClass A = TestNewableClass.of("A", n::get, n, acs, br);
        TestNewableClass B = TestNewableClass.of("B", n::get, n, bcs, ar);
        TestNewableClass AC = TestNewableClass.of("AC", n::get, n, br);
        TestNewableClass BC = TestNewableClass.of("BC", n::get, n, ar);

        U.observe(u -> {
            Set<TestNewable> bs = cs.get(u).filter(B::isInstance).toSet();
            cs.set(u, bs.addAll(bs.map(ar::get)));
        }, u -> {
            Set<TestNewable> as = cs.get(u).filter(A::isInstance).toSet();
            cs.set(u, as.addAll(as.map(br::get)));
        });

        A.observe(a -> br.set(a, create(a, B, b -> n.set(b, n.get(a)), b -> bcs.set(b, acs.get(a).map(br::get).toSet()))));
        B.observe(b -> ar.set(b, create(b, A, a -> n.set(a, n.get(b)), a -> acs.set(a, bcs.get(b).map(ar::get).toSet()))));

        AC.observe(ac -> br.set(ac, create(ac, BC, bc -> n.set(bc, n.get(ac)))));
        BC.observe(bc -> ar.set(bc, create(bc, AC, ac -> n.set(ac, n.get(bc)))));

        TestUniverse universe = TestUniverse.of("universe", U);
        UniverseTransaction universeTransaction = UniverseTransaction.of(universe, THE_POOL);

        universeTransaction.put("init", () -> {
            TestNewable a1 = create(1, A);
            TestNewable b1 = create(2, B);
            TestNewable a2 = create(3, A);
            TestNewable b2 = create(4, B);
            TestNewable a3 = create(5, A);
            TestNewable b3 = create(6, B);
            cs.set(universe, Set.of(a1, a2, a3, b1, b2, b3));
            n.set(a1, "x");
            n.set(b1, "x");
            n.set(a2, "y");
            n.set(b2, "y");
            n.set(a3, "z");
            n.set(b3, "z");
            TestNewable ac1 = create(11, AC);
            TestNewable bc1 = create(12, BC);
            TestNewable ac2 = create(13, AC);
            TestNewable bc2 = create(14, BC);
            TestNewable ac3 = create(15, AC);
            TestNewable bc3 = create(16, BC);
            TestNewable ac4 = create(17, AC);
            TestNewable bc4 = create(18, BC);
            acs.set(a1, Set.of(ac1, ac2));
            bcs.set(b1, Set.of(bc1, bc2));
            acs.set(a2, Set.of(ac3, ac4));
            bcs.set(b2, Set.of(bc3, bc4));
            n.set(ac1, "p");
            n.set(bc1, "p");
            n.set(ac2, "q");
            n.set(bc2, "q");
            n.set(ac3, "r");
            n.set(bc3, "r");
            n.set(ac4, "s");
            n.set(bc4, "s");
        });

        universeTransaction.stop();
        State result = universeTransaction.waitForEnd();

        System.err.println(result.asString(o -> o instanceof TestMutable, s -> s instanceof Observed));

    }

}
