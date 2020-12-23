package org.modelingvalue.dclare;

import java.util.function.Supplier;

import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.IdentifiedByArray;

public class Construction extends IdentifiedByArray {

    protected static final Constant<Construction, Newable> CONSTRUCTED = //
            Constant.of("D_CONSTRUCTED", (Newable) null, (tx, c, p, n) -> Newable.CONSTRUCTIONS.set(n, Set::add, c));

    public static Construction of(Object object, Feature feature, Context context) {
        return new Construction(object, feature, context);
    }

    private Construction(Object object, Feature feature, Context context) {
        super(new Object[]{object, feature, context});
    }

    public Object object() {
        return array()[0];
    }

    public Feature feature() {
        return (Feature) array()[1];
    }

    public Context context() {
        return (Context) array()[2];
    }

    public boolean isObserver() {
        return feature() instanceof Observer;
    }

    public boolean isNotObserver() {
        return !(feature() instanceof Observer);
    }

    public static Set<Newable> sources(Set<Construction> cons, Set<Newable> sources) {
        for (Construction c : cons) {
            sources = sources.addAll(c.sources(sources));
        }
        return sources;
    }

    private Set<Newable> sources(Set<Newable> sources) {
        if (object() instanceof Newable && !sources.contains(object())) {
            sources = sources.add((Newable) object());
            sources = sources.addAll(sources(((Newable) object()).dConstructions(), sources));
        }
        Object[] array = context().array();
        for (int i = 0; i < array.length; i++) {
            if (array[i] instanceof Newable && !sources.contains(array[i])) {
                sources = sources.add((Newable) array[i]);
                sources = sources.addAll(sources(((Newable) array[i]).dConstructions(), sources));
            }
        }
        return sources;
    }

    public static final class Context extends IdentifiedByArray {

        public Context(Object[] identity) {
            super(identity);
        }

        public <O extends Newable> O construct(Supplier<O> supplier) {
            return currentLeaf().construct(this, supplier);
        }

        private LeafTransaction currentLeaf() {
            LeafTransaction current = LeafTransaction.getCurrent();
            if (current == null) {
                throw new NullPointerException("No current transaction in " + Thread.currentThread() + " , while accessing " + toString());
            }
            return current;
        }

    }

}
