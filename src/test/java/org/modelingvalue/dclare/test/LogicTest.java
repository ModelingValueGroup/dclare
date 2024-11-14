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

    static final Fun1<String, Set<String>> PARENTS   = fun1("parent", Set.of());

    static final Fun1<String, Set<String>> ANCESTORS = fun1("ancestor",                          //
            (p) -> sup(PARENTS.get(p).addAll(PARENTS.get(p).flatMap(LogicTest.ANCESTORS::get))));

    static final Rel2<String, String>      PARENT    = rel2("parent",                            //
            (a, b) -> sup(PARENTS.get(a).contains(b)));

    static final Rel2<String, String>      ANCESTOR1 = rel2("isAncestor",                        //
            (a, o) -> or(PARENT.sup(o, a),                                                       //
                    any(PARENTS.get(o).map(p -> LogicTest.ANCESTOR1.sup(a, p)))));

    static final Rel2<String, String>      ANCESTOR2 = rel2("isAncestor",                        // 
            (a, o) -> or(PARENT.sup(o, a),                                                       //
                    uni((String x) -> and(LogicTest.ANCESTOR2.sup(a, x), PARENT.sup(o, x)))));

    @RepeatedTest(32)
    public void test1() {
        run(() -> {
            PARENTS.set("Jan", Set.of("Carel"));
            PARENTS.set("Wim", Set.of("Jan", "Elske"));
            PARENTS.set("Joppe", Set.of("Wim", "Heleen"));
            PARENTS.set("Marijn", Set.of("Wim", "Heleen"));
            assertEquals(ANCESTORS.get("Joppe"), Set.of("Wim", "Heleen", "Jan", "Elske", "Carel"));

            assertTrue(ANCESTOR1.is("Carel", "Marijn"));
            assertTrue(ANCESTOR1.is("Wim", "Marijn"));

            assertTrue(ANCESTOR2.is("Carel", "Marijn"));
            assertTrue(ANCESTOR2.is("Wim", "Marijn"));
        });
    }
}
