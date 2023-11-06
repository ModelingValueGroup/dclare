//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2023 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import org.modelingvalue.collections.List;
import org.modelingvalue.collections.util.IdentifiedBy;
import org.modelingvalue.collections.util.Pair;

import java.util.Optional;

public interface TypeWrapper {

    Constant<TypeWrapper, List<TypeWrapper>> SUPERS = Constant.of(Pair.of("SUPERS", TypeWrapper.class), TypeWrapper::computeSupers);
    List<TypeWrapper> computeSupers();

    default List<TypeWrapper> supers(){
        return SUPERS.get(this);
    }

    Constant<Pair<TypeWrapper, TypeWrapper>, Boolean> IS_ASSIGNABLE_TO = Constant.of(Pair.of("IS_ASSIGNABLE_TO", TypeWrapper.class), TypeWrapper::isAssignableTo);
    private static boolean isAssignableTo(Pair<TypeWrapper, TypeWrapper> pair){
        var sub = pair.a();
        var sup = pair.b();
        return sub.equals(sup) || sub.supers().anyMatch(t -> IS_ASSIGNABLE_TO.get(Pair.of(t, sup)));
    }

    default boolean isAssignableTo(TypeWrapper type){
        return IS_ASSIGNABLE_TO.get(Pair.of(this, type));
    }

    default boolean isSuperOf(TypeWrapper sub){
        if(this.equals(sub)) return false;
        return sub.isAssignableTo(this);
    }

    default boolean isSubOf(TypeWrapper sup){
        if(this.equals(sup)) return false;
        return this.isAssignableTo(sup);
    }

    Constant<TypeWrapper, List<TypeWrapper>> LINEARIZATION = Constant.of(Pair.of("LINEARIZATION", TypeWrapper.class), tw -> TypeWrapper.linearization(tw));
    private static List<TypeWrapper> linearization(TypeWrapper type){
        var supers = type.supers();
        var ls = supers.map(LINEARIZATION::get).toList();
        return merge(List.of(type), ls, supers);
    }

    private static List<TypeWrapper> merge(List<TypeWrapper> result, List<List<TypeWrapper>> supers, List<TypeWrapper> immediates){
        var supersFiltered = supers.filter(m -> !m.isEmpty()).toList();
        var combined = supers.add(immediates).filter(m -> !m.isEmpty()).toList();
        if(combined.isEmpty()) return result;
        var candidate = getMergeCandidate(combined)
                .or(() -> getMergeCandidate(supersFiltered))
                .orElseGet(() -> combined.get(0).get(0));
        return merge(result.add(candidate), supersFiltered.map(l -> l.remove(candidate)).toList(), immediates.remove(candidate));
    }

    private static Optional<TypeWrapper> getMergeCandidate(List<List<TypeWrapper>> merge){
        for (var list : merge) {
            var head = list.get(0);
            if(merge.noneMatch(l -> l.lastIndexOf(head) > 0)) return Optional.of(head);
        }
        return Optional.empty();
    }

    default List<TypeWrapper> linearization(){
        return LINEARIZATION.get(this);
    }

    class ObjectType extends IdentifiedBy<Class<?>> implements TypeWrapper{

        public ObjectType(Class<?> clazz) {
            super(clazz);
        }

        @Override
        public List<TypeWrapper> computeSupers() {
            if (this.id() == Object.class) {
                return List.of();
            } else {
                var sups = List.<Class<?>, TypeWrapper>of(ObjectType::new, this.id().getInterfaces());
                sups = sups.add(this.id().getSuperclass() == null ? new ObjectType(Object.class) : new ObjectType(this.id().getSuperclass()));
                return sups;
            }
        }

        @Override
        public String toString() {
            return "object<" + this.id().toString() + ">";
        }
    }

}
