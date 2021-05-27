package org.modelingvalue.dclare;

/**
 * this is a marker interface
 */
public interface SetableModifier {
    default SetableModifier iff(boolean b) {
        return b ? this : null;
    }

    default SetableModifier ifnot(boolean b) {
        return b ? null : this;
    }

    default boolean in(SetableModifier[] modifiers) {
        for (SetableModifier m : modifiers) {
            if (this == m) {
                return true;
            }
        }
        return false;
    }
}
