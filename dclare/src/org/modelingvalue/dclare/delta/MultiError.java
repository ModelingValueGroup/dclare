package org.modelingvalue.dclare.delta;

import java.util.ArrayList;
import java.util.List;

public class MultiError extends Error {
    private final List<Throwable> causes = new ArrayList<Throwable>();

    public MultiError(String message, List<Throwable> causes) {
        super(message, causes.isEmpty() ? null : causes.get(0));
    }

    public MultiError(List<Throwable> causes) {
        this(null, causes);
    }

    public void add(Exception e) {
        causes.add(e);
    }

    public List<Throwable> getCauses() {
        return causes;
    }
}
