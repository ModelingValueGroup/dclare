//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2021 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
//                                                                                                                     ~
// Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in      ~
// compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0  ~
// Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on ~
// an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the  ~
// specific language governing permissions and limitations under the License.                                          ~
//                                                                                                                     ~
// Maintainers:                                                                                                        ~
//     Wim Bast, Tom Brus, Ronald Krijgsheld                                                                           ~
// Contributors:                                                                                                       ~
//     Arjan Kok, Carel Bast                                                                                           ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.dclare;

import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Mergeable;

public interface Newable extends Mutable, Mergeable<Newable> {

    Newable                              MERGER               = new Newable() {
                                                                  @Override
                                                                  public MutableClass dClass() {
                                                                      throw new UnsupportedOperationException();
                                                                  }

                                                                  @Override
                                                                  public Object dIdentity() {
                                                                      throw new UnsupportedOperationException();
                                                                  }

                                                                  @Override
                                                                  public Object dNewableType() {
                                                                      throw new UnsupportedOperationException();
                                                                  }

                                                                  @Override
                                                                  public Comparable<?> dSortKey() {
                                                                      throw new UnsupportedOperationException();
                                                                  }
                                                              };

    Observed<Newable, Set<Construction>> CONSTRUCTIONS        = Observed.of("D_CONSTRUCTIONS", Set.of(), SetableModifier.synthetic, SetableModifier.doNotCheckConsistency);

    Observer<Newable>                    D_CONSTRUCTIONS_RULE = Observer.of("D_CONSTRUCTIONS_RULE", n -> CONSTRUCTIONS.set(n, cs -> {
                                                                  for (Construction c : cs) {
                                                                      if (c.object() instanceof Newable && CONSTRUCTIONS.get((Newable) c.object()).isEmpty()) {
                                                                          cs = cs.remove(c);
                                                                      }
                                                                  }
                                                                  return cs;
                                                              }));

    Object dIdentity();

    Object dNewableType();

    @SuppressWarnings("rawtypes")
    Comparable dSortKey();

    default Set<Construction> dConstructions() {
        return CONSTRUCTIONS.current(this);
    }

    @Override
    default boolean dIsIdentified() {
        return dIdentity() != null;
    }

    @Override
    default boolean dIsObsolete() {
        return CONSTRUCTIONS.current(this).isEmpty();
    }

    @Override
    default Newable merge(Newable[] branches, int length) {
        for (int i = branches.length - 1; i >= 0; i--) {
            if (branches[i] != null && branches[i] != MERGER && !branches[i].dIsObsolete()) {
                return branches[i];
            }
        }
        return this;
    }

    @Override
    default Newable getMerger() {
        return MERGER;
    }

    @Override
    default Class<Newable> getMeetClass() {
        return Newable.class;
    }

    @Override
    default void dActivate() {
        Mutable.super.dActivate();
        D_CONSTRUCTIONS_RULE.trigger(this);
    }

    @Override
    default void dDeactivate() {
        Mutable.super.dDeactivate();
        D_CONSTRUCTIONS_RULE.deObserve(this);
    }

}
