package org.modelingvalue.dclare;

import java.math.BigInteger;

import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.SerializableBiFunction;
import org.modelingvalue.collections.util.SerializableFunction;
import org.modelingvalue.collections.util.SerializableTriFunction;

public class Arithmetic extends Logic {

    // Is

    private static Functor<Pred> is = functor((SerializableBiFunction<Int, IntLit, Pred>) Arithmetic::is);

    public static Pred is(Int i, IntLit r) {
        return term(is, i, r);
    }

    // Integer

    public static interface Int extends Term {
    }

    public static interface IntLit extends Int {
    }

    public static interface IntFun extends Int {
    }

    private static Functor<IntLit> i = functor((SerializableFunction<BigInteger, IntLit>) Arithmetic::i);

    private static IntLit i(BigInteger x) {
        return term(i, x);
    }

    public static IntLit i(long x) {
        return i(BigInteger.valueOf(x));
    }

    public static IntLit ilv(String name) {
        return var(IntLit.class, name);
    }

    public static IntFun ifv(String name) {
        return var(IntFun.class, name);
    }

    public static Int iv(String name) {
        return var(Int.class, name);
    }

    // Operators

    public static interface Pred extends Term {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Functor<Pred> plusPred = functor((SerializableTriFunction<IntLit, IntLit, IntLit, Pred>) Arithmetic::plus, t -> {
        TermImpl<IntLit> at = t.getTerm(1);
        TermImpl<IntLit> bt = t.getTerm(2);
        TermImpl<IntLit> ct = t.getTerm(3);
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

    public static Pred plus(IntLit a, IntLit b, IntLit r) {
        return term(plusPred, a, b, r);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Functor<Pred> multiplyPred = functor((SerializableTriFunction<IntLit, IntLit, IntLit, Pred>) Arithmetic::multiply, t -> {
        TermImpl<IntLit> at = t.getTerm(1);
        TermImpl<IntLit> bt = t.getTerm(2);
        TermImpl<IntLit> ct = t.getTerm(3);
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

    public static Pred multiply(IntLit a, IntLit b, IntLit r) {
        return term(multiplyPred, a, b, r);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Functor<Pred> powerPred = functor((SerializableBiFunction<IntLit, IntLit, Pred>) Arithmetic::power, t -> {
        TermImpl<IntLit> at = t.getTerm(1);
        TermImpl<IntLit> bt = t.getTerm(2);
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

    public static Pred power(IntLit a, IntLit r) {
        return term(powerPred, a, r);
    }

    // Functions

    private static Functor<IntFun> plusFunc = functor((SerializableBiFunction<Int, Int, IntFun>) Arithmetic::plus);

    public static IntFun plus(Int a, Int b) {
        return term(plusFunc, a, b);
    }

    private static Functor<IntFun> minusFunc = functor((SerializableBiFunction<Int, Int, IntFun>) Arithmetic::minus);

    public static IntFun minus(Int a, Int b) {
        return term(minusFunc, a, b);
    }

    private static Functor<IntFun> multiplyFunc = functor((SerializableBiFunction<Int, Int, IntFun>) Arithmetic::multiply);

    public static IntFun multiply(Int a, Int b) {
        return term(multiplyFunc, a, b);
    }

    private static Functor<IntFun> divideFunc = functor((SerializableBiFunction<Int, Int, IntFun>) Arithmetic::divide);

    public static IntFun divide(Int a, Int b) {
        return term(divideFunc, a, b);
    }

    private static Functor<IntFun> powerFunc = functor((SerializableFunction<Int, IntFun>) Arithmetic::power);

    public static IntFun power(Int a) {
        return term(powerFunc, a);
    }

    private static Functor<IntFun> sqrtFunc = functor((SerializableFunction<Int, IntFun>) Arithmetic::sqrt);

    public static IntFun sqrt(Int a) {
        return term(sqrtFunc, a);
    }

    // Is Rules

    public static void rules() {
        IntLit PL = ilv("PL");
        IntLit QL = ilv("QL");
        IntLit RL = ilv("RL");

        Int X = iv("X");
        Int Y = iv("Y");

        rule(is(PL, RL), eq(PL, RL));
        rule(is(plus(X, Y), RL), is(X, PL), is(Y, QL), plus(PL, QL, RL));
        rule(is(minus(X, Y), RL), is(X, PL), is(Y, QL), plus(RL, QL, PL));
        rule(is(multiply(X, Y), RL), is(X, PL), is(Y, QL), multiply(PL, QL, RL));
        rule(is(divide(X, Y), RL), is(X, PL), is(Y, QL), multiply(RL, QL, PL));
        rule(is(power(X), RL), is(X, PL), power(PL, RL));
        rule(is(sqrt(X), RL), is(X, PL), power(RL, PL));
    }

}
