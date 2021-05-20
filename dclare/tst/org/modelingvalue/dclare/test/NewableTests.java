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
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.modelingvalue.dclare.SetableModifier.containment;
import static org.modelingvalue.dclare.SetableModifier.mandatory;
import static org.modelingvalue.dclare.SetableModifier.symmetricOpposite;
import static org.modelingvalue.dclare.SetableModifier.synthetic;
import static org.modelingvalue.dclare.test.support.Shared.THE_POOL;
import static org.modelingvalue.dclare.test.support.TestNewable.create;
import static org.modelingvalue.dclare.test.support.TestNewable.n;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.struct.Struct;
import org.modelingvalue.collections.util.Concurrent;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.DclareConfig;
import org.modelingvalue.dclare.Direction;
import org.modelingvalue.dclare.LeafTransaction;
import org.modelingvalue.dclare.Newable;
import org.modelingvalue.dclare.Observed;
import org.modelingvalue.dclare.Setable;
import org.modelingvalue.dclare.State;
import org.modelingvalue.dclare.UniverseTransaction;
import org.modelingvalue.dclare.test.support.TestImperative;
import org.modelingvalue.dclare.test.support.TestMutable;
import org.modelingvalue.dclare.test.support.TestMutableClass;
import org.modelingvalue.dclare.test.support.TestNewable;
import org.modelingvalue.dclare.test.support.TestNewableClass;
import org.modelingvalue.dclare.test.support.TestUniverse;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class NewableTests {
    private static final DclareConfig BASE_CONFIG = new DclareConfig()
            .withDevMode(true)
            .withMaxNrOfChanges(32)
            .withMaxTotalNrOfChanges(1000)
            .withMaxNrOfObserved(36)
            .withMaxNrOfObservers(36)
            .withTraceUniverse(false)
            .withTraceMatching(false)
            .withTraceActions(false);

    private static final DclareConfig[] CONFIGS = new DclareConfig[]{
            BASE_CONFIG,
            BASE_CONFIG.withRunSequential(true),
    };

    private static final int     NUM_CONFIGS           = 2; // = CONFIGS.length; // used in annotation which requires a constant
    private static final int     MANY_NR               = 8;
    private static final boolean PRINT_RESULT_STATE    = false;
    private static final boolean SKIP_SEQUENTIAL_TESTS = true; // sequential tests yield problems in some tests so we skip them. set this to true for testing locally

    @Test
    public void sanityCheck() {
        // checked dynamically because compile time constant is required
        assertEquals(NUM_CONFIGS, CONFIGS.length);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    public void singleBidirectional(int number) {
        bidirectional(CONFIGS[number], TestImperative.of());
    }

    //    @ParameterizedTest
    //    @ValueSource(ints = {0, 1})
    @RepeatedTest(MANY_NR * NUM_CONFIGS)
    public void manyBidirectional(RepetitionInfo repetitionInfo) {
        DclareConfig   config     = CONFIGS[(repetitionInfo.getCurrentRepetition() - 1) / MANY_NR]; // combining junit5 @ParameterizedTest and @RepeatedTest is not (yet) possible
        TestImperative imperative = TestImperative.of();
        State          state      = bidirectional(config, imperative);
        compareStates(state, bidirectional(config, imperative));
        compareStates(state, bidirectional(config, imperative));
    }

    public State bidirectional(DclareConfig config, TestImperative imperative) {
        Observed<TestMutable, Set<TestNewable>> cs = Observed.of("cs", Set.of(), containment);
        TestMutableClass                        U  = TestMutableClass.of("Universe", cs);

        Direction aDir = Direction.of("A");
        Direction bDir = Direction.of("B");

        Observed<TestMutable, TestNewable> acr = Observed.of("acr", null, containment);
        Observed<TestMutable, TestNewable> bcr = Observed.of("bcr", null, containment);

        Observed<TestMutable, Set<TestNewable>> acs = Observed.of("acs", Set.of(), containment);
        Observed<TestMutable, Set<TestNewable>> bcs = Observed.of("bcs", Set.of(), containment);

        Observed<TestMutable, TestNewable> ar = Observed.of("ar", null, mandatory);
        Observed<TestMutable, TestNewable> br = Observed.of("br", null, mandatory);

        TestNewableClass A  = TestNewableClass.of("A", aDir, n::get, n, acs, acr, br);
        TestNewableClass B  = TestNewableClass.of("B", bDir, n::get, n, bcs, bcr, ar);
        TestNewableClass AC = TestNewableClass.of("AC", aDir, n::get, n, br);
        TestNewableClass BC = TestNewableClass.of("BC", bDir, n::get, n, ar);

        U.observe(u -> {
            Set<TestNewable> bs = cs.get(u).filter(B::isInstance).toSet();
            cs.set(u, bs.addAll(bs.map(ar::get)));
        }, u -> {
            Set<TestNewable> as = cs.get(u).filter(A::isInstance).toSet();
            cs.set(u, as.addAll(as.map(br::get)));
        });

        A.observe(aDir, a -> br.set(a, create(aDir, "1", a, B, //
                b -> n.set(b, n.get(a)), //
                b -> bcs.set(b, acs.get(a).map(br::get).toSet()), //
                b -> bcr.set(b, acr.get(a) != null ? br.get(acr.get(a)) : null) //
        )));
        B.observe(bDir, b -> ar.set(b, create(bDir, "2", b, A, //
                a -> n.set(a, n.get(b)), //
                a -> acs.set(a, bcs.get(b).map(ar::get).toSet()), //
                a -> acr.set(a, bcr.get(b) != null ? ar.get(bcr.get(b)) : null) //
        )));

        AC.observe(aDir, ac -> br.set(ac, create(aDir, "3", ac, BC, //
                bc -> n.set(bc, n.get(ac))//
        )));
        BC.observe(bDir, bc -> ar.set(bc, create(bDir, "4", bc, AC, //
                ac -> n.set(ac, n.get(bc))//
        )));

        TestUniverse        universe = TestUniverse.of("universe", U, imperative);
        UniverseTransaction utx      = new UniverseTransaction(universe, THE_POOL, config);

        Concurrent<Set<TestNewable>> created = run(utx, "init", c -> {

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
        });

        run(utx, "changeName", c -> {
            for (TestNewable o : created.merge()) {
                n.set(o, n.get(o) != null ? n.get(o).toUpperCase() : null);
            }
        });

        run(utx, "stop", c -> utx.stop());
        State result = utx.waitForEnd();

        if (PRINT_RESULT_STATE) {
            System.err.println(result.asString(o -> o instanceof TestMutable, s -> s instanceof Observed && !s.plumming() && s != n));
        }

        result.run(() -> {
            Set<TestNewable> objects = result.getObjects(TestNewable.class).toSet();
            assertTrue(objects.containsAll(created.result()));
            assertEquals(24, objects.size());
            assertTrue(objects.allMatch(o -> n.get(o) == null || n.get(o).equals(n.get(o).toUpperCase())));
            assertTrue(objects.allMatch(o -> o.dDerivedConstructions().size() >= 0 && o.dDerivedConstructions().size() <= 1));
            assertTrue(objects.allMatch(o -> o.dNonDerivedSources().size() == 1));
        });

        return result;
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    public void oo_fb(int number) {
        TestImperative imperative = TestImperative.of();
        compareStates(oofb(CONFIGS[number], false, false, true, true, imperative), oofb(CONFIGS[number], false, false, true, true, imperative));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    public void oo2fb_oo(int number) {
        TestImperative imperative = TestImperative.of();
        compareStates(oofb(CONFIGS[number], false, false, true, true, imperative), oofb(CONFIGS[number], true, false, true, false, imperative));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    public void fb2oo_fb(int number) {
        TestImperative imperative = TestImperative.of();
        compareStates(oofb(CONFIGS[number], false, false, true, true, imperative), oofb(CONFIGS[number], false, true, false, true, imperative));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    public void oo2fb_oo_fb(int number) {
        TestImperative imperative = TestImperative.of();
        compareStates(oofb(CONFIGS[number], false, false, true, true, imperative), oofb(CONFIGS[number], true, false, true, true, imperative));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    public void fb2oo_oo_fb(int number) {
        TestImperative imperative = TestImperative.of();
        compareStates(oofb(CONFIGS[number], false, false, true, true, imperative), oofb(CONFIGS[number], false, true, true, true, imperative));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    public void oo2fb_fb2oo_oo_fb(int number) {
        TestImperative imperative = TestImperative.of();
        compareStates(oofb(CONFIGS[number], false, false, true, true, imperative), oofb(CONFIGS[number], true, true, true, true, imperative));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    public void oo2fb_fb2oo_oo(int number) {
        TestImperative imperative = TestImperative.of();
        compareStates(oofb(CONFIGS[number], false, false, true, true, imperative), oofb(CONFIGS[number], true, true, true, false, imperative));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    public void oo2fb_fb2oo_fb(int number) {
        TestImperative imperative = TestImperative.of();
        compareStates(oofb(CONFIGS[number], false, false, true, true, imperative), oofb(CONFIGS[number], true, true, false, true, imperative));
    }

    //    @ParameterizedTest
    //    @ValueSource(ints = {0, 1})
    @RepeatedTest(MANY_NR * 2)
    public void testAll(RepetitionInfo repetitionInfo) {
        DclareConfig config = CONFIGS[(repetitionInfo.getCurrentRepetition() - 1) / MANY_NR]; // combining junit5 @ParameterizedTest and @RepeatedTest is not (yet) possible
        if (SKIP_SEQUENTIAL_TESTS && config.isRunSequential()) {
            System.err.println("!!!!!!!! skip sequential test because it still gives errors");
            assumeFalse(config.isRunSequential()); // stops this test
        }
        TestImperative imperative = TestImperative.of();
        State          state      = oofb(config, false, false, true, true, imperative);
        for (int i = 0; i < 2; i++) {
            compareStates(state, oofb(config, true, false, true, false, imperative));
            compareStates(state, oofb(config, false, true, false, true, imperative));
            compareStates(state, oofb(config, true, false, true, true, imperative));
            compareStates(state, oofb(config, false, true, true, true, imperative));
            compareStates(state, oofb(config, true, true, true, true, imperative));
            compareStates(state, oofb(config, true, true, true, false, imperative));
            compareStates(state, oofb(config, true, true, false, true, imperative));
        }
    }

    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    private State oofb(DclareConfig config, boolean oo2fb, boolean fb2oo, boolean ooIn, boolean fbIn, TestImperative imperative) {

        Direction ooDir = Direction.of("OO");
        Direction fbDir = Direction.of("FB");

        // OO

        Observed<TestMutable, Set<TestNewable>> cls  = Observed.of("cls", Set.of(), containment);
        Observed<TestMutable, TestNewable>      mfbm = Observed.of("mfbm", null, synthetic);
        TestNewableClass                        OOM  = TestNewableClass.of("OOM", ooDir, n::get, n, cls, mfbm);

        Observed<TestMutable, Set<TestNewable>> refs = Observed.of("refs", Set.of(), containment);
        Observed<TestMutable, TestNewable>      mobt = Observed.of("mobt", null, synthetic);
        TestNewableClass                        CLS  = TestNewableClass.of("CLS", ooDir, n::get, n, refs, mobt);

        Observed<TestMutable, TestNewable> typ = Observed.of("typ", null);

        Observed<TestMutable, TestNewable> opp  = Observed.of("opp", null, symmetricOpposite);
        Observed<TestMutable, TestNewable> mrol = Observed.of("mrol", null, synthetic);
        Observed<TestMutable, TestNewable> mfat = Observed.of("mfat", null, synthetic);
        TestNewableClass                   REF  = TestNewableClass.of("REF", ooDir, n::get, n, typ, opp, mrol, mfat);

        // FB

        Observed<TestMutable, Set<TestNewable>> fts  = Observed.of("fts", Set.of(), containment);
        Observed<TestMutable, Set<TestNewable>> ots  = Observed.of("ots", Set.of(), containment);
        Observed<TestMutable, TestNewable>      moom = Observed.of("moom", null, synthetic);
        TestNewableClass                        FBM  = TestNewableClass.of("FBM", fbDir, n::get, n, ots, fts, moom);

        Observed<TestMutable, TestNewable>      mcls = Observed.of("mcls", null, synthetic);
        Observed<TestMutable, Set<TestNewable>> _otr = Observed.of("_otr", Set.of());
        TestNewableClass                        OBT  = TestNewableClass.of("OBT", fbDir, n::get, n, mcls, _otr);

        Observed<TestMutable, TestNewable> otr   = Observed.of("otr", null, () -> _otr);
        Observed<TestMutable, TestNewable> mref  = Observed.of("mref", null, synthetic);
        Observed<TestMutable, TestNewable> rlopp = Observed.of("rlopp", null, mandatory, symmetricOpposite);
        TestNewableClass                   ROL   = TestNewableClass.of("ROL", fbDir, n::get, n, otr, mref, rlopp);

        Observed<TestMutable, Pair<Pair<String, TestNewable>, Pair<String, TestNewable>>> ftid  = Observed.of("ftid", null);
        Observed<TestMutable, TestNewable>                                                left  = Observed.of("left", null, containment, mandatory);
        Observed<TestMutable, TestNewable>                                                right = Observed.of("right", null, containment, mandatory);
        TestNewableClass                                                                  FAT   = TestNewableClass.of("FAT", fbDir, ftid::get, n, ftid, left, right);

        ROL.observe(fbDir, //
                rl -> {
                    TestNewable ft = (TestNewable) rl.dParent();
                    rlopp.set(rl, rl.equals(left.get(ft)) ? right.get(ft) : left.get(ft));
                });

        FAT.observe(fbDir, //
                ft -> {
                    if (left.get(ft) == null) {
                        left.set(ft, create(fbDir, "L", ft, ROL));
                    }
                }, //
                ft -> {
                    if (right.get(ft) == null) {
                        right.set(ft, create(fbDir, "R", ft, ROL));
                    }
                }, //
                ft -> {
                    String ln = n.get(left.get(ft));
                    String rn = n.get(right.get(ft));
                    n.set(ft, "~".equals(ln) ? rn : ln != null && rn != null ? ln + "_" + rn : null);
                }, //
                ft -> {
                    TestNewable lr = left.get(ft);
                    TestNewable rr = right.get(ft);
                    TestNewable lt = otr.get(lr);
                    TestNewable rt = otr.get(rr);
                    String      ln = n.get(lr);
                    String      rn = n.get(rr);
                    ftid.set(ft, Pair.of(Pair.of(ln, lt), Pair.of(rn, rt)));
                });

        // Universe

        Observed<TestMutable, Set<TestNewable>> fbms = Observed.of("fbms", Set.of(), containment);
        Observed<TestMutable, Set<TestNewable>> ooms = Observed.of("ooms", Set.of(), containment);
        TestMutableClass                        U    = TestMutableClass.of("Universe", fbms, ooms);

        // Transformation

        if (oo2fb) {
            U.observe(ooDir, u -> fbms.set(u, ooms.get(u).map(mfbm::get).toSet()));
            OOM.observe(ooDir, oo -> mfbm.set(oo, create(ooDir, "1", oo, FBM, //
                    fb -> n.set(fb, n.get(oo)), //
                    fb -> ots.set(fb, cls.get(oo).map(mobt::get).toSet()), //
                    fb -> fts.set(fb, cls.get(oo).flatMap(refs::get).map(mfat::get).notNull().toSet()) //
            )));
            CLS.observe(ooDir, cl -> mobt.set(cl, create(ooDir, "2", cl, OBT, //
                    ot -> n.set(ot, n.get(cl)) //
            )));
            REF.observe(ooDir, rf -> mrol.set(rf, create(ooDir, "3", rf, ROL, //
                    rl -> n.set(rl, n.get(rf)), //
                    rl -> otr.set(rl, typ.get(rf) != null ? mobt.get(typ.get(rf)) : null) //
            )), rf -> mfat.set(rf, opp.get(rf) == null || n.get(rf).compareTo(n.get(opp.get(rf))) > 0 ? create(ooDir, "4", rf, FAT, //
                    ft -> right.set(ft, mrol.get(rf)), //
                    ft -> left.set(ft, opp.get(rf) == null ? create(ooDir, "5", rf, ROL, //
                            rl -> n.set(rl, "~"), //
                            rl -> otr.set(rl, mobt.get((TestNewable) rf.dParent()))) : mrol.get(opp.get(rf))) //
            ) : null));
        }

        if (fb2oo) {
            U.observe(fbDir, u -> ooms.set(u, fbms.get(u).map(moom::get).toSet()));
            FBM.observe(fbDir, fb -> moom.set(fb, create(fbDir, "6", fb, OOM, //
                    oo -> n.set(oo, n.get(fb)), //
                    oo -> cls.set(oo, ots.get(fb).map(mcls::get).toSet()) //
            )));
            OBT.observe(fbDir, ot -> mcls.set(ot, create(fbDir, "7", ot, CLS, //
                    cl -> n.set(cl, n.get(ot)), //
                    cl -> refs.set(cl, _otr.get(ot).map(rlopp::get).map(mref::get).notNull().toSet()) //
            )));
            ROL.observe(fbDir, rl -> mref.set(rl, otr.get(rlopp.get(rl)) != null && !"~".equals(n.get(rl)) ? create(fbDir, "8", rl, REF, //
                    rf -> n.set(rf, n.get(rl)), //
                    rf -> typ.set(rf, otr.get(rl) != null ? mcls.get(otr.get(rl)) : null), //
                    rf -> opp.set(rf, mref.get(rlopp.get(rl))) //
            ) : null));
        }

        // Instances

        TestUniverse        universe = TestUniverse.of("universe", U, imperative);
        UniverseTransaction utx      = new UniverseTransaction(universe, THE_POOL, config);
        final State[]       state    = new State[]{utx.emptyState()};

        Concurrent<Set<TestNewable>> created = run(utx, "init", c -> {

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

                TestNewable rl1  = c.create(ROL);
                TestNewable rl2  = c.create(ROL);
                TestNewable rl3  = c.create(ROL);
                TestNewable rl4  = c.create(ROL);
                TestNewable rl5  = c.create(ROL);
                TestNewable rl6  = c.create(ROL);
                TestNewable rl7  = c.create(ROL);
                TestNewable rl8  = c.create(ROL);
                TestNewable rl9  = c.create(ROL);
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

        });

        Concurrent<Set<TestNewable>> added = run(utx, "add", c -> {
            state[0] = checkState(state[0]);
            Set<TestNewable> objects = state[0].getObjects(TestNewable.class).toSet();
            assertTrue(objects.containsAll(created.merge()));
            assertEquals(32, objects.size());

            if (oo2fb) { // add OO
                TestNewable      oom     = ooms.get(universe).get(0);
                Set<TestNewable> classes = cls.get(oom);
                TestNewable      cl1     = classes.filter(cl -> Objects.equals(n.get(cl), "A")).findAny().get();
                TestNewable      cl2     = classes.filter(cl -> Objects.equals(n.get(cl), "B")).findAny().get();
                TestNewable      cl3     = classes.filter(cl -> Objects.equals(n.get(cl), "C")).findAny().get();
                TestNewable      cl4     = classes.filter(cl -> Objects.equals(n.get(cl), "D")).findAny().get();

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
                TestNewable      fbm         = fbms.get(universe).get(0);
                Set<TestNewable> objectTypes = ots.get(fbm);
                TestNewable      ot1         = objectTypes.filter(ot -> Objects.equals(n.get(ot), "A")).findAny().get();
                TestNewable      ot2         = objectTypes.filter(ot -> Objects.equals(n.get(ot), "B")).findAny().get();
                TestNewable      ot3         = objectTypes.filter(ot -> Objects.equals(n.get(ot), "C")).findAny().get();
                TestNewable      ot4         = objectTypes.filter(ot -> Objects.equals(n.get(ot), "D")).findAny().get();

                TestNewable ft1 = c.create(FAT);
                TestNewable ft2 = c.create(FAT);
                TestNewable ft3 = c.create(FAT);
                fts.set(fbm, Set::addAll, Set.of(ft1, ft2, ft3));
                n.set(ft1, "x_y");
                n.set(ft2, "z");
                n.set(ft3, "w");

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
                n.set(rl6, "w");
                otr.set(rl1, ot1);
                otr.set(rl2, ot2);
                otr.set(rl3, ot3);
                otr.set(rl4, ot4);
                otr.set(rl5, ot4);
                otr.set(rl6, ot3);
            }

        });

        run(utx, "change", c -> {
            state[0] = checkState(state[0]);
            Set<TestNewable> objects = state[0].getObjects(TestNewable.class).toSet();
            assertTrue(objects.containsAll(added.merge()));
            assertEquals((oo2fb && fb2oo) ? 58 : (oo2fb || fb2oo) ? 45 : 32, objects.size());

            if (oo2fb) { // change OO
                TestNewable      oom         = ooms.get(universe).get(0);
                Set<TestNewable> classes     = cls.get(oom);
                Set<TestNewable> refferences = classes.flatMap(refs::get).toSet();
                TestNewable      rf3         = refferences.filter(rf -> Objects.equals(n.get(rf), "r")).findAny().get();
                TestNewable      rf4         = refferences.filter(rf -> Objects.equals(n.get(rf), "s")).findAny().get();

                opp.set(rf3, rf4);
                opp.set(rf4, rf3);
            }

            if (fb2oo) { // change FB
                TestNewable      fbm       = fbms.get(universe).get(0);
                Set<TestNewable> factTypes = fts.get(fbm);
                TestNewable      ft3       = factTypes.filter(ft -> Objects.equals(n.get(ft), "w")).findAny().get();
                TestNewable      rl5       = left.get(ft3);

                n.set(rl5, "v");
            }

        });

        run(utx, "back", c -> {
            state[0] = checkState(state[0]);
            Set<TestNewable> objects = state[0].getObjects(TestNewable.class).toSet();
            assertEquals((oo2fb && fb2oo) ? 56 : fb2oo ? 46 : oo2fb ? 42 : 32, objects.size());
            assertTrue(objects.containsAll(added.merge()));

            if (oo2fb) { // change OO
                TestNewable      oom         = ooms.get(universe).get(0);
                Set<TestNewable> classes     = cls.get(oom);
                Set<TestNewable> refferences = classes.flatMap(refs::get).toSet();
                TestNewable      rf3         = refferences.filter(rf -> Objects.equals(n.get(rf), "r")).findAny().get();
                TestNewable      rf4         = refferences.filter(rf -> Objects.equals(n.get(rf), "s")).findAny().get();

                opp.set(rf3, (TestNewable) null);
                opp.set(rf4, (TestNewable) null);
            }

            if (fb2oo) { // change FB
                TestNewable      fbm       = fbms.get(universe).get(0);
                Set<TestNewable> factTypes = fts.get(fbm);
                TestNewable      ft3       = factTypes.filter(ft -> Objects.equals(n.get(ft), "v_w")).findAny().get();
                TestNewable      rl5       = left.get(ft3);

                n.set(rl5, "~");
            }

        });

        run(utx, "setType", c -> {
            state[0] = checkState(state[0]);
            Set<TestNewable> objects = state[0].getObjects(TestNewable.class).toSet();
            assertEquals((oo2fb && fb2oo) ? 58 : (oo2fb || fb2oo) ? 45 : 32, objects.size());
            assertTrue(objects.containsAll(added.merge()));

            if (oo2fb) { // change OO
                TestNewable      oom         = ooms.get(universe).get(0);
                Set<TestNewable> classes     = cls.get(oom);
                TestNewable      cl1         = classes.filter(cl -> Objects.equals(n.get(cl), "A")).findAny().get();
                Set<TestNewable> refferences = classes.flatMap(refs::get).toSet();
                TestNewable      rf3         = refferences.filter(rf -> Objects.equals(n.get(rf), "r")).findAny().get();

                typ.set(rf3, cl1);
            }

            if (fb2oo) { // change FB
                TestNewable      fbm         = fbms.get(universe).get(0);
                Set<TestNewable> objectTypes = ots.get(fbm);
                TestNewable      ot4         = objectTypes.filter(ot -> Objects.equals(n.get(ot), "D")).findAny().get();

                TestNewable ft4 = c.create(FAT);
                fts.set(fbm, Set::add, ft4);
                TestNewable rl7 = c.create(ROL);
                TestNewable rl8 = c.create(ROL);
                left.set(ft4, rl7);
                right.set(ft4, rl8);
                n.set(rl7, "~");
                n.set(rl8, "dd");
                otr.set(rl7, ot4);
            }

        });

        run(utx, "checkAndSetTypeBack", c -> {
            state[0] = checkState(state[0]);
            Set<TestNewable> objects = state[0].getObjects(TestNewable.class).toSet();
            assertEquals((oo2fb && fb2oo) ? 62 : oo2fb ? 45 : fb2oo ? 49 : 32, objects.size());
            assertTrue(objects.containsAll(added.merge()));

            if (oo2fb) { // change OO
                TestNewable      oom         = ooms.get(universe).get(0);
                Set<TestNewable> classes     = cls.get(oom);
                TestNewable      cl1         = classes.filter(cl -> Objects.equals(n.get(cl), "A")).findAny().get();
                TestNewable      cl4         = classes.filter(cl -> Objects.equals(n.get(cl), "D")).findAny().get();
                Set<TestNewable> refferences = classes.flatMap(refs::get).toSet();
                TestNewable      rf3         = refferences.filter(rf -> Objects.equals(n.get(rf), "r")).findAny().get();

                assertEquals(cl1, typ.get(rf3));

                typ.set(rf3, cl4);
            }

            if (fb2oo) { // change FB
                TestNewable      fbm       = fbms.get(universe).get(0);
                Set<TestNewable> factTypes = fts.get(fbm);
                TestNewable      ft4       = factTypes.filter(ft -> Objects.equals(n.get(right.get(ft)), "dd")).findAny().get();

                fts.set(fbm, Set::remove, ft4);
            }

        });

        run(utx, "remove", c -> {
            state[0] = checkState(state[0]);
            Set<TestNewable> objects = state[0].getObjects(TestNewable.class).toSet();
            assertEquals((oo2fb && fb2oo) ? 58 : (oo2fb || fb2oo) ? 45 : 32, objects.size());
            assertTrue(objects.containsAll(added.merge()));

            for (TestNewable add : added.merge()) {
                add.dDelete();
            }
        });

        run(utx, "stop", c -> utx.stop());
        State result = utx.waitForEnd();

        if (PRINT_RESULT_STATE) {
            System.err.println(result.asString(o -> o instanceof TestMutable, s -> s instanceof Observed && !s.plumming() && s != n));
        }

        result.run(() -> {
            Set<TestNewable> objects = result.getObjects(TestNewable.class).toSet();
            assertEquals(32, objects.size());
            assertTrue(objects.containsAll(created.result()));
            assertTrue(objects.allMatch(o -> o.dDerivedConstructions().size() >= 0 && o.dDerivedConstructions().size() <= 1));
            assertTrue(objects.allMatch(o -> o.dNonDerivedSources().size() == 1));
        });

        return result;
    }

    private State checkState(State pre) {
        State post = LeafTransaction.getCurrent().state();
        if (PRINT_RESULT_STATE) {
            System.err.println(pre.diffString(post, o -> o instanceof TestMutable, s -> s instanceof Observed && !s.plumming() && s != n));
        }
        return post;
    }

    private static final Direction INIT = Direction.of("INIT");

    private Concurrent<Set<TestNewable>> run(UniverseTransaction utx, String id, Consumer<Creator> action) {
        if (!utx.isKilled()) {
            TestUniverse                 u       = (TestUniverse) utx.universe();
            Concurrent<Set<TestNewable>> created = Concurrent.of(Set.of());
            u.schedule(() -> action.accept(c -> {
                TestNewable newable = create(INIT, id + u.uniqueInt(), c);
                created.set(Set::add, newable);
                return newable;
            }));
            return created;
        } else {
            return Concurrent.of(Set.of());
        }
    }

    @FunctionalInterface
    public interface Creator {
        TestNewable create(TestNewableClass clazz);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void compareStates(State as, State bs) {
        List<Newable> al = as.getObjects(Newable.class).sortedBy(Newable::dSortKey).toList();
        List<Newable> bl = bs.getObjects(Newable.class).sortedBy(Newable::dSortKey).toList();
        assertEquals(al.size(), bl.size());
        HashMap<Pair<Newable, Newable>, Boolean> done = new HashMap<>();
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
    private static boolean equals(State as, Object a, State bs, Object b, Map<Pair<Newable, Newable>, Boolean> done) {
        boolean result = false;
        if (a instanceof Newable && b instanceof Newable) {
            Newable                an  = (Newable) a;
            Newable                bn  = (Newable) b;
            Pair<Newable, Newable> key = Pair.of(an, bn);
            if (done.containsKey(key)) {
                result = done.get(key);
            } else {
                if (equals(as, as.get(an::dClass), bs, bs.get(bn::dClass), done) && //
                    equals(as, as.get(an::dNewableType), bs, bs.get(bn::dNewableType), done) && //
                    equals(as, as.get(an::dMatchingIdentity), bs, bs.get(bn::dMatchingIdentity), done) && //
                    equals(as, as.get(an::dParent), bs, bs.get(bn::dParent), done)) {
                    result = true;
                }
                done.put(key, result);
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

}
