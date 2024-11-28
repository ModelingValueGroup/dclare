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

import static org.junit.jupiter.api.Assertions.*;
import static org.modelingvalue.dclare.Logic.*;
import static org.modelingvalue.dclare.test.support.Shared.THE_POOL;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.RepeatedTest;
import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.dclare.Logic.Functor;
import org.modelingvalue.dclare.Logic.Term;
import org.modelingvalue.dclare.Logic.Variable;
import org.modelingvalue.dclare.Universe;
import org.modelingvalue.dclare.UniverseTransaction;

public class LogicTest {

    // Utilities

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

    static void hasResult(Set<Map<Variable, Object>> bindings, Term... goals) {
        assertEquals(bindings, eval(goals));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Set<Map<Variable, Object>> set(Map... bindings) {
        return Set.of(bindings);
    }

    // Integer

    interface Int extends Term {
    }

    static Functor<Int> i = functor(LogicTest::i);

    static Int i(Integer x) {
        return term(i, x);
    }

    static Int iv(String name) {
        return var(Int.class, name);
    }

    Int mOne = i(-1);
    Int zero = i(0);
    Int one  = i(1);

    Int U    = iv("U");
    Int V    = iv("V");
    Int W    = iv("W");
    Int X    = iv("X");
    Int Y    = iv("Y");
    Int Z    = iv("Z");

    // Plus

    interface Pred extends Term {
    }

    static Functor<Pred> plus = functor(LogicTest::plus, (Int a, Int b, Int c) -> Set.<Pred> of());

    static Pred plus(Int a, Int b, Int r) {
        return term(plus, a, b, r);
    }

    // FamilyTree

    interface Person extends Term {
    }

    static Functor<Person> person = functor(LogicTest::person);

    static Person person(String name) {
        return term(person, name);
    }

    static Person personVar(String name) {
        return var(Person.class, name);
    }

    interface ParentChild extends Term {
    }

    static Functor<ParentChild> parentChild = functor(LogicTest::parentChild);

    static ParentChild parentChild(Person parent, Person child) {
        return term(parentChild, parent, child);
    }

    static ParentChild parentChildVar(String name) {
        return var(ParentChild.class, name);
    }

    interface AncestorDescendent extends Term {
    }

    static Functor<AncestorDescendent> ancestorDescendent = functor(LogicTest::ancestorDescendent);

    static AncestorDescendent ancestorDescendent(Person ancestor, Person descendent) {
        return term(ancestorDescendent, ancestor, descendent);
    }

    static AncestorDescendent ancestorDescendentVar(String name) {
        return var(AncestorDescendent.class, name);
    }

    // Variables

    Person A      = personVar("A");  // Ancestor
    Person D      = personVar("D");  // Descendent
    Person R      = personVar("R");  // Relative
    Person B      = personVar("B");  // Relative1
    Person C      = personVar("C");  // Relative2

    // Terms

    Person Carel  = person("Carel");
    Person Jan    = person("Jan");
    Person Elske  = person("Elske");
    Person Wim    = person("Wim");
    Person Joppe  = person("Joppe");
    Person Heleen = person("Heleen");
    Person Marijn = person("Marijn");

    @RepeatedTest(100)
    public void test0() {
        run(() -> {
            rule(ancestorDescendent(A, D), parentChild(A, D));
            rule(ancestorDescendent(A, D), ancestorDescendent(A, R), parentChild(R, D));

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

    @RepeatedTest(100)
    public void test1() {
        run(() -> {
            rule(ancestorDescendent(A, D), parentChild(A, D));
            rule(ancestorDescendent(A, D), ancestorDescendent(A, R), parentChild(R, D));

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

    @RepeatedTest(100)
    public void test2() {
        run(() -> {
            rule(ancestorDescendent(A, D), parentChild(A, D));
            rule(ancestorDescendent(A, D), parentChild(A, B), parentChild(B, D));
            rule(ancestorDescendent(A, D), parentChild(A, B), ancestorDescendent(B, C), parentChild(C, D));

            Person Carel = person("Carel");
            Person Jan = person("Jan");
            Person Wim = person("Wim");

            fact(parentChild(Carel, Jan));
            fact(parentChild(Jan, Wim));

            hasResult(set(bind(A, Jan), bind(A, Carel)), ancestorDescendent(A, Wim));
            hasResult(set(bind(D, Jan), bind(D, Wim)), ancestorDescendent(Carel, D));
        });
    }

    @RepeatedTest(100)
    public void test3() {
        run(() -> {
            rule(parentChild(B, C), parentChild(B, C));

            Person Jan = person("Jan");
            Person Wim = person("Wim");

            hasResult(set(incomplete(parentChild(Wim, Jan), parentChild(Wim, Jan))), parentChild(Wim, Jan));
        });
    }

    @RepeatedTest(100)
    public void test4() {
        run(() -> {
            rule(plus(zero, zero, zero));
        });
    }
}
