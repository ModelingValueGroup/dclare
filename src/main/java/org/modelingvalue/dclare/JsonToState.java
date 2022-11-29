package org.modelingvalue.dclare;

import org.modelingvalue.dclare.sync.JsonIC.FromJsonIC;

public class JsonToState extends FromJsonIC {
    public <U extends Universe> JsonToState(@SuppressWarnings("unused") U universe, String json) {
        super(json);
    }
}
