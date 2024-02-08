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

package org.modelingvalue.dclare.test.support;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.util.SerializableConsumer;
import org.modelingvalue.collections.util.SerializableFunction;
import org.modelingvalue.collections.util.SerializablePredicate;
import org.modelingvalue.collections.util.SerializableUnaryOperator;
import org.modelingvalue.collections.util.SerializableUnaryOperator.SerializableUnaryOperatorImpl;
import org.modelingvalue.dclare.*;
import org.modelingvalue.dclare.Construction.Reason;

@SuppressWarnings("rawtypes")
public class TestNewable extends TestMutable implements Newable {

    public static final Observed<TestMutable, String> n = Observed.of("n", null);

    public static TestNewable create(TestNewableClass clazz, Object reason) {
        LeafTransaction tx = LeafTransaction.getCurrent();
        return create(tx, clazz, new TestReason(tx.mutable(), new Object[]{tx.direction(), reason}, null));
    }

    public static TestNewable create(TestNewableClass clazz, Object reason1, Object reason2) {
        LeafTransaction tx = LeafTransaction.getCurrent();
        return create(tx, clazz, new TestReason(tx.mutable(), new Object[]{tx.direction(), reason1, reason2}, null));
    }

    public static TestNewable create(TestNewableClass clazz, SerializableUnaryOperator<TestNewableClass> init) {
        LeafTransaction tx = LeafTransaction.getCurrent();
        SerializableUnaryOperatorImpl<TestNewableClass> lambda = init.of();
        Object[] args = lambda.capturedArgs();
        Object[] id = new Object[args.length + 2];
        id[0] = tx.direction();
        id[1] = lambda.getImplMethodName();
        System.arraycopy(args, 0, id, 2, args.length);
        return create(tx, clazz, new TestReason(tx.mutable(), id, lambda));
    }

    private static TestNewable create(LeafTransaction tx, TestNewableClass clazz, TestReason reason) {
        return tx.construct(reason, () -> new TestNewable(clazz.newObjectInt(), clazz));
    }

    @SuppressWarnings("unchecked")
    public static void construct(TestNewable newable, Object reason) {
        LeafTransaction tx = LeafTransaction.getCurrent();
        tx.construct(new TestReason(tx.mutable(), new Object[]{tx.direction(), reason}, null), () -> newable);
    }

    private TestNewable(Comparable id, TestMutableClass clazz) {
        super(id, clazz);
    }

    @Override
    public Object dIdentity() {
        return dClass().identity().apply(this);
    }

    @Override
    public TestNewableClass dClass() {
        return (TestNewableClass) super.dClass();
    }

    @Override
    public Integer id() {
        return (Integer) super.id();
    }

    @Override
    public TestNewableClass dNewableType() {
        return dClass();
    }

    private Collection<AnonymousClass> anonymous() {
        return dAllDerivations().map(Construction::reason).filter(TestReason.class).map(TestReason::anonymous);
    }

    @Override
    public Collection<? extends Observer<?>> dAllObservers() {
        return Collection.concat(Newable.super.dAllObservers(), anonymous().flatMap(TestNewableClass::dObservers));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Collection<Observer> dAllDerivers(Setable setable) {
        return Collection.concat(Newable.super.dAllDerivers(setable), anonymous().flatMap(a -> a.dDerivers(setable)));
    }

    @Override
    public String toString() {
        Object id = n.get(this);
        return id != null ? super.toString() + ":" + id : super.toString();
    }

    private static class TestReason extends Construction.Reason {

        private final SerializableUnaryOperator<TestNewableClass> init;

        @SuppressWarnings("unchecked")
        private final static Constant<TestReason, AnonymousClass> ANONYMOUS = Constant.of("ANONYMOUS", null, r -> {
                                                                                AnonymousClass anon = new AnonymousClass(r);
                                                                                if (r.init != null) {
                                                                                    r.init.apply(anon);
                                                                                }
                                                                                return anon;
                                                                            });

        @SuppressWarnings("unchecked")
        private TestReason(Mutable thiz, Object[] id, SerializableUnaryOperator<TestNewableClass> init) {
            super(null, id);
            this.init = init;
        }

        public AnonymousClass anonymous() {
            return ANONYMOUS.get(this);
        }

        @Override
        public Direction direction() {
            return (Direction) get(null, 0);
        }

        @Override
        public String toString() {
            return super.toString().substring(getClass().getSimpleName().length());
        }

        @Override
        protected Reason clone(Mutable thiz, Object[] identity) {
            return new TestReason(thiz, identity, init);
        }
    }

    private static final class AnonymousClass extends TestNewableClass {
        private AnonymousClass(TestReason reason) {
            super(reason, reason.direction(), null, new Setable[0]);
        }

        @SuppressWarnings("unused")
        private TestReason reason() {
            return (TestReason) id();
        }

        @Override
        @SuppressWarnings("unchecked")
        public final TestNewableClass observe(SerializableConsumer<TestMutable> action, LeafModifier... modifiers) {
            return super.observe(this::isActive, action, FeatureModifier.add(modifiers, direction(), LeafModifier.anonymous));
        }

        @Override
        @SuppressWarnings("unchecked")
        public final <V> TestNewableClass observe(Setable<TestMutable, V> setable, SerializableFunction<TestMutable, V> value, LeafModifier... modifiers) {
            return super.observe(this::isActive, setable, value, FeatureModifier.add(modifiers, direction(), LeafModifier.anonymous));
        }

        @Override
        @SuppressWarnings("unchecked")
        public TestNewableClass observe(SerializablePredicate<TestMutable> predicate, SerializableConsumer<TestMutable> action, LeafModifier... modifiers) {
            SerializablePredicate<TestMutable> a = this::isActive;
            return (TestNewableClass) super.observe((t) -> a.test(t) && predicate.test(t), action, FeatureModifier.add(modifiers, direction(), LeafModifier.anonymous));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> TestNewableClass observe(SerializablePredicate<TestMutable> predicate, Setable<TestMutable, V> setable, SerializableFunction<TestMutable, V> value, LeafModifier... modifiers) {
            SerializablePredicate<TestMutable> a = this::isActive;
            return (TestNewableClass) super.observe((t) -> a.test(t) && predicate.test(t), setable, value, FeatureModifier.add(modifiers, direction(), LeafModifier.anonymous));
        }

        private boolean isActive(TestMutable object) {
            return object instanceof TestNewable && ((TestNewable) object).anonymous().contains(this);
        }

        @Override
        public String toString() {
            return Integer.toString(System.identityHashCode(this), Character.MAX_RADIX);
        }
    }

}
