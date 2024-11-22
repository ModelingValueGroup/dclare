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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.modelingvalue.dclare.Logic.*;
import static org.modelingvalue.dclare.test.support.Shared.THE_POOL;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.RepeatedTest;
import org.modelingvalue.collections.Collection;
import org.modelingvalue.dclare.Logic;
import org.modelingvalue.dclare.Logic.Term;
import org.modelingvalue.dclare.Universe;
import org.modelingvalue.dclare.UniverseTransaction;

public class LogicTest {

    void run(Runnable test) {
        UniverseTransaction universeTransaction = new UniverseTransaction(Universe.of(), THE_POOL);
        boolean seq = ThreadLocalRandom.current().nextBoolean();
        universeTransaction.put("test", seq ? Collection.sequential(test) : test);
        universeTransaction.stop();
        universeTransaction.waitForEnd();
    }

    static void isTrue(Term... goals) {
        assertTrue(is(goals));
    }

    static void isFalse(Term... goals) {
        assertFalse(is(goals));
    }

    interface Person extends Term {
    }

    Person person(String name) {
        return term(Person.class, name);
    }

    Person var(String name) {
        return Logic.var(Person.class, name);
    }

    interface ParentChild extends Term {
    }

    ParentChild parentChild(Person parent, Person child) {
        return term(ParentChild.class, parent, child);
    }

    interface AncestorDescendent extends Term {
    }

    AncestorDescendent ancestorDescendent(Person ancestor, Person descendent) {
        return term(AncestorDescendent.class, ancestor, descendent);
    }

    @RepeatedTest(1)
    public void test0() {
        run(() -> {
            Person A = var("A"); // Ancestor
            Person D = var("D"); // Descendent
            Person R = var("R"); // Relative

            rule(ancestorDescendent(A, D), parentChild(A, D));
            rule(ancestorDescendent(A, D), ancestorDescendent(A, R), parentChild(R, D));

            Person Carel = person("Carel");
            Person Jan = person("Jan");
            Person Elske = person("Elske");
            Person Wim = person("Wim");
            Person Joppe = person("Joppe");
            Person Heleen = person("Heleen");
            Person Marijn = person("Marijn");

            fact(parentChild(Carel, Jan));
            fact(parentChild(Jan, Wim));
            fact(parentChild(Elske, Wim));
            fact(parentChild(Wim, Joppe));
            fact(parentChild(Heleen, Joppe));
            fact(parentChild(Wim, Marijn));
            fact(parentChild(Heleen, Marijn));

            isTrue(parentChild(Heleen, Joppe));
            isTrue(parentChild(Jan, Wim));

            isFalse(parentChild(Marijn, Wim));
            isFalse(parentChild(Heleen, Wim));
            isFalse(parentChild(Wim, Wim));

            isTrue(ancestorDescendent(Wim, Marijn));
            isTrue(ancestorDescendent(Carel, Marijn));

            isFalse(ancestorDescendent(Marijn, Wim));
            isFalse(ancestorDescendent(Heleen, Wim));
            isFalse(ancestorDescendent(Joppe, Carel));
            isFalse(ancestorDescendent(Carel, Carel));
        });
    }

    @RepeatedTest(1)
    public void test1() {
        run(() -> {
            Person A = var("A"); // Ancestor
            Person D = var("D"); // Descendent
            Person R = var("R"); // Relative

            rule(ancestorDescendent(A, D), parentChild(A, D));
            rule(ancestorDescendent(A, D), ancestorDescendent(A, R), parentChild(R, D));

            Person Carel = person("Carel");
            Person Jan = person("Jan");
            Person Wim = person("Wim");

            fact(parentChild(Carel, Jan));
            fact(parentChild(Jan, Wim));

            isTrue(parentChild(Carel, Jan));
            isTrue(parentChild(Jan, Wim));

            isFalse(parentChild(Jan, Carel));
            isFalse(parentChild(Wim, Wim));

            isTrue(ancestorDescendent(Carel, Jan));
            isTrue(ancestorDescendent(Carel, Wim));
        });
    }
}
