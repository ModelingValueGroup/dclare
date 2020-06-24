package org.modelingvalue.dclare.test;

import static java.math.BigInteger.*;

import java.math.BigInteger;

import org.modelingvalue.dclare.Constant;

public class Fibonacci {
    static final BigInteger ONE = BigInteger.valueOf(1);
    static final BigInteger TWO = BigInteger.valueOf(2);

    static final Constant<BigInteger, BigInteger> FIBONACCI = Constant.of("FIBONACCI", n -> {
        if (n.equals(ZERO) || n.equals(ONE)) {
            return n;
        } else {
            BigInteger one = Fibonacci.FIBONACCI.get(n.subtract(ONE));
            BigInteger two = Fibonacci.FIBONACCI.get(n.subtract(TWO));
            return one.add(two);
        }
    });
}
