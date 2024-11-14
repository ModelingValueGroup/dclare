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

package org.modelingvalue.dclare.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.modelingvalue.dclare.Logic.*;
import static org.modelingvalue.dclare.test.support.Shared.THE_POOL;

import org.junit.jupiter.api.RepeatedTest;
import org.modelingvalue.collections.Set;
import org.modelingvalue.dclare.Logic.Fun1;
import org.modelingvalue.dclare.Logic.Rel2;
import org.modelingvalue.dclare.Universe;
import org.modelingvalue.dclare.UniverseTransaction;

public class LogicTest {

    void run(Runnable test) {
        UniverseTransaction universeTransaction = new UniverseTransaction(Universe.of(), THE_POOL);
        universeTransaction.put("test", test);
        universeTransaction.stop();
        universeTransaction.waitForEnd();
    }

    static final Fun1<String, Set<String>> PARENTS   = fun1("parent");

    static final Fun1<String, Set<String>> ANCESTORS = fun1("ancestor",                          //
            (p) -> sup(PARENTS.get(p).addAll(PARENTS.get(p).flatMap(LogicTest.ANCESTORS::get))));

    static final Rel2<String, String>      PARENT    = rel2("parent",                            //
            (a, b) -> sup(PARENTS.get(a).contains(b)));

    static final Rel2<String, String>      ANCESTOR1 = rel2("isAncestor",                        //
            (a, o) -> or(PARENT.sup(o, a),                                                       //
                    any(PARENTS.get(o).map(p -> LogicTest.ANCESTOR1.sup(a, p)))));

    static final Rel2<String, String>      ANCESTOR2 = rel2("isAncestor",                        // 
            (a, o) -> or(PARENT.sup(o, a),                                                       //
                    uni((String x) -> and(LogicTest.ANCESTOR1.sup(a, x), PARENT.sup(o, x)))));

    @RepeatedTest(32)
    public void test1() {
        run(() -> {
            PARENTS.set("Jan", Set.of("Carel"));
            PARENTS.set("Wim", Set.of("Jan", "Elske"));
            PARENTS.set("Joppe", Set.of("Wim", "Heleen"));
            PARENTS.set("Marijn", Set.of("Wim", "Heleen"));
            assertEquals(ANCESTORS.sup("Joppe"), Set.of("Wim", "Heleen", "Jan", "Elske", "Carel"));

            assertTrue(ANCESTOR1.sup("Carel", "Marijn"));
            assertTrue(ANCESTOR1.sup("Wim", "Marijn"));
        });
    }
}
