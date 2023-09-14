package org.modelingvalue.dclare.ex;

import org.modelingvalue.collections.List;
import org.modelingvalue.dclare.Mutable;
import org.modelingvalue.dclare.Setable;

import java.io.Serial;

public class CycleInParentChainException extends ConsistencyError {
    @Serial
    private static final long serialVersionUID = -8866815750182251938L;

    private final List<Mutable> chain;

    public CycleInParentChainException(Object object, Setable<?, ?> setable, List<Mutable> chain) {
        super(object, setable, 10);
        this.chain = chain;
    }

    @Override
    public String getMessage() {
        return "cycle (#" + (chain.size() - 1) + ") detected in parent chain while getting property '" + getFeature() + "' of object '" + getObject() + "' chain=" + chain;
    }
}
