package org.modelingvalue.dclare;

public interface FixpointGroup {

    FixpointGroup DEFAULT = new FixpointGroup() {

        @Override
        public String toString() {
            return "<DEF>";
        }

    };

    static FixpointGroup of(String name) {
        return new FixpointGroup() {
            @Override
            public String toString() {
                return name;
            }
        };
    }

}
