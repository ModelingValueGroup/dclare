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
import static org.modelingvalue.dclare.logic.Arithmetic.*;
import static org.modelingvalue.dclare.logic.Logic.*;
import static org.modelingvalue.dclare.test.support.Shared.THE_POOL;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.RepeatedTest;
import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.SerializableFunction;
import org.modelingvalue.dclare.Universe;
import org.modelingvalue.dclare.UniverseTransaction;
import org.modelingvalue.dclare.logic.Arithmetic.IntAtom;
import org.modelingvalue.dclare.logic.Logic;
import org.modelingvalue.dclare.logic.Logic.*;

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

    interface RootAtom extends Root, Atom<Root> {
    }

    interface RootFunc extends Root, Func<Root> {
    }

    static Functor<RootAtom> rootAtom = functor((SerializableFunction<String, RootAtom>) LogicTest::root);

    static RootAtom root(String name) {
        return term(rootAtom, name);
    }

    static RootAtom rootAtomVar(String name) {
        return var(RootAtom.class, name);
    }

    static Root rootVar(String name) {
        return var(Root.class, name);
    }

    static Functor<Pred> rootPerson = functor(LogicTest::rootPerson);

    static Pred rootPerson(RootAtom root, PersonAtom person) {
        return term(rootPerson, root, person);
    }

    static Functor<RootFunc> rootFunc = functor((SerializableFunction<Person, RootFunc>) LogicTest::root);

    static RootFunc root(Person person) {
        return term(rootFunc, person);
    }

    // Family Tree

    interface Person extends Term {
    }

    interface PersonAtom extends Person, Atom<Person> {
    }

    interface PersonFunc extends Person, Func<Person> {
    }

    static Functor<PersonAtom> strPerson = functor((SerializableFunction<String, PersonAtom>) LogicTest::person);

    static PersonAtom person(String name) {
        return term(strPerson, name);
    }

    static Functor<PersonAtom> intPerson = functor((SerializableFunction<IntAtom, PersonAtom>) LogicTest::person);

    static PersonAtom person(IntAtom i) {
        return term(intPerson, i);
    }

    static PersonAtom person(int i) {
        return person(i(i));
    }

    static PersonAtom personAtomVar(String name) {
        return var(PersonAtom.class, name);
    }

    static Person personVar(String name) {
        return var(Person.class, name);
    }

    static Functor<Rel> parentChild = functor(LogicTest::parentChild);

    static Rel parentChild(PersonAtom parent, PersonAtom child) {
        return term(parentChild, parent, child);
    }

    static Functor<PersonFunc> parent = functor(LogicTest::parent);

    static PersonFunc parent(Person child) {
        return term(parent, child);
    }

    static Functor<PersonFunc> child = functor(LogicTest::child);

    static PersonFunc child(Person parent) {
        return term(child, parent);
    }

    static Functor<Pred> ancestorDescendent = functor(LogicTest::ancestorDescendent);

    static Pred ancestorDescendent(PersonAtom ancestor, PersonAtom descendent) {
        return term(ancestorDescendent, ancestor, descendent);
    }

    static Functor<PersonFunc> ancestor = functor(LogicTest::ancestor);

    static PersonFunc ancestor(Person descendent) {
        return term(ancestor, descendent);
    }

    static Functor<PersonFunc> descendent = functor(LogicTest::descendent);

    static PersonFunc descendent(Person ancestor) {
        return term(descendent, ancestor);
    }

    // Variables

    IntAtom    P      = iav("P");
    IntAtom    Q      = iav("Q");

    PersonAtom A      = personAtomVar("A");
    PersonAtom B      = personAtomVar("B");
    PersonAtom C      = personAtomVar("C");

    Person     X      = personVar("X");
    Person     Y      = personVar("Y");
    Person     Z      = personVar("Z");

    RootAtom   U      = rootAtomVar("U");
    Root       V      = rootVar("V");

    @SuppressWarnings("unchecked")
    L<Person>  PL     = var(L.class, "PL");

    // Terms

    PersonAtom Carel  = person("Carel");
    PersonAtom Jan    = person("Jan");
    PersonAtom Elske  = person("Elske");
    PersonAtom Wim    = person("Wim");
    PersonAtom Joppe  = person("Joppe");
    PersonAtom Heleen = person("Heleen");
    PersonAtom Marijn = person("Marijn");

    RootAtom   Root   = root("Root");

    // Root Rules

    private void rootRules() {
        arithmeticRules();

        rule(is(parent(X), A), goal(is(X, B), parentChild(A, B)));
        rule(is(child(X), A), goal(is(X, B), parentChild(B, A)));

        rule(is(root(X), U), goal(is(X, B), rootPerson(U, B)));

        rule(parentChild(person(Q), person(P)), goal(lt(Q, i(4)), is(plus(Q, i(1)), P)));
        rule(rootPerson(U, person(0)), goal());
        rule(rootPerson(U, C), goal(rootPerson(U, A), parentChild(A, C)));
    }

    // Family Rules

    private void familyRules() {
        isAtomRule();

        rule(is(parent(X), A), goal(is(X, B), parentChild(A, B)));
        rule(is(child(X), A), goal(is(X, B), parentChild(B, A)));

        rule(is(ancestor(X), A), goal(is(X, B), ancestorDescendent(A, B)));
        rule(is(descendent(X), A), goal(is(X, B), ancestorDescendent(B, A)));

        rule(ancestorDescendent(A, C), goal(parentChild(A, C)));
        rule(ancestorDescendent(A, C), goal(ancestorDescendent(A, B), parentChild(B, C)));
    }

    @RepeatedTest(100)
    public void famTest0() {
        run(() -> {
            familyRules();

            fact(parentChild(Carel, Jan));
            fact(parentChild(Jan, Wim));
            fact(parentChild(Elske, Wim));
            fact(parentChild(Wim, Joppe));
            fact(parentChild(Heleen, Joppe));
            fact(parentChild(Wim, Marijn));
            fact(parentChild(Heleen, Marijn));

            isTrue(goal(is(parent(Joppe), Heleen)));
            isTrue(goal(is(child(Jan), Wim)));

            isFalse(goal(is(parent(Wim), Marijn)));
            isFalse(goal(is(parent(Wim), Heleen)));
            isFalse(goal(is(child(Wim), Wim)));

            isTrue(goal(is(ancestor(Marijn), Wim)));
            isTrue(goal(is(descendent(Carel), Marijn)));

            isFalse(goal(is(descendent(Marijn), Wim)));
            isFalse(goal(is(descendent(Heleen), Wim)));
            isFalse(goal(is(descendent(Joppe), Carel)));
            isFalse(goal(is(descendent(Carel), Carel)));
        });
    }

    @RepeatedTest(100)
    public void famTest1() {
        run(() -> {
            familyRules();

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

            hasBindings(goal(collect(parentChild(Wim, C), add(C, l(), PL))), binding(PL, l(Joppe, Marijn)));
        });
    }

    @RepeatedTest(100)
    public void famTest2() {
        run(() -> {
            familyRules();

            PersonAtom Carel = person("Carel");
            PersonAtom Jan = person("Jan");
            PersonAtom Wim = person("Wim");

            fact(parentChild(Carel, Jan));
            fact(parentChild(Jan, Wim));

            hasBindings(goal(ancestorDescendent(A, Wim)), binding(A, Jan), binding(A, Carel));
            hasBindings(goal(ancestorDescendent(Carel, C)), binding(C, Jan), binding(C, Wim));
        });
    }

    @RepeatedTest(100)
    public void famTest3() {
        run(() -> {
            rule(parentChild(B, C), goal(parentChild(B, C)));

            PersonAtom Jan = person("Jan");
            PersonAtom Wim = person("Wim");

            hasBindings(goal(parentChild(Wim, Jan)), incomplete(parentChild(Wim, Jan), parentChild(Wim, Jan)));
            isIncomplete(goal(parentChild(Wim, Jan)));
        });
    }

    @RepeatedTest(100)
    public void famTest4() {
        run(() -> {
            rootRules();

            isTrue(goal(is(child(person(0)), person(1))));
            isTrue(goal(is(child(person(3)), person(4))));
            isFalse(goal(is(child(person(4)), person(5))));

            isTrue(goal(is(root(person(0)), Root)));
            isTrue(goal(is(root(person(1)), Root)));
            isTrue(goal(is(root(person(4)), Root)));
            isTrue(goal(is(root(person(3)), Root)));
            isTrue(goal(is(root(person(2)), Root)));

            hasBindings(goal(is(root(C), Root)), binding(C, person(0)), binding(C, person(1)), //
                    binding(C, person(2)), binding(C, person(3)), binding(C, person(4)));
        });
    }

    @RepeatedTest(100)
    public void intTest() {
        run(() -> {
            arithmeticRules();

            hasBindings(goal(plus(i(7), i(3), P)), binding(P, i(10)));
            hasBindings(goal(plus(i(7), P, i(10))), binding(P, i(3)));
            hasBindings(goal(plus(P, i(3), i(10))), binding(P, i(7)));
        });
    }

    @RepeatedTest(100)
    public void isTest() {
        run(() -> {
            arithmeticRules();

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

            hasBindings(goal(collect(is(sqrt(i(49)), P), plus(P, i(0), Q))), binding(Q, i(0)));
        });
    }

}
