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

package org.modelingvalue.dclare;

import java.math.BigInteger;

import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.SerializableBiFunction;
import org.modelingvalue.collections.util.SerializableFunction;
import org.modelingvalue.collections.util.SerializableTriFunction;

public class Arithmetic extends Logic {

    // Is

    private static Functor<Pred> is = functor((SerializableBiFunction<Int, IntAtom, Pred>) Arithmetic::is);

    public static Pred is(Int i, IntAtom r) {
        return term(is, i, r);
    }

    // Integer

    public static interface Int extends Term {
    }

    public static interface IntAtom extends Int {
    }

    public static interface IntFunc extends Int {
    }

    private static Functor<IntAtom> i = functor((SerializableFunction<BigInteger, IntAtom>) Arithmetic::i);

    private static IntAtom i(BigInteger x) {
        return term(i, x);
    }

    public static IntAtom i(long x) {
        return i(BigInteger.valueOf(x));
    }

    public static IntAtom ilv(String name) {
        return var(IntAtom.class, name);
    }

    public static IntFunc ifv(String name) {
        return var(IntFunc.class, name);
    }

    public static Int iv(String name) {
        return var(Int.class, name);
    }

    // Operators

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Functor<Pred> compare = functor((SerializableTriFunction<IntAtom, IntAtom, IntAtom, Pred>) Arithmetic::compare, t -> {
        TermImpl<IntAtom> at = t.getTerm(1);
        TermImpl<IntAtom> bt = t.getTerm(2);
        TermImpl<IntAtom> ct = t.getTerm(3);
        BigInteger ai = at != null ? at.getVal(1) : null;
        BigInteger bi = bt != null ? bt.getVal(1) : null;
        BigInteger ci = ct != null ? ct.getVal(1) : null;
        if (ai != null && bi != null) {
            BigInteger r = BigInteger.valueOf(ai.compareTo(bi));
            if (ci != null) {
                return ci.equals(r) ? Set.of(t) : Set.of();
            } else {
                return Set.of(t.set(3, at.set(1, r)));
            }
        } else if (BigInteger.ZERO.equals(ci)) {
            if (ai != null) {
                return Set.of(t.set(2, at));
            } else if (bi != null) {
                return Set.of(t.set(1, bt));
            }
        }
        return t.incomplete();
    });

    public static Pred compare(IntAtom a, IntAtom b, IntAtom c) {
        return term(compare, a, b, c);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Functor<Pred> plusPred = functor((SerializableTriFunction<IntAtom, IntAtom, IntAtom, Pred>) Arithmetic::plus, t -> {
        TermImpl<IntAtom> at = t.getTerm(1);
        TermImpl<IntAtom> bt = t.getTerm(2);
        TermImpl<IntAtom> ct = t.getTerm(3);
        BigInteger ai = at != null ? at.getVal(1) : null;
        BigInteger bi = bt != null ? bt.getVal(1) : null;
        BigInteger ci = ct != null ? ct.getVal(1) : null;
        if (ai != null && bi != null && ci != null) {
            return ai.add(bi).equals(ci) ? Set.of(t) : Set.of();
        } else if (ai != null && bi != null && ci == null) {
            return Set.of(t.set(3, at.set(1, ai.add(bi))));
        } else if (ai != null && bi == null && ci != null) {
            return Set.of(t.set(2, at.set(1, ci.subtract(ai))));
        } else if (ai == null && bi != null && ci != null) {
            return Set.of(t.set(1, bt.set(1, ci.subtract(bi))));
        } else {
            return t.incomplete();
        }
    });

    public static Pred plus(IntAtom a, IntAtom b, IntAtom r) {
        return term(plusPred, a, b, r);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Functor<Pred> multiplyPred = functor((SerializableTriFunction<IntAtom, IntAtom, IntAtom, Pred>) Arithmetic::multiply, t -> {
        TermImpl<IntAtom> at = t.getTerm(1);
        TermImpl<IntAtom> bt = t.getTerm(2);
        TermImpl<IntAtom> ct = t.getTerm(3);
        BigInteger ai = at != null ? at.getVal(1) : null;
        BigInteger bi = bt != null ? bt.getVal(1) : null;
        BigInteger ci = ct != null ? ct.getVal(1) : null;
        if (ai != null && bi != null && ci != null) {
            return ai.multiply(bi).equals(ci) ? Set.of(t) : Set.of();
        } else if (ai != null && bi != null && ci == null) {
            return Set.of(t.set(3, at.set(1, ai.multiply(bi))));
        } else if (ai != null && bi == null && ci != null) {
            return Set.of(t.set(2, at.set(1, ci.divide(ai))));
        } else if (ai == null && bi != null && ci != null) {
            return Set.of(t.set(1, bt.set(1, ci.divide(bi))));
        } else {
            return t.incomplete();
        }
    });

    public static Pred multiply(IntAtom a, IntAtom b, IntAtom r) {
        return term(multiplyPred, a, b, r);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Functor<Pred> powerPred = functor((SerializableBiFunction<IntAtom, IntAtom, Pred>) Arithmetic::power, t -> {
        TermImpl<IntAtom> at = t.getTerm(1);
        TermImpl<IntAtom> bt = t.getTerm(2);
        BigInteger ai = at != null ? at.getVal(1) : null;
        BigInteger bi = bt != null ? bt.getVal(1) : null;
        if (ai != null && bi != null) {
            return ai.multiply(ai).equals(bi) ? Set.of(t) : Set.of();
        } else if (ai != null && bi == null) {
            return Set.of(t.set(2, at.set(1, ai.multiply(ai))));
        } else if (ai == null && bi != null) {
            BigInteger sqrt = bi.sqrt();
            return Set.of(t.set(1, bt.set(1, sqrt)), t.set(1, bt.set(1, sqrt.negate())));
        } else {
            return t.incomplete();
        }
    });

    public static Pred power(IntAtom a, IntAtom r) {
        return term(powerPred, a, r);
    }

    // Functions

    private static Functor<Pred> gt = functor(Arithmetic::gt);

    public static Pred gt(Int a, Int b) {
        return term(gt, a, b);
    }

    private static Functor<Pred> lt = functor(Arithmetic::lt);

    public static Pred lt(Int a, Int b) {
        return term(lt, a, b);
    }

    private static Functor<Pred> ge = functor(Arithmetic::ge);

    public static Pred ge(Int a, Int b) {
        return term(ge, a, b);
    }

    private static Functor<Pred> le = functor(Arithmetic::le);

    public static Pred le(Int a, Int b) {
        return term(le, a, b);
    }

    private static Functor<IntFunc> plusFunc = functor((SerializableBiFunction<Int, Int, IntFunc>) Arithmetic::plus);

    public static IntFunc plus(Int a, Int b) {
        return term(plusFunc, a, b);
    }

    private static Functor<IntFunc> minusFunc = functor((SerializableBiFunction<Int, Int, IntFunc>) Arithmetic::minus);

    public static IntFunc minus(Int a, Int b) {
        return term(minusFunc, a, b);
    }

    private static Functor<IntFunc> multiplyFunc = functor((SerializableBiFunction<Int, Int, IntFunc>) Arithmetic::multiply);

    public static IntFunc multiply(Int a, Int b) {
        return term(multiplyFunc, a, b);
    }

    private static Functor<IntFunc> divideFunc = functor((SerializableBiFunction<Int, Int, IntFunc>) Arithmetic::divide);

    public static IntFunc divide(Int a, Int b) {
        return term(divideFunc, a, b);
    }

    private static Functor<IntFunc> powerFunc = functor((SerializableFunction<Int, IntFunc>) Arithmetic::power);

    public static IntFunc power(Int a) {
        return term(powerFunc, a);
    }

    private static Functor<IntFunc> sqrtFunc = functor((SerializableFunction<Int, IntFunc>) Arithmetic::sqrt);

    public static IntFunc sqrt(Int a) {
        return term(sqrtFunc, a);
    }

    // Is Rules

    public static void rules() {
        IntAtom PL = ilv("PL");
        IntAtom QL = ilv("QL");
        IntAtom RL = ilv("RL");

        Int X = iv("X");
        Int Y = iv("Y");

        rule(is(PL, RL), goal(eq(PL, RL)));
        rule(is(plus(X, Y), RL), goal(is(X, PL), is(Y, QL), plus(PL, QL, RL)));
        rule(is(minus(X, Y), RL), goal(is(X, PL), is(Y, QL), plus(RL, QL, PL)));
        rule(is(multiply(X, Y), RL), goal(is(X, PL), is(Y, QL), multiply(PL, QL, RL)));
        rule(is(divide(X, Y), RL), goal(is(X, PL), is(Y, QL), multiply(RL, QL, PL)));
        rule(is(power(X), RL), goal(is(X, PL), power(PL, RL)));
        rule(is(sqrt(X), RL), goal(is(X, PL), power(RL, PL)));
        rule(gt(X, Y), goal(is(X, PL), is(Y, QL), compare(PL, QL, i(1))));
        rule(lt(X, Y), goal(is(X, PL), is(Y, QL), compare(PL, QL, i(-1))));
        rule(ge(X, Y), goal(is(X, PL), is(Y, QL), compare(PL, QL, i(1))));
        rule(ge(X, Y), goal(is(X, PL), is(Y, QL), compare(PL, QL, i(0))));
        rule(le(X, Y), goal(is(X, PL), is(Y, QL), compare(PL, QL, i(-1))));
        rule(le(X, Y), goal(is(X, PL), is(Y, QL), compare(PL, QL, i(0))));
    }

}
