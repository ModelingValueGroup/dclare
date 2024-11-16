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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.modelingvalue.dclare.Logic.*;
import static org.modelingvalue.dclare.test.support.Shared.THE_POOL;

import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.RepeatedTest;
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

    static void isTrue(BooleanSupplier bs) {
        assertTrue(bs.getAsBoolean());
    }

    static void isFalse(BooleanSupplier bs) {
        assertTrue(!bs.getAsBoolean());
    }

    static final String               X        = "$X";

    static final Rel2<String, String> PARENT   = rel2("parent");

    static final Rel2<String, String> ANCESTOR = rel2("ancestor",               // 
            (a, o) -> or(PARENT.is(a, o),                                       //
                    uni(X, and(LogicTest.ANCESTOR.is(a, X), PARENT.is(X, o)))));

    @RepeatedTest(32)
    public void test1() {
        run(() -> {
            PARENT.fact("Carel", "Jan");
            PARENT.fact("Jan", "Wim");
            PARENT.fact("Elske", "Wim");
            PARENT.fact("Wim", "Joppe");
            PARENT.fact("Heleen", "Joppe");
            PARENT.fact("Wim", "Marijn");
            PARENT.fact("Heleeen", "Marijn");

            isTrue(PARENT.is("Heleen", "Joppe"));
            isTrue(PARENT.is("Jan", "Wim"));

            isFalse(PARENT.is("Marijn", "Wim"));
            isFalse(PARENT.is("Heleeen", "Wim"));

            isTrue(ANCESTOR.is("Carel", "Marijn"));
            isTrue(ANCESTOR.is("Wim", "Marijn"));

            isFalse(ANCESTOR.is("Marijn", "Wim"));
            isFalse(ANCESTOR.is("Heleeen", "Wim"));
        });
    }
}
