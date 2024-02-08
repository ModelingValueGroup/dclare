//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2024 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
//                                                                                                                       ~
//  Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in       ~
//  compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0   ~
//  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on  ~
//  an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the   ~
//  specific language governing permissions and limitations under the License.                                           ~
//                                                                                                                       ~
//  Maintainers:                                                                                                         ~
//      Wim Bast, Tom Brus                                                                                               ~
//                                                                                                                       ~
//  Contributors:                                                                                                        ~
//      Ronald Krijgsheld ✝, Arjan Kok, Carel Bast                                                                       ~
// --------------------------------------------------------------------------------------------------------------------- ~
//  In Memory of Ronald Krijgsheld, 1972 - 2023                                                                          ~
//      Ronald was suddenly and unexpectedly taken from us. He was not only our long-term colleague and team member      ~
//      but also our friend. "He will live on in many of the lines of code you see below."                               ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.dclare;

import java.util.Objects;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.StringUtil;

public abstract class Leaf implements TransactionClass, Feature {
    private final Object               id;
    private final Priority             initPriority;
    private final Set<LeafModifier<?>> modifierSet;
    private final int                  hashCode;

    protected Leaf(Object id, LeafModifier<?>... modifiers) {
        this.id          = id;
        this.modifierSet = Collection.of(modifiers).notNull().asSet();
        Priority prio = getModifier(Priority.class);
        this.initPriority = prio == null ? Priority.one : prio;
        this.hashCode     = Objects.hash(id, getClass());
    }

    public boolean hasModifier(LeafModifier<?> modifier) {
        return modifierSet.contains(modifier);
    }

    protected  <SM extends LeafModifier<?>> SM getModifier(Class<SM> modifierClass) {
        return FeatureModifier.ofClass(modifierClass, modifierSet);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (getClass() != obj.getClass()) {
            return false;
        } else {
            return id.equals(((Leaf) obj).id);
        }
    }

    @Override
    public String toString() {
        return StringUtil.toString(id);
    }

    public Object id() {
        return id;
    }

    protected final Priority initPriority() {
        return initPriority;
    }
}
