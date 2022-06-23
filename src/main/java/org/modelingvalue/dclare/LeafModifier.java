package org.modelingvalue.dclare;

public interface LeafModifier extends FeatureModifier<LeafModifier> {

    static LeafModifier anonymous = new LeafModifier() {
                                      @Override
                                      public String toString() {
                                          return "anonymous";
                                      }
                                  };

    static LeafModifier preserved = new LeafModifier() {
                                      @Override
                                      public String toString() {
                                          return "preserved";
                                      }
                                  };

}
