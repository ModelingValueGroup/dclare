package org.modelingvalue.dclare.test.support;

import org.modelingvalue.dclare.sync.converter.*;

public class CommunicationModelMakerWithDeltaAdaptor extends CommunicationModelMaker {
    private final TestDeltaAdaptor adaptor;

    public CommunicationModelMakerWithDeltaAdaptor(String name, boolean noDeltasOut) {
        super(name);
        adaptor = new TestDeltaAdaptor(
                name,
                getTx(),
                noDeltasOut,
                o -> o instanceof TestObject,
                s -> s.id().toString().startsWith("#"),
                new ConvertJson()
        );
        CommunicationHelper.add(this);
    }

    public TestDeltaAdaptor getDeltaAdaptor() {
        return adaptor;
    }
}
