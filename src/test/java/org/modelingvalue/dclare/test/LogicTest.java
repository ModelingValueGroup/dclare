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
import static org.modelingvalue.dclare.Arithmetic.*;
import static org.modelingvalue.dclare.Logic.*;
import static org.modelingvalue.dclare.test.support.Shared.THE_POOL;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.RepeatedTest;
import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.SerializableFunction;
import org.modelingvalue.dclare.Arithmetic;
import org.modelingvalue.dclare.Arithmetic.IntLit;
import org.modelingvalue.dclare.Logic;
import org.modelingvalue.dclare.Logic.Functor;
import org.modelingvalue.dclare.Logic.Goal;
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

    static void isTrue(Goal goal) {
        assertTrue(Logic.isTrue(goal));
    }

    static void isFalse(Goal goal) {
        assertTrue(Logic.isFalse(goal));
    }

    static void isIncomplete(Goal goal) {
        assertTrue(Logic.isIncomplete(goal));
    }

    @SafeVarargs
    static void hasBindings(Goal goal, Map<Variable, Object>... bindings) {
        assertEquals(Set.of(bindings), getBindings(goal));
    }

    // Root

    interface Root extends Term {
    }

    static Functor<Root> root = functor(LogicTest::root);

    static Root root(String name) {
        return term(root, name);
    }

    static Root rootVar(String name) {
        return var(Root.class, name);
    }

    static Functor<Term> rootPerson = functor(LogicTest::rootPerson);

    static Term rootPerson(Root root, Person person) {
        return term(rootPerson, root, person);
    }

    // FamilyTree

    interface Person extends Term {
    }

    static Functor<Person> strPerson = functor((SerializableFunction<String, Person>) LogicTest::person);

    static Person person(String name) {
        return term(strPerson, name);
    }

    static Functor<Person> intPerson = functor((SerializableFunction<IntLit, Person>) LogicTest::person);

    static Person person(IntLit i) {
        return term(intPerson, i);
    }

    static Person person(int i) {
        return person(i(i));
    }

    static Person personVar(String name) {
        return var(Person.class, name);
    }

    static Functor<Term> parentChild = functor(LogicTest::parentChild);

    static Term parentChild(Person parent, Person child) {
        return term(parentChild, parent, child);
    }

    static Functor<Term> ancestorDescendent = functor(LogicTest::ancestorDescendent);

    static Term ancestorDescendent(Person ancestor, Person descendent) {
        return term(ancestorDescendent, ancestor, descendent);
    }

    // Variables

    Root   U      = rootVar("U");

    IntLit P      = ilv("P");
    IntLit Q      = ilv("Q");

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

    Root   Root   = root("Root");

    @RepeatedTest(100)
    public void famTest1() {
        run(() -> {
            rule(ancestorDescendent(A, D), goal(parentChild(A, D)));
            rule(ancestorDescendent(A, D), goal(ancestorDescendent(A, R), parentChild(R, D)));

            fact(parentChild(Carel, Jan));
            fact(parentChild(Jan, Wim));
            fact(parentChild(Elske, Wim));
            fact(parentChild(Wim, Joppe));
            fact(parentChild(Heleen, Joppe));
            fact(parentChild(Wim, Marijn));
            fact(parentChild(Heleen, Marijn));

            isTrue(goal(parentChild(Heleen, Joppe)));
            isTrue(goal(parentChild(Jan, Wim)));

            isFalse(goal(parentChild(Marijn, Wim)));
            isFalse(goal(parentChild(Heleen, Wim)));
            isFalse(goal(parentChild(Wim, Wim)));

            isTrue(goal(ancestorDescendent(Wim, Marijn)));
            isTrue(goal(ancestorDescendent(Carel, Marijn)));

            isFalse(goal(ancestorDescendent(Marijn, Wim)));
            isFalse(goal(ancestorDescendent(Heleen, Wim)));
            isFalse(goal(ancestorDescendent(Joppe, Carel)));
            isFalse(goal(ancestorDescendent(Carel, Carel)));
        });
    }

    @RepeatedTest(100)
    public void famTest2() {
        run(() -> {
            rule(ancestorDescendent(A, D), goal(parentChild(A, D)));
            rule(ancestorDescendent(A, D), goal(parentChild(A, B), parentChild(B, D)));
            rule(ancestorDescendent(A, D), goal(parentChild(A, B), ancestorDescendent(B, C), parentChild(C, D)));

            Person Carel = person("Carel");
            Person Jan = person("Jan");
            Person Wim = person("Wim");

            fact(parentChild(Carel, Jan));
            fact(parentChild(Jan, Wim));

            hasBindings(goal(ancestorDescendent(A, Wim)), binding(A, Jan), binding(A, Carel));
            hasBindings(goal(ancestorDescendent(Carel, D)), binding(D, Jan), binding(D, Wim));
        });
    }

    @RepeatedTest(100)
    public void famTest3() {
        run(() -> {
            rule(parentChild(B, C), goal(parentChild(B, C)));

            Person Jan = person("Jan");
            Person Wim = person("Wim");

            hasBindings(goal(parentChild(Wim, Jan)), incomplete(parentChild(Wim, Jan), parentChild(Wim, Jan)));
            isIncomplete(goal(parentChild(Wim, Jan)));
        });
    }

    @RepeatedTest(1)
    public void famTest4() {
        run(() -> {
            Arithmetic.rules();

            rule(parentChild(person(Q), person(P)), goal(lt(Q, i(4)), is(plus(Q, i(1)), P)));
            rule(rootPerson(U, person(0)), goal());
            rule(rootPerson(U, D), goal(rootPerson(U, R), parentChild(R, D)));

            isTrue(goal(parentChild(person(0), person(1))));
            isTrue(goal(parentChild(person(3), person(4))));
            isFalse(goal(parentChild(person(4), person(5))));

            isTrue(goal(rootPerson(Root, person(0))));
            isTrue(goal(rootPerson(Root, person(1))));
            isTrue(goal(rootPerson(Root, person(2))));
            isTrue(goal(rootPerson(Root, person(3))));
            isTrue(goal(rootPerson(Root, person(4))));

            hasBindings(goal(rootPerson(Root, D)), binding(D, person(0)), binding(D, person(1)), //
                    binding(D, person(2)), binding(D, person(3)), binding(D, person(4)));
        });
    }

    @RepeatedTest(100)
    public void intTest() {
        run(() -> {
            Arithmetic.rules();

            hasBindings(goal(plus(i(7), i(3), P)), binding(P, i(10)));
            hasBindings(goal(plus(i(7), P, i(10))), binding(P, i(3)));
            hasBindings(goal(plus(P, i(3), i(10))), binding(P, i(7)));
        });
    }

    @RepeatedTest(100)
    public void isTest() {
        run(() -> {
            Arithmetic.rules();

            isTrue(goal(is(plus(i(11), i(22)), i(33))));
            isTrue(goal(is(minus(i(33), i(22)), i(11))));
            isTrue(goal(is(plus(i(11), plus(plus(i(22), i(33)), i(44))), i(110))));

            isTrue(goal(is(plus(i(11), divide(multiply(i(44), i(33)), i(22))), i(77))));

            isTrue(goal(is(sqrt(i(49)), i(7))));
            isTrue(goal(is(sqrt(i(49)), i(-7))));

            hasBindings(goal(is(plus(i(11), plus(plus(i(22), i(33)), i(44))), P)), binding(P, i(110)));
            hasBindings(goal(is(plus(i(11), plus(plus(i(22), P), i(44))), i(110))), binding(P, i(33)));
            hasBindings(goal(is(plus(i(7), i(3)), P)), binding(P, i(10)));
            hasBindings(goal(is(plus(i(7), P), i(10))), binding(P, i(3)));
            hasBindings(goal(is(plus(P, i(3)), i(10))), binding(P, i(7)));

            hasBindings(goal(is(sqrt(i(49)), P)), binding(P, i(7)), binding(P, i(-7)));
        });
    }

}
