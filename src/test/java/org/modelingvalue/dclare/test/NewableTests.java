//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2022 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import static org.junit.jupiter.api.Assertions.*;
import static org.modelingvalue.dclare.CoreSetableModifier.*;
import static org.modelingvalue.dclare.test.support.Shared.THE_POOL;
import static org.modelingvalue.dclare.test.support.TestNewable.create;
import static org.modelingvalue.dclare.test.support.TestNewable.n;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.struct.Struct;
import org.modelingvalue.collections.util.Concurrent;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.collections.util.StatusProvider.StatusIterator;
import org.modelingvalue.dclare.*;
import org.modelingvalue.dclare.UniverseTransaction.Status;
import org.modelingvalue.dclare.test.support.TestMutable;
import org.modelingvalue.dclare.test.support.TestMutableClass;
import org.modelingvalue.dclare.test.support.TestNewable;
import org.modelingvalue.dclare.test.support.TestNewableClass;
import org.modelingvalue.dclare.test.support.TestUniverse;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class NewableTests {
    //    static {
    //        System.setProperty("TRACE_STATUS", "true");
    //    }

    private static final DclareConfig   BASE_CONFIG        = new DclareConfig().withDevMode(true).withCheckOrphanState(true).     //
            withMaxNrOfChanges(16).withMaxTotalNrOfChanges(1000).withMaxNrOfObserved(36).withMaxNrOfObservers(36).                //
            withTraceUniverse(true).withTraceMutable(false).withTraceMatching(true).withTraceActions(true);

    private static final DclareConfig[] CONFIGS            = new DclareConfig[]{BASE_CONFIG, BASE_CONFIG.withRunSequential(true)};

    private static final int            NUM_CONFIGS        = 2;                                                                   // = CONFIGS.length; // used in annotation which requires a hardconstant
    private static final int            MANY_NR            = 8;
    private static final boolean        PRINT_RESULT_STATE = false;                                                               // sequential tests yield problems in some tests so we skip them. set this to true for testing locally
    private static final boolean        FULL               = false;

    @Test
    public void sanityCheck() {
        // checked dynamically because compile time constant is required
        assertEquals(NUM_CONFIGS, CONFIGS.length);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    public void singleBidirectional(int number) {
        bidirectional(CONFIGS[number]);
    }

    //    @ParameterizedTest
    //    @ValueSource(ints = {0, 1})
    @RepeatedTest(MANY_NR * NUM_CONFIGS)
    public void manyBidirectional(RepetitionInfo repetitionInfo) {
        DclareConfig config = CONFIGS[(repetitionInfo.getCurrentRepetition() - 1) / MANY_NR]; // combining junit5 @ParameterizedTest and @RepeatedTest is not (yet) possible
        State state = bidirectional(config);
        compareStates(state, bidirectional(config));
        compareStates(state, bidirectional(config));
    }

    public State bidirectional(DclareConfig config) {
        Observed<TestMutable, Set<TestNewable>> cs = Observed.of("cs", Set.of(), containment);
        TestMutableClass U = TestMutableClass.of("Universe", cs);

        Observed<TestMutable, TestNewable> acr = Observed.of("acr", null, containment);
        Observed<TestMutable, TestNewable> bcr = Observed.of("bcr", null, containment);

        Observed<TestMutable, Set<TestNewable>> acs = Observed.of("acs", Set.of(), containment);
        Observed<TestMutable, Set<TestNewable>> bcs = Observed.of("bcs", Set.of(), containment);

        Observed<TestMutable, TestNewable> ar = Observed.of("ar", null, mandatory);
        Observed<TestMutable, TestNewable> br = Observed.of("br", null, mandatory);

        TestNewableClass A = TestNewableClass.of("A", n::get, n, acs, acr, br);
        TestNewableClass B = TestNewableClass.of("B", n::get, n, bcs, bcr, ar);
        TestNewableClass AC = TestNewableClass.of("AC", n::get, n, br);
        TestNewableClass BC = TestNewableClass.of("BC", n::get, n, ar);

        Direction a2b = Direction.of("A2B");
        Direction b2a = Direction.of("B2A");

        U.observe(b2a, cs, u -> {
            Set<TestNewable> bs = cs.get(u).filter(B::isInstance).toSet();
            return bs.addAll(bs.map(ar::get));
        }).observe(a2b, cs, u -> {
            Set<TestNewable> as = cs.get(u).filter(A::isInstance).toSet();
            return as.addAll(as.map(br::get));
        });

        A.observe(a2b, br, a -> create(B, x -> x.//
                observe(a2b, n, b -> n.get(a)).//
                observe(a2b, bcs, b -> acs.get(a).map(br::get).toSet()).//
                observe(a2b, bcr, b -> acr.get(a) != null ? br.get(acr.get(a)) : null))//
        );
        B.observe(b2a, ar, b -> create(A, x -> x.//
                observe(b2a, n, a -> n.get(b)). //
                observe(b2a, acs, a -> bcs.get(b).map(ar::get).toSet()). //
                observe(b2a, acr, a -> bcr.get(b) != null ? ar.get(bcr.get(b)) : null))//
        );
        AC.observe(a2b, br, ac -> create(BC, x -> x.//
                observe(a2b, n, bc -> n.get(ac)))//
        );
        BC.observe(b2a, ar, bc -> create(AC, x -> x.//
                observe(b2a, n, ac -> n.get(bc)))//
        );

        TestUniverse universe = TestUniverse.of("universe", U);
        UniverseTransaction utx = new UniverseTransaction(universe, THE_POOL, config);

        Concurrent<Set<TestNewable>> created = run(utx, "init", c -> {

            if (FULL) {
                TestNewable a0 = c.create(A);
                TestNewable b0 = c.create(B);
                TestNewable a1 = c.create(A);
                TestNewable b1 = c.create(B);
                TestNewable a2 = c.create(A);
                TestNewable b2 = c.create(B);
                TestNewable a3 = c.create(A);
                TestNewable b3 = c.create(B);
                TestNewable a4 = c.create(A);
                TestNewable b4 = c.create(B);
                TestNewable a5 = c.create(A);
                TestNewable b5 = c.create(B);
                cs.set(universe, Set.of(a0, b0, a1, b1, a2, b2, a3, b3, a4, b4, a5, b5));
                n.set(a0, "x");
                n.set(b0, "x");
                n.set(a1, "y");
                n.set(b1, "z");
                n.set(a4, "w");
                n.set(b4, "w");
                n.set(a5, "w");
                n.set(b5, "w");

                TestNewable ac1 = c.create(AC);
                TestNewable bc1 = c.create(BC);
                TestNewable ac2 = c.create(AC);
                TestNewable bc2 = c.create(BC);
                TestNewable bc3 = c.create(BC);
                TestNewable bc4 = c.create(BC);
                TestNewable ac5 = c.create(AC);
                TestNewable bc5 = c.create(BC);
                acs.set(a0, Set.of(ac1, ac2));
                bcs.set(b0, Set.of(bc1, bc2));
                bcs.set(b1, Set.of(bc3, bc4));
                acr.set(a0, ac5);
                bcr.set(b0, bc5);
                n.set(ac1, "p");
                n.set(bc1, "p");
                n.set(ac2, "q");
                n.set(bc2, "q");
                n.set(bc3, "r");
                n.set(bc4, "s");
                n.set(ac5, "t");
                n.set(bc5, "t");
            } else {
                TestNewable a0 = c.create(A);
                TestNewable b0 = c.create(B);
                TestNewable a1 = c.create(A);
                TestNewable b1 = c.create(B);
                cs.set(universe, Set.of(a0, b0, a1, b1));
                n.set(a0, "x");
                n.set(b0, "x");
                n.set(a1, "y");
                n.set(b1, "z");

                TestNewable ac1 = c.create(AC);
                TestNewable bc1 = c.create(BC);
                TestNewable ac2 = c.create(AC);
                TestNewable bc2 = c.create(BC);
                acs.set(a0, Set.of(ac1, ac2));
                bcs.set(b0, Set.of(bc1, bc2));
                n.set(ac1, "p");
                n.set(bc1, "p");
                n.set(ac2, "q");
                n.set(bc2, "q");
            }

        });

        run(utx, "changeName", c -> {
            for (TestNewable o : created.merge()) {
                n.set(o, n.get(o) != null ? n.get(o).toUpperCase() : null);
            }
        });

        run(utx, "stop", c -> utx.stop());
        State result = utx.waitForEnd();

        if (PRINT_RESULT_STATE) {
            System.err.println(result.asString(o -> o instanceof TestMutable, s -> s instanceof Observed && s.isTraced() && s != n));
        }

        result.run(() -> {
            Set<TestNewable> objects = result.getObjects(TestNewable.class).toSet();
            Set<TestNewable> news = created.result();
            Set<TestNewable> lost = news.removeAll(objects);
            assertEquals(Set.of(), lost);
            assertEquals(FULL ? 24 : 10, objects.size());
            assertTrue(objects.allMatch(o -> n.get(o) == null || n.get(o).equals(n.get(o).toUpperCase())));
            assertTrue(objects.allMatch(o -> o.dDerivedConstructions().size() >= 0 && o.dDerivedConstructions().size() <= 1));
        });

        return result;
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    public void oo_fb(int number) {
        compareStates(oofb(CONFIGS[number], false, false, true, true, "oo_fb-" + number + "-L"), oofb(CONFIGS[number], false, false, true, true, "oo_fb-" + number + "-R"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    public void oo2fb_oo(int number) {
        compareStates(oofb(CONFIGS[number], false, false, true, true, "oo2fb_oo-" + number + "-L"), oofb(CONFIGS[number], true, false, true, false, "oo2fb_oo-" + number + "-R"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    public void fb2oo_fb(int number) {
        compareStates(oofb(CONFIGS[number], false, false, true, true, "fb2oo_fb-" + number + "-L"), oofb(CONFIGS[number], false, true, false, true, "fb2oo_fb-" + number + "-R"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    public void oo2fb_oo_fb(int number) {
        compareStates(oofb(CONFIGS[number], false, false, true, true, "oo2fb_oo_fb-" + number + "-L"), oofb(CONFIGS[number], true, false, true, true, "oo2fb_oo_fb-" + number + "-R"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    public void fb2oo_oo_fb(int number) {
        compareStates(oofb(CONFIGS[number], false, false, true, true, "fb2oo_oo_fb-" + number + "-L"), oofb(CONFIGS[number], false, true, true, true, "fb2oo_oo_fb-" + number + "-R"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    public void oo2fb_fb2oo_oo_fb(int number) {
        compareStates(oofb(CONFIGS[number], false, false, true, true, "oo2fb_fb2oo_oo_fb-" + number + "-L"), oofb(CONFIGS[number], true, true, true, true, "oo2fb_fb2oo_oo_fb-" + number + "-R"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    public void oo2fb_fb2oo_oo(int number) {
        compareStates(oofb(CONFIGS[number], false, false, true, true, "oo2fb_fb2oo_oo-" + number + "-L"), oofb(CONFIGS[number], true, true, true, false, "oo2fb_fb2oo_oo-" + number + "-R"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    public void oo2fb_fb2oo_fb(int number) {
        compareStates(oofb(CONFIGS[number], false, false, true, true, "oo2fb_fb2oo_fb-" + number + "-L"), oofb(CONFIGS[number], true, true, false, true, "oo2fb_fb2oo_fb-" + number + "-R"));
    }

    //    @ParameterizedTest
    //    @ValueSource(ints = {0, 1})
    @RepeatedTest(MANY_NR * 2)
    public void testAll(RepetitionInfo repetitionInfo) {
        DclareConfig config = CONFIGS[(repetitionInfo.getCurrentRepetition() - 1) / MANY_NR]; // combining junit5 @ParameterizedTest and @RepeatedTest is not (yet) possible
        State state = oofb(config, false, false, true, true, repetitionInfo.getCurrentRepetition() + "-pre");
        for (int i = 0; i < 2; i++) {
            compareStates(state, oofb(config, false, true, false, true, repetitionInfo.getCurrentRepetition() + "-" + i));
            compareStates(state, oofb(config, false, true, true, true, repetitionInfo.getCurrentRepetition() + "-" + i));
            compareStates(state, oofb(config, true, false, true, false, repetitionInfo.getCurrentRepetition() + "-" + i));
            compareStates(state, oofb(config, true, false, true, true, repetitionInfo.getCurrentRepetition() + "-" + i));
            compareStates(state, oofb(config, true, true, false, true, repetitionInfo.getCurrentRepetition() + "-" + i));
            compareStates(state, oofb(config, true, true, true, false, repetitionInfo.getCurrentRepetition() + "-" + i));
            compareStates(state, oofb(config, true, true, true, true, repetitionInfo.getCurrentRepetition() + "-" + i));
        }
    }

    @Test
    public void testNoTransformation() {
        for (int i = 0; i < MANY_NR; i++) {
            DclareConfig config = CONFIGS[i % 2];
            oofb(config, false, false, true, true, "testNoTransformation-" + i);
        }
    }

    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    private State oofb(DclareConfig config, boolean oo2fb, boolean fb2oo, boolean ooIn, boolean fbIn, String debug_info) {
        sanityCheck();

        // OO

        Observed<TestMutable, Set<TestNewable>> cls = Observed.of("cls", Set.of(), containment);
        Observed<TestMutable, TestNewable> mfbm = Observed.of("mfbm", null, synthetic);
        TestNewableClass OOM = TestNewableClass.of("OOM", n::get, n, cls, mfbm);

        Observed<TestMutable, Set<TestNewable>> refs = Observed.of("refs", Set.of(), containment);
        Observed<TestMutable, TestNewable> mobt = Observed.of("mobt", null, synthetic);
        TestNewableClass CLS = TestNewableClass.of("CLS", n::get, n, refs, mobt);

        Observed<TestMutable, TestNewable> typ = Observed.of("typ", null);

        Observed<TestMutable, TestNewable> opp = Observed.of("opp", null, symmetricOpposite);
        Observed<TestMutable, TestNewable> mrol = Observed.of("mrol", null, synthetic);
        Observed<TestMutable, TestNewable> mfat = Observed.of("mfat", null, synthetic);
        TestNewableClass REF = TestNewableClass.of("REF", n::get, n, typ, opp, mrol, mfat);

        // FB

        Observed<TestMutable, Set<TestNewable>> fts = Observed.of("fts", Set.of(), containment);
        Observed<TestMutable, Set<TestNewable>> ots = Observed.of("ots", Set.of(), containment);
        Observed<TestMutable, TestNewable> moom = Observed.of("moom", null, synthetic);
        TestNewableClass FBM = TestNewableClass.of("FBM", n::get, n, ots, fts, moom);

        Observed<TestMutable, TestNewable> mcls = Observed.of("mcls", null, synthetic);
        Observed<TestMutable, Set<TestNewable>> _otr = Observed.of("_otr", Set.of());
        TestNewableClass OBT = TestNewableClass.of("OBT", n::get, n, mcls, _otr);

        Observed<TestMutable, TestNewable> otr = Observed.of("otr", null, () -> _otr);
        Observed<TestMutable, TestNewable> mref = Observed.of("mref", null, synthetic);
        Observed<TestMutable, TestNewable> rlopp = Observed.of("rlopp", null, mandatory, symmetricOpposite);
        TestNewableClass ROL = TestNewableClass.of("ROL", n::get, n, otr, mref, rlopp);

        Observed<TestMutable, TestNewable> left = Observed.of("left", null, containment, mandatory);
        Observed<TestMutable, TestNewable> right = Observed.of("right", null, containment, mandatory);
        TestNewableClass FAT = TestNewableClass.of("FAT", n::get, n, left, right);

        ROL.observe(rlopp, rl -> {
            TestNewable ft = (TestNewable) rl.dParent();
            return rl.equals(left.get(ft)) ? right.get(ft) : left.get(ft);
        });

        //        FAT.observe(left, ft -> {
        //            TestNewable l = left.get(ft);
        //            return l != null ? l : create(ROL, "L", ft);
        //        }).observe(right, ft -> {
        //            TestNewable r = right.get(ft);
        //            return r != null ? r : create(ROL, "R", ft);
        //        });

        FAT.observe(n, ft -> {
            String ln = n.get(left.get(ft));
            ln = "~".equals(ln) ? null : ln;
            String rn = n.get(right.get(ft));
            rn = "~".equals(rn) ? null : rn;
            return ln != null && rn != null ? ln + "_" + rn : ln != null ? ln : rn;
        });

        // Universe

        Observed<TestMutable, Set<TestNewable>> fbms = Observed.of("fbms", Set.of(), containment);
        Observed<TestMutable, Set<TestNewable>> ooms = Observed.of("ooms", Set.of(), containment);
        TestMutableClass U = TestMutableClass.of("Universe", fbms, ooms);

        // Transformation

        Direction oo2fbDir = Direction.of("OO2FB");
        Direction fb2ooDir = Direction.of("FB2OO");

        if (oo2fb) {
            U.observe(oo2fbDir, fbms, u -> ooms.get(u).map(mfbm::get).toSet());
            OOM.observe(oo2fbDir, mfbm, oo -> create(FBM, x -> x.//
                    observe(n, fb -> n.get(oo)). //
                    observe(ots, fb -> cls.get(oo).map(mobt::get).toSet()). //
                    observe(fts, fb -> cls.get(oo).flatMap(refs::get).map(mfat::get).notNull().toSet())) //
            );
            CLS.observe(oo2fbDir, mobt, cl -> create(OBT, x -> x.//
                    observe(n, ot -> n.get(cl))) //
            );
            REF.observe(oo2fbDir, mrol, rf -> create(ROL, x -> x.//
                    observe(n, rl -> n.get(rf)). //
                    observe(otr, rl -> typ.get(rf) != null ? mobt.get(typ.get(rf)) : null)) //
            );
            REF.observe(oo2fbDir, mfat, rf -> opp.get(rf) == null || compare(n.get(rf), n.get(opp.get(rf))) > 0 ? //
                    create(FAT, x -> x.//
                            observe(right, ft -> mrol.get(rf)). //
                            observe(left, ft -> opp.get(rf) == null ? //
                                    create(ROL, y -> y.//
                                            observe(n, rl -> "~"). //
                                            observe(otr, rl -> mobt.get((TestNewable) rf.dParent()))//
                                    ) : mrol.get(opp.get(rf))) //
                    ) : null);
        }

        if (fb2oo) {
            U.observe(fb2ooDir, ooms, u -> fbms.get(u).map(moom::get).toSet());
            FBM.observe(fb2ooDir, moom, fb -> create(OOM, x -> x.//
                    observe(n, oo -> n.get(fb)). //
                    observe(cls, oo -> ots.get(fb).map(mcls::get).toSet()) //
            ));
            OBT.observe(fb2ooDir, mcls, ot -> create(CLS, x -> x.//
                    observe(n, cl -> n.get(ot)). //
                    observe(refs, cl -> _otr.get(ot).map(rlopp::get).notNull().map(mref::get).notNull().toSet())//
            ));
            ROL.observe(fb2ooDir, mref, rl -> otr.get(rlopp.get(rl)) != null && !"~".equals(n.get(rl)) ? //
                    create(REF, x -> x.//
                            observe(n, rf -> !"~".equals(n.get(rl)) ? n.get(rl) : n.get(rf)). //
                            observe(typ, rf -> otr.get(rl) != null ? mcls.get(otr.get(rl)) : null). //
                            observe(opp, rf -> mref.get(rlopp.get(rl)))//
                    ) : null);
        }

        // Instances

        TestUniverse universe = TestUniverse.of("universe", U);
        UniverseTransaction utx = new UniverseTransaction(universe, THE_POOL, config);
        final State[] state = new State[]{utx.emptyState()};

        Concurrent<Set<TestNewable>> created = run(utx, "init", c -> {
            state[0] = checkState(state[0]);

            if (FULL) {
                if (ooIn) { // OO
                    TestNewable oom = c.create(OOM);
                    ooms.set(universe, Set.of(oom));
                    n.set(oom, "model");

                    TestNewable cl1 = c.create(CLS);
                    TestNewable cl2 = c.create(CLS);
                    TestNewable cl3 = c.create(CLS);
                    TestNewable cl4 = c.create(CLS);
                    cls.set(oom, Set.of(cl1, cl2, cl3, cl4));
                    n.set(cl1, "A");
                    n.set(cl2, "B");
                    n.set(cl3, "C");
                    n.set(cl4, "D");

                    TestNewable rf1 = c.create(REF);
                    TestNewable rf2 = c.create(REF);
                    TestNewable rf3 = c.create(REF);
                    TestNewable rf4 = c.create(REF);
                    TestNewable rf5 = c.create(REF);
                    TestNewable rf6 = c.create(REF);
                    TestNewable rf7 = c.create(REF);
                    refs.set(cl1, Set.of(rf1, rf5));
                    refs.set(cl2, Set.of(rf2));
                    refs.set(cl3, Set.of(rf3));
                    refs.set(cl4, Set.of(rf4, rf6, rf7));
                    n.set(rf1, "b");
                    n.set(rf2, "a");
                    n.set(rf3, "d");
                    n.set(rf4, "c");
                    n.set(rf5, "e");
                    n.set(rf6, "f");
                    opp.set(rf1, rf2);
                    opp.set(rf2, rf1);
                    opp.set(rf3, rf4);
                    opp.set(rf4, rf3);
                    typ.set(rf1, cl2);
                    typ.set(rf2, cl1);
                    typ.set(rf3, cl4);
                    typ.set(rf4, cl3);
                    typ.set(rf5, cl3);
                }

                if (fbIn) { // FB
                    TestNewable fbm = c.create(FBM);
                    fbms.set(universe, Set.of(fbm));
                    n.set(fbm, "model");

                    TestNewable ot1 = c.create(OBT);
                    TestNewable ot2 = c.create(OBT);
                    TestNewable ot3 = c.create(OBT);
                    TestNewable ot4 = c.create(OBT);
                    ots.set(fbm, Set.of(ot1, ot2, ot3, ot4));
                    n.set(ot1, "A");
                    n.set(ot2, "B");
                    n.set(ot3, "C");
                    n.set(ot4, "D");

                    TestNewable ft1 = c.create(FAT);
                    TestNewable ft2 = c.create(FAT);
                    TestNewable ft3 = c.create(FAT);
                    TestNewable ft4 = c.create(FAT);
                    TestNewable ft5 = c.create(FAT);
                    fts.set(fbm, Set.of(ft1, ft2, ft3, ft4, ft5));
                    n.set(ft1, "a_b");
                    n.set(ft2, "c_d");
                    n.set(ft3, "e");
                    n.set(ft4, "f");

                    TestNewable rl1 = c.create(ROL);
                    TestNewable rl2 = c.create(ROL);
                    TestNewable rl3 = c.create(ROL);
                    TestNewable rl4 = c.create(ROL);
                    TestNewable rl5 = c.create(ROL);
                    TestNewable rl6 = c.create(ROL);
                    TestNewable rl7 = c.create(ROL);
                    TestNewable rl8 = c.create(ROL);
                    TestNewable rl9 = c.create(ROL);
                    TestNewable rl10 = c.create(ROL);
                    left.set(ft1, rl1);
                    right.set(ft1, rl2);
                    left.set(ft2, rl3);
                    right.set(ft2, rl4);
                    left.set(ft3, rl5);
                    right.set(ft3, rl6);
                    left.set(ft4, rl7);
                    right.set(ft4, rl8);
                    left.set(ft5, rl9);
                    right.set(ft5, rl10);
                    n.set(rl1, "a");
                    n.set(rl2, "b");
                    n.set(rl3, "c");
                    n.set(rl4, "d");
                    n.set(rl5, "~");
                    n.set(rl6, "e");
                    n.set(rl7, "~");
                    n.set(rl8, "f");
                    n.set(rl9, "~");
                    otr.set(rl1, ot1);
                    otr.set(rl2, ot2);
                    otr.set(rl3, ot3);
                    otr.set(rl4, ot4);
                    otr.set(rl5, ot1);
                    otr.set(rl6, ot3);
                    otr.set(rl7, ot4);
                    otr.set(rl9, ot4);
                }
            } else {
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
            }

        });

        Concurrent<Set<TestNewable>> added = run(utx, "add", c -> {
            state[0] = checkState(state[0]);
            Set<TestNewable> objects = state[0].getObjects(TestNewable.class).toSet();
            Set<TestNewable> news = created.merge();
            Set<TestNewable> lost = news.removeAll(objects);
            assertEquals(Set.of(), lost);

            if (ooIn && fbIn) {
                Set<TestNewable> derived = objects.removeAll(news);
                assertEquals(Set.of(), derived);
            }

            assertEquals(FULL ? 32 : 11, objects.size());

            if (FULL) {
                if (oo2fb) { // add OO
                    TestNewable oom = ooms.get(universe).get(0);
                    Set<TestNewable> classes = cls.get(oom);
                    TestNewable cl1 = classes.filter(cl -> Objects.equals(n.get(cl), "A")).findAny().get();
                    TestNewable cl2 = classes.filter(cl -> Objects.equals(n.get(cl), "B")).findAny().get();
                    TestNewable cl3 = classes.filter(cl -> Objects.equals(n.get(cl), "C")).findAny().get();
                    TestNewable cl4 = classes.filter(cl -> Objects.equals(n.get(cl), "D")).findAny().get();

                    TestNewable rf1 = c.create(REF);
                    TestNewable rf2 = c.create(REF);
                    TestNewable rf3 = c.create(REF);
                    TestNewable rf4 = c.create(REF);
                    refs.set(cl1, Set::add, rf1);
                    refs.set(cl2, Set::add, rf2);
                    refs.set(cl3, Set::add, rf3);
                    refs.set(cl4, Set::add, rf4);
                    n.set(rf1, "p");
                    n.set(rf2, "q");
                    n.set(rf3, "r");
                    n.set(rf4, "s");
                    opp.set(rf1, rf2);
                    opp.set(rf2, rf1);
                    typ.set(rf1, cl2);
                    typ.set(rf2, cl1);
                    typ.set(rf3, cl4);
                    typ.set(rf4, cl3);
                }

                if (fb2oo) { // add FB
                    TestNewable fbm = fbms.get(universe).get(0);
                    Set<TestNewable> objectTypes = ots.get(fbm);
                    TestNewable ot1 = objectTypes.filter(ot -> Objects.equals(n.get(ot), "A")).findAny().get();
                    TestNewable ot2 = objectTypes.filter(ot -> Objects.equals(n.get(ot), "B")).findAny().get();
                    TestNewable ot3 = objectTypes.filter(ot -> Objects.equals(n.get(ot), "C")).findAny().get();
                    TestNewable ot4 = objectTypes.filter(ot -> Objects.equals(n.get(ot), "D")).findAny().get();

                    TestNewable ft1 = c.create(FAT);
                    TestNewable ft2 = c.create(FAT);
                    TestNewable ft3 = c.create(FAT);
                    fts.set(fbm, Set::addAll, Set.of(ft1, ft2, ft3));
                    n.set(ft1, "x_y");
                    n.set(ft2, "z");
                    n.set(ft3, "v");

                    TestNewable rl1 = c.create(ROL);
                    TestNewable rl2 = c.create(ROL);
                    TestNewable rl3 = c.create(ROL);
                    TestNewable rl4 = c.create(ROL);
                    TestNewable rl5 = c.create(ROL);
                    TestNewable rl6 = c.create(ROL);
                    left.set(ft1, rl1);
                    right.set(ft1, rl2);
                    left.set(ft2, rl3);
                    right.set(ft2, rl4);
                    left.set(ft3, rl5);
                    right.set(ft3, rl6);
                    n.set(rl1, "x");
                    n.set(rl2, "y");
                    n.set(rl3, "~");
                    n.set(rl4, "z");
                    n.set(rl5, "~");
                    n.set(rl6, "v");
                    otr.set(rl1, ot1);
                    otr.set(rl2, ot2);
                    otr.set(rl3, ot3);
                    otr.set(rl4, ot4);
                    otr.set(rl5, ot4);
                    otr.set(rl6, ot3);
                }
            } else {
                if (oo2fb) { // add OO
                    TestNewable oom = ooms.get(universe).get(0);
                    Set<TestNewable> classes = cls.get(oom);
                    TestNewable cl3 = classes.filter(cl -> Objects.equals(n.get(cl), "A")).findAny().get();
                    TestNewable cl4 = classes.filter(cl -> Objects.equals(n.get(cl), "B")).findAny().get();

                    TestNewable rf3 = c.create(REF);
                    TestNewable rf4 = c.create(REF);
                    refs.set(cl3, Set::add, rf3);
                    refs.set(cl4, Set::add, rf4);
                    n.set(rf3, "r");
                    n.set(rf4, "s");
                    typ.set(rf3, cl4);
                    typ.set(rf4, cl3);
                }

                if (fb2oo) { // add FB
                    TestNewable fbm = fbms.get(universe).get(0);
                    Set<TestNewable> objectTypes = ots.get(fbm);

                    TestNewable ot3 = objectTypes.filter(ot -> Objects.equals(n.get(ot), "A")).findAny().get();
                    TestNewable ot4 = objectTypes.filter(ot -> Objects.equals(n.get(ot), "B")).findAny().get();

                    TestNewable ft3 = c.create(FAT);
                    fts.set(fbm, Set::addAll, Set.of(ft3));
                    n.set(ft3, "v");

                    TestNewable rl5 = c.create(ROL);
                    TestNewable rl6 = c.create(ROL);
                    left.set(ft3, rl5);
                    right.set(ft3, rl6);
                    n.set(rl5, "~");
                    n.set(rl6, "v");
                    otr.set(rl5, ot4);
                    otr.set(rl6, ot3);
                }
            }

        });

        run(utx, "changeA", c -> {
            state[0] = checkState(state[0]);
            Set<TestNewable> objects = state[0].getObjects(TestNewable.class).toSet();
            if (oo2fb && fb2oo) {
                Set<TestNewable> noReasons = objects.filter(o -> o.dDerivedConstructions().isEmpty()).toSet();
                assertEquals(Set.of(), noReasons, "No Reasons ");
            }
            Set<TestNewable> lost = added.merge().removeAll(objects);
            assertEquals(Set.of(), lost, "Lost ");
            assertEquals((oo2fb && fb2oo) ? (FULL ? 58 : 23) : oo2fb ? (FULL ? 45 : 19) : fb2oo ? (FULL ? 45 : 15) : (FULL ? 32 : 11), objects.size());

            if (oo2fb) { // change OO
                TestNewable oom = ooms.get(universe).get(0);
                Set<TestNewable> classes = cls.get(oom);
                Set<TestNewable> refferences = classes.flatMap(refs::get).toSet();
                TestNewable rf3 = refferences.filter(rf -> Objects.equals(n.get(rf), "r")).findAny().get();
                TestNewable rf4 = refferences.filter(rf -> Objects.equals(n.get(rf), "s")).findAny().get();

                opp.set(rf3, rf4);
                opp.set(rf4, rf3);
            }

            if (fb2oo) { // change FB
                TestNewable fbm = fbms.get(universe).get(0);
                Set<TestNewable> factTypes = fts.get(fbm);
                TestNewable ft3 = factTypes.filter(ft -> Objects.equals(n.get(ft), "v")).findAny().get();
                TestNewable rl5 = left.get(ft3);

                n.set(rl5, "u");
            }

        });

        //        run(utx, "changeB", c -> {
        //            state[0] = checkState(state[0]);
        //            Set<TestNewable> objects = state[0].getObjects(TestNewable.class).toSet();
        //            assertEquals((oo2fb && fb2oo) ? (full ? 56 : 21) : fb2oo ? (full ? 46 : 16) : oo2fb ? (full ? 42 : 16) : (full ? 32 : 11), objects.size());
        //            Set<TestNewable> lost = added.merge().removeAll(objects);
        //            assertEquals(Set.of(), lost);
        //
        //            if (fb2oo) { // change FB
        //                TestNewable fbm = fbms.get(universe).get(0);
        //                Set<TestNewable> factTypes = fts.get(fbm);
        //                TestNewable ft3 = factTypes.filter(ft -> Objects.equals(n.get(ft), "u_v")).findAny().get();
        //                TestNewable rl5 = left.get(ft3);
        //
        //                n.set(rl5, "w");
        //            }
        //
        //        });
        //
        //        run(utx, "changeBackB", c -> {
        //            state[0] = checkState(state[0]);
        //            Set<TestNewable> objects = state[0].getObjects(TestNewable.class).toSet();
        //            assertEquals((oo2fb && fb2oo) ? (full ? 56 : 21) : fb2oo ? (full ? 46 : 16) : oo2fb ? (full ? 42 : 16) : (full ? 32 : 11), objects.size());
        //            Set<TestNewable> lost = added.merge().removeAll(objects);
        //            assertEquals(Set.of(), lost);
        //
        //            if (fb2oo) { // change FB
        //                TestNewable fbm = fbms.get(universe).get(0);
        //                Set<TestNewable> factTypes = fts.get(fbm);
        //                TestNewable ft3 = factTypes.filter(ft -> Objects.equals(n.get(ft), oo2fb ? "v_w" : "w_v")).findAny().get();
        //                TestNewable rl5 = (oo2fb ? right : left).get(ft3);
        //
        //                n.set(rl5, "u");
        //            }
        //
        //        });

        //        run(utx, "changeC", c -> {
        //            state[0] = checkState(state[0]);
        //            Set<TestNewable> objects = state[0].getObjects(TestNewable.class).toSet();
        //            assertEquals((oo2fb && fb2oo) ? (full ? 56 : 21) : fb2oo ? (full ? 46 : 16) : oo2fb ? (full ? 42 : 16) : (full ? 32 : 11), objects.size());
        //            Set<TestNewable> lost = added.merge().removeAll(objects);
        //            assertEquals(Set.of(), lost);
        //
        //            if (fb2oo) { // change FB
        //                TestNewable fbm = fbms.get(universe).get(0);
        //                Set<TestNewable> factTypes = fts.get(fbm);
        //                TestNewable ft3 = factTypes.filter(ft -> Objects.equals(n.get(ft), "u_v")).findAny().get();
        //                TestNewable rl5 = right.get(ft3);
        //
        //                n.set(rl5, "~");
        //            }
        //
        //        });
        //
        //        run(utx, "changeBackC", c -> {
        //            state[0] = checkState(state[0]);
        //            Set<TestNewable> objects = state[0].getObjects(TestNewable.class).toSet();
        //            assertEquals((oo2fb && fb2oo) ? (full ? 56 : 21) : fb2oo ? (full ? 46 : 16) : oo2fb ? (full ? 42 : 16) : (full ? 32 : 11), objects.size());
        //            Set<TestNewable> lost = added.merge().removeAll(objects);
        //            assertEquals(Set.of(), lost);
        //
        //            if (fb2oo) { // change FB
        //                TestNewable fbm = fbms.get(universe).get(0);
        //                Set<TestNewable> factTypes = fts.get(fbm);
        //                TestNewable ft3 = factTypes.filter(ft -> Objects.equals(n.get(ft), "u")).findAny().get();
        //                TestNewable rl5 = (oo2fb ? left : right).get(ft3);
        //
        //                n.set(rl5, "v");
        //            }
        //
        //        });

        run(utx, "changeBackA", c -> {
            state[0] = checkState(state[0]);
            Set<TestNewable> objects = state[0].getObjects(TestNewable.class).toSet();
            assertEquals((oo2fb && fb2oo) ? (FULL ? 56 : 21) : fb2oo ? (FULL ? 46 : 16) : oo2fb ? (FULL ? 42 : 16) : (FULL ? 32 : 11), objects.size());
            Set<TestNewable> lost = added.merge().removeAll(objects);
            assertEquals(Set.of(), lost);

            if (oo2fb) { // change OO
                TestNewable oom = ooms.get(universe).get(0);
                Set<TestNewable> classes = cls.get(oom);
                Set<TestNewable> refferences = classes.flatMap(refs::get).toSet();
                TestNewable rf3 = refferences.filter(rf -> Objects.equals(n.get(rf), "r")).findAny().get();
                TestNewable rf4 = refferences.filter(rf -> Objects.equals(n.get(rf), "s")).findAny().get();

                opp.set(rf3, (TestNewable) null);
                opp.set(rf4, (TestNewable) null);
            }

            if (fb2oo) { // change FB
                TestNewable fbm = fbms.get(universe).get(0);
                Set<TestNewable> factTypes = fts.get(fbm);
                TestNewable ft3 = factTypes.filter(ft -> Objects.equals(n.get(ft), "u_v")).findAny().get();
                TestNewable rl5 = left.get(ft3);

                n.set(rl5, "~");
            }

        });

        run(utx, "setType", c -> {
            state[0] = checkState(state[0]);
            Set<TestNewable> objects = state[0].getObjects(TestNewable.class).filter(n -> n instanceof Universe || n.dParent() != null).toSet();
            assertEquals((oo2fb && fb2oo) ? (FULL ? 58 : 23) : oo2fb ? (FULL ? 45 : 19) : fb2oo ? (FULL ? 45 : 15) : (FULL ? 32 : 11), objects.size());
            Set<TestNewable> lost = added.merge().removeAll(objects);
            assertEquals(Set.of(), lost);

            if (oo2fb) { // change OO
                TestNewable oom = ooms.get(universe).get(0);
                Set<TestNewable> classes = cls.get(oom);
                TestNewable cl2 = classes.filter(cl -> Objects.equals(n.get(cl), "B")).findAny().get();
                Set<TestNewable> refferences = classes.flatMap(refs::get).toSet();
                TestNewable rf3 = refferences.filter(rf -> Objects.equals(n.get(rf), "r")).findAny().get();

                typ.set(rf3, cl2);
            }

            if (fb2oo) { // change FB
                TestNewable fbm = fbms.get(universe).get(0);
                Set<TestNewable> objectTypes = ots.get(fbm);
                TestNewable ot1 = objectTypes.filter(ot -> Objects.equals(n.get(ot), "A")).findAny().get();

                TestNewable ft4 = c.create(FAT);
                fts.set(fbm, Set::add, ft4);
                TestNewable rl7 = c.create(ROL);
                TestNewable rl8 = c.create(ROL);
                left.set(ft4, rl7);
                right.set(ft4, rl8);
                n.set(rl7, "~");
                n.set(rl8, "dd");
                otr.set(rl7, ot1);
            }

        });

        run(utx, "setTypeBack", c -> {
            state[0] = checkState(state[0]);
            Set<TestNewable> objects = state[0].getObjects(TestNewable.class).toSet();
            assertEquals((oo2fb && fb2oo) ? (FULL ? 62 : 27) : oo2fb ? (FULL ? 45 : 19) : fb2oo ? (FULL ? 49 : 19) : (FULL ? 32 : 11), objects.size());
            Set<TestNewable> lost = added.merge().removeAll(objects);
            assertEquals(Set.of(), lost);

            if (oo2fb) { // change OO
                TestNewable oom = ooms.get(universe).get(0);
                Set<TestNewable> classes = cls.get(oom);
                TestNewable cl1 = classes.filter(cl -> Objects.equals(n.get(cl), "A")).findAny().get();
                TestNewable cl2 = classes.filter(cl -> Objects.equals(n.get(cl), "B")).findAny().get();
                Set<TestNewable> refferences = classes.flatMap(refs::get).toSet();
                TestNewable rf3 = refferences.filter(rf -> Objects.equals(n.get(rf), "r")).findAny().get();

                assertEquals(cl2, typ.get(rf3));

                typ.set(rf3, cl1);
            }

            if (fb2oo) { // change FB
                TestNewable fbm = fbms.get(universe).get(0);
                Set<TestNewable> factTypes = fts.get(fbm);
                TestNewable ft4 = factTypes.filter(ft -> Objects.equals(n.get(right.get(ft)), "dd")).findAny().get();

                fts.set(fbm, Set::remove, ft4);
            }

        });

        run(utx, "remove", c -> {
            state[0] = checkState(state[0]);
            Set<TestNewable> objects = state[0].getObjects(TestNewable.class).toSet();
            assertEquals((oo2fb && fb2oo) ? (FULL ? 58 : 23) : oo2fb ? (FULL ? 45 : 19) : fb2oo ? (FULL ? 45 : 15) : (FULL ? 32 : 11), objects.size());
            Set<TestNewable> lost = added.merge().removeAll(objects);
            assertEquals(Set.of(), lost);

            for (TestNewable add : added.result()) {
                add.dDelete();
            }
        });

        run(utx, "stop", c -> utx.stop());
        State result = utx.waitForEnd();

        if (PRINT_RESULT_STATE) {
            System.err.println(result.asString(o -> o instanceof TestMutable, s -> s instanceof Observed && s.isTraced() && s != n));
        }

        result.run(() -> {
            Set<TestNewable> objects = result.getObjects(TestNewable.class).toSet();
            assertEquals(FULL ? 32 : 11, objects.size());
            Set<TestNewable> lost = created.result().removeAll(objects);
            assertEquals(Set.of(), lost);
            assertTrue(objects.allMatch(o -> o.dDerivedConstructions().size() >= 0 && o.dDerivedConstructions().size() <= 1));
            for (TestNewable o : objects) {
                if (REF.isInstance(o) && opp.get(o) != null) {
                    assertNotNull(n.get(o));
                    assertNotNull(n.get(opp.get(o)));
                }
            }
        });

        return result;
    }

    private int compare(String a, String b) {
        a = a != null ? a : "";
        b = b != null ? b : "";
        return a.compareTo(b);
    }

    private State checkState(State pre) {
        State post = LeafTransaction.getCurrent().state();
        if (PRINT_RESULT_STATE) {
            System.err.println(pre.diffString(post, o -> o instanceof TestMutable, s -> s instanceof Observed && s.isTraced() && s != n));
        }
        return post;
    }

    private Concurrent<Set<TestNewable>> run(UniverseTransaction utx, String id, Consumer<Creator> action) {
        StatusIterator<Status> it = utx.getStatusIterator();
        Status status = it.waitForStoppedOr(Status::isIdle);
        if (!status.isStopped()) {
            if (utx.getConfig().isTraceUniverse()) {
                System.err.println("-------------------------- " + id + " -------------------------------------------");
            }
            Concurrent<Set<TestNewable>> created = Concurrent.of(Set.of());
            TestUniverse u = (TestUniverse) utx.universe();
            Creator creator = new Creator() {
                @Override
                public TestNewable create(TestNewableClass clazz) {
                    TestNewable newable = TestNewable.create(clazz, id + u.uniqueInt());
                    created.set(Set::add, newable);
                    return newable;
                }

                @Override
                public String toString() {
                    return id + "Creator";
                }
            };
            u.schedule(() -> action.accept(creator));
            it.waitForStoppedOr(s -> !s.active.isEmpty());
            return created;
        } else {
            return Concurrent.of(Set.of());
        }
    }

    public interface Creator {
        TestNewable create(TestNewableClass clazz);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void compareStates(State as, State bs) {
        List<Newable> al = as.getObjects(Newable.class).sortedBy(Newable::dSortKey).toList();
        List<Newable> bl = bs.getObjects(Newable.class).sortedBy(Newable::dSortKey).toList();
        assertEquals(al.size(), bl.size());
        AtomicReference<Map<Pair<Newable, Newable>, Boolean>> done = new AtomicReference<>(Map.of());
        for (Newable an : al) {
            Optional<Newable> bo = bl.filter(bn -> equals(as, an, bs, bn, done)).findFirst();
            assertTrue(bo.isPresent());
            Newable bn = bo.get();
            bl = bl.remove(bn);
            for (Setable s : an.dClass().dSetables()) {
                if (!s.synthetic()) {
                    Object av = as.get(() -> s.get(an));
                    Object bv = bs.get(() -> s.get(bn));
                    assertTrue(equals(as, av, bs, bv, done), s + " = " + as.get(() -> "" + av) + " <> " + bs.get(() -> "" + bv));
                }
            }
        }
        assertTrue(bl.isEmpty());
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantSuppression"})
    private static boolean equals(State as, Object a, State bs, Object b, AtomicReference<Map<Pair<Newable, Newable>, Boolean>> done) {
        boolean result = false;
        if (a instanceof Newable && b instanceof Newable) {
            Newable an = (Newable) a;
            Newable bn = (Newable) b;
            Pair<Newable, Newable> key = Pair.of(an, bn);
            if (done.get().containsKey(key)) {
                result = done.get().get(key);
            } else {
                if (equals(as, as.get(an::dClass), bs, bs.get(bn::dClass), done) && //
                        equals(as, as.get(an::dNewableType), bs, bs.get(bn::dNewableType), done) && //
                        equals(as, as.get(an::dMatchingIdentity), bs, bs.get(bn::dMatchingIdentity), done) && //
                        equals(as, as.get(an::dParent), bs, bs.get(bn::dParent), done)) {
                    result = true;
                }
                done.set(done.get().put(key, result));
            }
        } else if (a instanceof Struct && b instanceof Struct) {
            Struct structa = (Struct) a;
            Struct structb = (Struct) b;
            if (structa.length() == structb.length()) {
                result = true;
                for (int i = 0; i < structa.length(); i++) {
                    result &= equals(as, structa.get(i), bs, structb.get(i), done);
                }
            }
        } else if (a instanceof Collection && b instanceof Collection) {
            if (((Collection) a).size() == ((Collection) b).size()) {
                List al = ((Collection) a).toList();
                List bl = ((Collection) b).toList();
                for (int ai = 0; ai < al.size(); ai++) {
                    for (int bi = 0; bi < bl.size(); bi++) {
                        if (equals(as, al.get(ai), bs, bl.get(bi), done)) {
                            bl = bl.removeIndex(bi);
                            break;
                        }
                    }
                }
                result = bl.isEmpty();
            }
        } else if (Objects.equals(a, b)) {
            result = true;
        }
        return result;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + Integer.toString(System.identityHashCode(this), Character.MAX_RADIX);
    }

}
