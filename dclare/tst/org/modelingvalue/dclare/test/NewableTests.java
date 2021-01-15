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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.modelingvalue.dclare.SetableModifier.containment;
// import static org.modelingvalue.dclare.SetableModifier.doNotCheckConsistency;
import static org.modelingvalue.dclare.SetableModifier.synthetic;
import static org.modelingvalue.dclare.test.support.Shared.THE_POOL;
import static org.modelingvalue.dclare.test.support.TestNewable.create;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Concurrent;
import org.modelingvalue.dclare.Observed;
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
        // System.setProperty("TRACE_OBSERVERS", "true");
    }

    @Test
    public void simpleBidirectional() {
        Observed<TestMutable, Set<TestNewable>> cs = Observed.of("cs", Set.of(), containment);
        TestMutableClass U = TestMutableClass.of("Universe", cs);

        Observed<TestMutable, String> n = Observed.of("n", null);
        Observed<TestMutable, Set<TestNewable>> acs = Observed.of("acs", Set.of(), containment);
        Observed<TestMutable, Set<TestNewable>> bcs = Observed.of("bcs", Set.of(), containment);

        Observed<TestMutable, TestNewable> ar = Observed.of("a", null, synthetic);
        Observed<TestMutable, TestNewable> br = Observed.of("b", null, synthetic);

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

        A.observe(a -> br.set(a, create(a, B, //
                b -> n.set(b, n.get(a)), //
                b -> bcs.set(b, acs.get(a).map(br::get).toSet())//
        )));
        B.observe(b -> ar.set(b, create(b, A, //
                a -> n.set(a, n.get(b)), //
                a -> acs.set(a, bcs.get(b).map(ar::get).toSet())//
        )));

        AC.observe(ac -> br.set(ac, create(ac, BC, //
                bc -> n.set(bc, n.get(ac))//
        )));
        BC.observe(bc -> ar.set(bc, create(bc, AC, //
                ac -> n.set(ac, n.get(bc))//
        )));

        TestUniverse universe = TestUniverse.of("universe", U);
        UniverseTransaction utx = UniverseTransaction.of(universe, THE_POOL);

        Concurrent<Set<TestNewable>> created = run(utx, "init", c -> {

            TestNewable a1 = c.create(A);
            TestNewable b1 = c.create(B);
            TestNewable b2 = c.create(B);
            TestNewable a3 = c.create(A);
            cs.set(universe, Set.of(a1, a3, b1, b2));
            n.set(a1, "x");
            n.set(b1, "x");
            n.set(b2, "y");
            n.set(a3, "z");

            TestNewable ac1 = c.create(AC);
            TestNewable bc1 = c.create(BC);
            TestNewable ac2 = c.create(AC);
            TestNewable bc2 = c.create(BC);
            TestNewable bc3 = c.create(BC);
            TestNewable bc4 = c.create(BC);
            acs.set(a1, Set.of(ac1, ac2));
            bcs.set(b1, Set.of(bc1, bc2));
            bcs.set(b2, Set.of(bc3, bc4));
            n.set(ac1, "p");
            n.set(bc1, "p");
            n.set(ac2, "q");
            n.set(bc2, "q");
            n.set(bc3, "r");
            n.set(bc4, "s");

        });

        run(utx, "changeName", c -> {
            for (TestNewable o : created.merge()) {
                n.set(o, n.get(o).toUpperCase());
            }
        });

        utx.stop();
        State result = utx.waitForEnd();

        System.err.println(result.asString(o -> o instanceof TestMutable, s -> s instanceof Observed && !s.synthetic() && s != n));

        result.run(() -> {
            Set<TestNewable> objects = result.getObjects(TestNewable.class).toSet();
            assertEquals(14, objects.size());
            assertTrue(objects.containsAll(created.result()));
            assertTrue(objects.allMatch(o -> n.get(o) != null && n.get(o).equals(n.get(o).toUpperCase())));
            assertTrue(objects.allMatch(o -> o.dConstructions().size() > 0 && o.dConstructions().size() <= 2));
        });

    }

    @Test
    public void oo2fb_oo() {
        oofb(true, false, true, false);
    }

    @Test
    public void fb2oo_fb() {
        oofb(false, true, false, true);
    }

    @Test
    public void oo2fb_oo_fb() {
        oofb(true, false, true, true);
    }

    @Test
    public void fb2oo_oo_fb() {
        oofb(false, true, true, true);
    }

    @Test
    public void oo2fb_fb2oo_oo_fb() {
        oofb(true, true, true, true);
    }

    @Test
    public void oo2fb_fb2oo_oo() {
        oofb(true, true, true, false);
    }

    @Test
    public void oo2fb_fb2oo_fb() {
        oofb(true, true, false, true);
    }

    private State oofb(boolean oo2fb, boolean fb2oo, boolean ooIn, boolean fbIn) {

        Observed<TestMutable, String> n = Observed.of("n", null);

        // OO

        Observed<TestMutable, Set<TestNewable>> cls = Observed.of("cls", Set.of(), containment);
        Observed<TestMutable, TestNewable> mfbm = Observed.of("mfbm", null, synthetic);
        TestNewableClass OOM = TestNewableClass.of("OOM", n::get, n, cls, mfbm);

        Observed<TestMutable, Set<TestNewable>> refs = Observed.of("refs", Set.of(), containment);
        Observed<TestMutable, TestNewable> mobt = Observed.of("mobt", null, synthetic);
        TestNewableClass CLS = TestNewableClass.of("CLS", n::get, n, refs, mobt);

        Observed<TestMutable, TestNewable> typ = Observed.of("typ", null);
        Observed<TestMutable, TestNewable> opp = Observed.of("opp", null);
        Observed<TestMutable, TestNewable> mrol = Observed.of("mrol", null, synthetic);
        Observed<TestMutable, TestNewable> mfat = Observed.of("mfat", null, synthetic);
        TestNewableClass REF = TestNewableClass.of("REF", n::get, n, typ, opp, mrol, mfat);

        // FB

        Observed<TestMutable, Set<TestNewable>> fts = Observed.of("fts", Set.of(), containment);
        Observed<TestMutable, Set<TestNewable>> ots = Observed.of("ots", Set.of(), containment);
        Observed<TestMutable, TestNewable> moom = Observed.of("moom", null, synthetic);
        TestNewableClass FBM = TestNewableClass.of("FBM", n::get, n, ots, fts, moom);

        Observed<TestMutable, TestNewable> mcls = Observed.of("mcls", null, synthetic);
        Observed<TestMutable, Set<TestNewable>> _otr = Observed.of("_otr", Set.of(), synthetic);
        TestNewableClass OBT = TestNewableClass.of("OBT", n::get, n, mcls, _otr);

        Observed<TestMutable, TestNewable> left = Observed.of("left", null, containment);
        Observed<TestMutable, TestNewable> right = Observed.of("right", null, containment);
        TestNewableClass FAT = TestNewableClass.of("FAT", n::get, n, left, right);

        Observed<TestMutable, TestNewable> otr = Observed.of("otr", null, () -> _otr);
        Observed<TestMutable, TestNewable> mref = Observed.of("mref", null, synthetic);
        Observed<TestMutable, TestNewable> rlopp = Observed.of("rlopp", null);
        TestNewableClass ROL = TestNewableClass.of("ROL", n::get, n, otr, mref, rlopp);

        ROL.observe(rl -> {
            TestNewable ft = (TestNewable) rl.dParent();
            rlopp.set(rl, rl.equals(left.get(ft)) ? right.get(ft) : left.get(ft));
        });

        // Universe

        Observed<TestMutable, Set<TestNewable>> fbms = Observed.of("fbms", Set.of(), containment);
        Observed<TestMutable, Set<TestNewable>> ooms = Observed.of("ooms", Set.of(), containment);
        TestMutableClass U = TestMutableClass.of("Universe", fbms, ooms);

        // Transformation

        if (oo2fb) {
            U.observe(u -> fbms.set(u, ooms.get(u).map(mfbm::get).toSet()));
            OOM.observe(oo -> mfbm.set(oo, create(oo, FBM, //
                    fb -> n.set(fb, n.get(oo)), //
                    fb -> ots.set(fb, cls.get(oo).map(mobt::get).toSet()), //
                    fb -> fts.set(fb, cls.get(oo).flatMap(refs::get).map(mfat::get).notNull().toSet()) //
            )));
            CLS.observe(cl -> mobt.set(cl, create(cl, OBT, //
                    ot -> n.set(ot, n.get(cl)) //
            )));
            REF.observe(rf -> mrol.set(rf, create(rf, "1", ROL, //
                    rl -> n.set(rl, n.get(rf)), //
                    rl -> otr.set(rl, mobt.get(typ.get(rf))) // !!!!!!!!!!!!!!!!!!!!!!!
            )), rf -> mfat.set(rf, opp.get(rf) == null || n.get(opp.get(rf)).compareTo(n.get(rf)) > 0 ? create(rf, "2", FAT, //
                    ft -> n.set(ft, n.get(rf) + (opp.get(rf) == null ? "" : "_" + n.get(opp.get(rf)))), //
                    ft -> left.set(ft, mrol.get(rf)), //
                    ft -> right.set(ft, opp.get(rf) == null ? create(rf, ROL, rl -> n.set(rl, "~")) : mrol.get(opp.get(rf))) //
            ) : null));
        }

        if (fb2oo) {
            U.observe(u -> ooms.set(u, fbms.get(u).map(moom::get).toSet()));
            FBM.observe(fb -> moom.set(fb, create(fb, OOM, //
                    oo -> n.set(oo, n.get(fb)), //
                    oo -> cls.set(oo, ots.get(fb).map(mcls::get).toSet()) //
            )));
            OBT.observe(ot -> mcls.set(ot, create(ot, CLS, //
                    cl -> n.set(cl, n.get(ot)), //
                    cl -> refs.set(cl, _otr.get(ot).map(rlopp::get).map(mref::get).notNull().toSet()) //
            )));
            ROL.observe(rl -> mref.set(rl, !n.get(rl).equals("~") ? create(rl, REF, //
                    rf -> n.set(rf, n.get(rl)), //
                    rf -> typ.set(rf, mcls.get(otr.get(rl))), // !!!!!!!!!!!!!!!!!!!!!!!
                    rf -> opp.set(rf, mref.get(rlopp.get(rl)))//
            ) : null));
        }

        // Instances

        TestUniverse universe = TestUniverse.of("universe", U);
        UniverseTransaction utx = UniverseTransaction.of(universe, THE_POOL);

        Concurrent<Set<TestNewable>> created = run(utx, "init", c -> {

            if (ooIn) { // OO
                TestNewable oom = c.create(OOM);
                ooms.set(universe, Set.of(oom));
                n.set(oom, "model");

                TestNewable cl1 = c.create(CLS);
                TestNewable cl2 = c.create(CLS);
                cls.set(oom, Set.of(cl1, cl2));
                n.set(cl1, "A");
                n.set(cl2, "B");

                TestNewable rf1 = c.create(REF);
                TestNewable rf2 = c.create(REF);
                refs.set(cl1, Set.of(rf1));
                refs.set(cl2, Set.of(rf2));
                n.set(rf1, "b");
                n.set(rf2, "a");
                opp.set(rf1, rf2);
                opp.set(rf2, rf1);
                typ.set(rf1, cl2);
                typ.set(rf2, cl1);
            }

            if (fbIn) { // FB
                TestNewable fbm = c.create(FBM);
                fbms.set(universe, Set.of(fbm));
                n.set(fbm, "model");

                TestNewable ot1 = c.create(OBT);
                TestNewable ot2 = c.create(OBT);
                ots.set(fbm, Set.of(ot1, ot2));
                n.set(ot1, "A");
                n.set(ot2, "B");

                TestNewable ft1 = c.create(FAT);
                fts.set(fbm, Set.of(ft1));
                n.set(ft1, "a_b");

                TestNewable rl1 = c.create(ROL);
                TestNewable rl2 = c.create(ROL);
                left.set(ft1, rl1);
                right.set(ft1, rl2);
                n.set(rl1, "a");
                n.set(rl2, "b");
                otr.set(rl1, ot1);
                otr.set(rl2, ot2);
            }

        });

        utx.stop();
        State result = utx.waitForEnd();

        System.err.println(result.asString(o -> o instanceof TestMutable, s -> s instanceof Observed && !s.synthetic() && s != n));

        result.run(() -> {
            Set<TestNewable> objects = result.getObjects(TestNewable.class).toSet();
            assertEquals(11, objects.size());
            assertTrue(objects.containsAll(created.result()));
            assertTrue(objects.allMatch(o -> n.get(o) != null));
            assertTrue(objects.allMatch(o -> o.dConstructions().size() > 0 && o.dConstructions().size() <= 2));
        });

        return result;
    }

    private Concurrent<Set<TestNewable>> run(UniverseTransaction utx, String id, Consumer<Creator> init) {
        Concurrent<Set<TestNewable>> created = Concurrent.of(Set.of());
        utx.put(id, () -> {
            AtomicInteger conter = new AtomicInteger(0);
            init.accept(c -> {
                TestNewable newable = create(conter.getAndIncrement(), c);
                created.set(Set::add, newable);
                return newable;
            });
        });
        return created;
    }

    @FunctionalInterface
    public interface Creator {
        TestNewable create(TestNewableClass clazz);
    }

}
