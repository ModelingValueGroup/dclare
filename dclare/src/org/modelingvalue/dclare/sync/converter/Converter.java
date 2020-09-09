package org.modelingvalue.dclare.sync.converter;

public interface Converter<T0, T1> {
    T1 convertForward(T0 o);

    T0 convertBackward(T1 o);

    static <A, T, B> Converter<A, B> concat(Converter<A, T> a2t, Converter<T, B> t2b) {
        return new Converter<>() {
            @Override
            public B convertForward(A o) {
                return t2b.convertForward(a2t.convertForward(o));
            }

            @Override
            public A convertBackward(B o) {
                return a2t.convertBackward(t2b.convertBackward(o));
            }
        };
    }
}
