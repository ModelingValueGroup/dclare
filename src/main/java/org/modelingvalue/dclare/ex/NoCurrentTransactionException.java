package org.modelingvalue.dclare.ex;

import org.modelingvalue.dclare.Feature;

public class NoCurrentTransactionException extends ConsistencyError {
    private static final long serialVersionUID = -2609383619673309143L;

    public NoCurrentTransactionException(Object object, Feature feature, String message) {
        super(object, feature, 20, message);
    }

}
