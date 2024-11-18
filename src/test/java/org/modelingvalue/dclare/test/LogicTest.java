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

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.junit.jupiter.api.RepeatedTest;
import org.modelingvalue.collections.Collection;
import org.modelingvalue.dclare.Logic;
import org.modelingvalue.dclare.Logic.Is;
import org.modelingvalue.dclare.Logic.Rel2;
import org.modelingvalue.dclare.Logic.Relation1;
import org.modelingvalue.dclare.Logic.Relation2;
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

    static void isTrue(Supplier<Boolean> bs) {
        assertTrue(bs.get());
    }

    static void isFalse(Supplier<Boolean> bs) {
        assertTrue(!bs.get());
    }

    static void isTrue(Is is) {
    }

    static void isFalse(Is is) {
    }

    interface Person extends Relation1<String> {
    }

    Person person(String name) {
        return obj(Person.class, name);
    }

    Person var(String name) {
        return Logic.var(Person.class, name);
    }

    interface ParentChild extends Relation2<Person, Person> {
    }

    ParentChild parentChild(Person parent, Person child) {
        return obj(ParentChild.class, parent, child);
    }

    interface AncestorDescendent extends Relation2<Person, Person> {
    }

    AncestorDescendent ancestorDescendent(Person ancestor, Person descendent) {
        return obj(AncestorDescendent.class, ancestor, descendent);
    }

    @RepeatedTest(1024)
    public void test0() {
        run(() -> {
            Person Carel = person("Carel");
            Person Jan = person("Jan");
            Person Wim = person("Wim");
            Person Joppe = person("Joppe");

            Person A = var("A"); // Ancestor
            Person D = var("D"); // Descendent
            Person R = var("R"); // Relative

            fact(parentChild(Carel, Jan));
            fact(parentChild(Jan, Wim));
            fact(parentChild(Wim, Joppe));

            rule(ancestorDescendent(A, D), parentChild(A, D));
            rule(ancestorDescendent(A, D), R, ancestorDescendent(A, R), parentChild(R, D));

            isTrue(is(ancestorDescendent(Carel, Joppe)));

            isFalse(is(ancestorDescendent(Wim, Carel)));
        });
    }

    @RepeatedTest(1024)
    public void test1() {
        run(() -> {
            Rel2<Person, Person> parent_child = rel2("PC");
            Rel2<Person, Person> ancestor_descendent = rel2("AD");

            Person A = var("A"); // Ancestor
            Person D = var("D"); // Descendent
            Person R = var("R"); // Relative

            ancestor_descendent.rule(A, D, parent_child.is(A, D));
            ancestor_descendent.rule(A, D, uni(R, and(ancestor_descendent.is(A, R), parent_child.is(R, D))));

            Person Carel = person("Carel");
            Person Jan = person("Jan");
            Person Elske = person("Elske");
            Person Wim = person("Wim");
            Person Joppe = person("Joppe");
            Person Heleen = person("Heleen");
            Person Marijn = person("Marijn");

            parent_child.fact(Carel, Jan);
            parent_child.fact(Jan, Wim);
            parent_child.fact(Elske, Wim);
            parent_child.fact(Wim, Joppe);
            parent_child.fact(Heleen, Joppe);
            parent_child.fact(Wim, Marijn);
            parent_child.fact(Heleen, Marijn);

            isTrue(parent_child.is(Heleen, Joppe));
            isTrue(parent_child.is(Jan, Wim));

            isFalse(parent_child.is(Marijn, Wim));
            isFalse(parent_child.is(Heleen, Wim));
            isFalse(parent_child.is(Wim, Wim));

            isTrue(ancestor_descendent.is(Wim, Marijn));
            isTrue(ancestor_descendent.is(Carel, Marijn));

            isFalse(ancestor_descendent.is(Marijn, Wim));
            isFalse(ancestor_descendent.is(Heleen, Wim));
            isFalse(ancestor_descendent.is(Joppe, Carel));
            isFalse(ancestor_descendent.is(Carel, Carel));
        });
    }
}
