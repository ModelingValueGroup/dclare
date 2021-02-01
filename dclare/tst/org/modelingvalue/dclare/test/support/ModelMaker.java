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

package org.modelingvalue.dclare.test.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.modelingvalue.collections.util.TraceTimer.*;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.*;
import org.modelingvalue.collections.util.*;
import org.modelingvalue.collections.util.ContextThread.*;
import org.modelingvalue.dclare.Observer;
import org.modelingvalue.dclare.*;
import org.modelingvalue.dclare.sync.*;

@SuppressWarnings({"FieldCanBeLocal", "unchecked", "rawtypes"})
public class ModelMaker {
    // TODO: need to fix the bug and remove this workaround:
    public static final  boolean                                                   BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER = true;
    public static final  int                                                       SOURCE_DEFAULT                           = 11;
    public static final  int                                                       TARGET_DEFAULT                           = 12;
    public static final  int                                                       TARGET2_DEFAULT                          = 13;
    private static final java.util.List<Pair<Thread, Throwable>>                   UNCAUGHT_THROWABLES                      = new ArrayList<>();
    //
    private static final Observed<TestObject, Integer>                             source                                   = TestObserved.of("#source", ModelMaker::id, ModelMaker::desInt, SOURCE_DEFAULT);
    private static final Observed<TestObject, Integer>                             target                                   = TestObserved.of("#target", ModelMaker::id, ModelMaker::desInt, TARGET_DEFAULT);
    private static final Observed<TestObject, Integer>                             target2                                  = TestObserved.of("#target2", ModelMaker::id, ModelMaker::desInt, TARGET2_DEFAULT);
    private static final Observed<TestObject, TestObject>                          extra                                    = TestObserved.of("#extraRef", ModelMaker::serTestObject, ModelMaker::desTestObject, null, true);
    private static final Observed<TestObject, Set<TestObject>>                     extraSet                                 = TestObserved.of("#extraSet", ModelMaker::serTestObjectSet, ModelMaker::desTestObjectSet, Set.of(), true);
    private static final Observed<TestObject, String>                              extraString                              = TestObserved.of("#extra\n\"String", ModelMaker::id, ModelMaker::desString, "default");
    private static final Observed<TestObject, List<String>>                        aList                                    = TestObserved.of("#aList", ModelMaker::id, ModelMaker::desList, List.of());
    private static final Observed<TestObject, Set<String>>                         aSet                                     = TestObserved.of("#aSet", ModelMaker::id, ModelMaker::desSet, Set.of());
    private static final Observed<TestObject, Map<String, String>>                 aMap                                     = TestObserved.of("#aMap", ModelMaker::id, ModelMaker::desMap, Map.of());
    private static final Observed<TestObject, DefaultMap<String, String>>          aDefMap                                  = TestObserved.of("#aDefMap", ModelMaker::id, ModelMaker::desDefMap, DefaultMap.of(k -> "zut"));
    private static final Observed<TestObject, QualifiedSet<String, String>>        aQuaSet                                  = TestObserved.of("#aQuaSet", ModelMaker::id, ModelMaker::desQuaSet, QualifiedSet.of(v -> v));
    private static final Observed<TestObject, QualifiedDefaultSet<String, String>> aQuaDefSet                               = TestObserved.of("#aQuaDefSet", ModelMaker::id, ModelMaker::desQuaDefSet, QualifiedDefaultSet.of(v -> v, k -> "zutje"));


    public static final SerializationHelper<TestClass, TestObject, TestObserved<TestObject, Object>> SERIALIZATION_HELPER = new SerializationHelper<>() {
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////
        @Override
        public Predicate<Mutable> mutableFilter() {
            return o -> o instanceof TestObject;
        }

        @Override
        public Predicate<Setable<TestObject, ?>> setableFilter() {
            return s -> s.id().toString().startsWith("#");
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////
        @Override
        public String serializeClass(TestClass clazz) {
            return clazz.serializeClass();
        }

        @Override
        public String serializeMutable(TestObject mutable) {
            return Util.encodeWithLength(serializeClass(mutable.dClass()), mutable.serialize());
        }

        @Override
        public String serializeSetable(TestObserved setable) {
            return setable.toString();
        }

        @Override
        public Object serializeValue(TestObserved setable, Object value) {
            return setable.getSerializeValue().apply(setable, value);
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////
        @Override
        public TestClass deserializeClass(String s) {
            return TestClass.existing(s);
        }

        @Override
        public TestObserved<TestObject, Object> deserializeSetable(TestClass clazz, String s) {
            return TestObserved.existing(s);
        }

        @Override
        public TestObject deserializeMutable(String s) {
            String[] parts = Util.decodeFromLength(s, 2);
            return TestObject.of(parts[1], deserializeClass(parts[0]));
        }

        @Override
        public Object deserializeValue(TestObserved setable, Object value) {
            return setable.getDeserializeValue().apply(setable, value);
        }
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    };

    private static <S, T> T id(S s, T a) {
        return a;
    }

    private static Object serTestObject(TestObserved<TestObject, TestObject> s, TestObject a) {
        return SERIALIZATION_HELPER.serializeMutable(a);
    }

    private static Object serTestObjectSet(TestObserved<TestObject, Set<TestObject>> s, Set<TestObject> a) {
        return a.map(SERIALIZATION_HELPER::serializeMutable);
    }

    private static Integer desInt(TestObserved<TestObject, Integer> obs, Object a) {
        return ((Number) a).intValue();
    }

    private static TestObject desTestObject(TestObserved<TestObject, TestObject> obs, Object a) {
        return SERIALIZATION_HELPER.deserializeMutable((String) a);
    }

    private static Set<TestObject> desTestObjectSet(TestObserved<TestObject, Set<TestObject>> obs, Object a) {
        List<String> oo = (List<String>) a;
        return oo.map(SERIALIZATION_HELPER::deserializeMutable).toSet();
    }

    private static String desString(TestObserved<TestObject, String> obs, Object a) {
        return (String) a;
    }

    private static List<String> desList(TestObserved<TestObject, List<String>> obs, Object a) {
        return (List<String>) a;
    }

    private static Set<String> desSet(TestObserved<TestObject, Set<String>> obs, Object a) {
        List<String> oo = (List<String>) a;
        return oo.toSet();
    }

    private static Map<String, String> desMap(TestObserved<TestObject, Map<String, String>> obs, Object a) {
        return (Map<String, String>) a;
    }

    private static DefaultMap<String, String> desDefMap(TestObserved<TestObject, DefaultMap<String, String>> obs, Object a) {
        List<List<String>> oo = (List<List<String>>) a;
        return obs.getDefault().addAll(oo.map(l -> Entry.of(l.get(0), l.get(1))).toList());
    }

    private static QualifiedSet<String, String> desQuaSet(TestObserved<TestObject, QualifiedSet<String, String>> obs, Object a) {
        List<String> oo = (List<String>) a;
        return obs.getDefault().addAll(oo);
    }

    private static QualifiedDefaultSet<String, String> desQuaDefSet(TestObserved<TestObject, QualifiedDefaultSet<String, String>> obs, Object o) {
        List<String> oo = (List<String>) o;
        return obs.getDefault().addAll(oo);
    }

    private static final TestClass                        extraClass      = TestClass.of("ExtraClass");
    private static final TestClass                        plughClassMain  = TestClass.of("PlughClass",
            Observer.of("M-source->target      ", o -> target.set(o, source.get(o))),
            Observer.of("M-source->extraString ", o -> extraString.set(o, "@@@@\n\"@@@" + source.get(o) + "@@@")),
            Observer.of("M-source->extra       ", o -> extra.set(o, TestObject.of("" + source.get(o), extraClass)))
    );
    private static final TestClass                        plughClassRobot = TestClass.of("PlughClass",
            Observer.of("R-source->target      ", o -> target.set(o, source.get(o))),
            Observer.of("R-source->target2     ", o -> target2.set(o, source.get(o))),
            Observer.of("R-source->extraString ", o -> extraString.set(o, "@@@@\n\"@@@" + source.get(o) + "@@@")),
            Observer.of("R-source->extra       ", o -> extra.set(o, TestObject.of("" + source.get(o), extraClass))),
            Observer.of("R-source->extraSet    ", o -> extraSet.set(o, Collection.range(0, source.get(o)).flatMap(i -> Stream.of(TestObject.of("TO-" + i, extraClass))).toSet())),
            Observer.of("R-target->extra.target", o -> target.set(extra.get(o), target.get(o))),
            Observer.of("R-target->aList       ", o -> aList.set(o, Collection.range(0, source.get(o)).map(i -> "~" + i).toList())),
            Observer.of("R-target->aSet        ", o -> aSet.set(o, Collection.range(0, source.get(o)).flatMap(i -> Stream.of("&" + i, "@" + i * 2)).toSet())),
            Observer.of("R-target->aMap        ", o -> aMap.set(o, Collection.range(0, source.get(o)).toMap(i -> Entry.of(i + "!m!k!", i + "!m!v!")))),
            Observer.of("R-target->aDefMap     ", o -> aDefMap.set(o, aDefMap.getDefault().addAll(Collection.range(0, source.get(o)).map(i -> Entry.of(i + "!dm!k!", i + "!dm!v!"))))),
            Observer.of("R-target->aQuaSet     ", o -> aQuaSet.set(o, aQuaSet.getDefault().addAll(Collection.range(0, source.get(o)).map(i -> "QS" + i)))),
            Observer.of("R-target->aQuaDefSet  ", o -> aQuaDefSet.set(o, aQuaDefSet.getDefault().addAll(Collection.range(0, source.get(o)).map(i -> "QDS" + i))))
    );
    private final        String                           name;
    private final        TestObject                       xyzzy;
    private final        Constant<TestObject, TestObject> plugConst;
    private final        TestClass                        universeClass;
    private final        TestUniverse                     universe;
    private final        ContextPool                      pool;
    private final        UniverseTransaction              tx;

    public ModelMaker(String name, boolean isRobot) {
        this.name = name;
        xyzzy = TestObject.of("xyzzy\n\"", isRobot ? plughClassRobot : plughClassMain);
        plugConst = Constant.of("plugConst", true, u -> xyzzy);
        universeClass = TestClass.of("Universe-" + name, plugConst);
        universe = TestUniverse.of("universe-" + name, universeClass);
        pool = newPool();
        tx = UniverseTransaction.of(universe, pool);

        CommunicationHelper.add(this);
        CommunicationHelper.add(pool);
    }

    public String getName() {
        return name;
    }

    public UniverseTransaction getTx() {
        return tx;
    }

    private static ContextPool BUGGERS_POOL;

    private static ContextPool newPool() {
        if (BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER) {
            if (BUGGERS_POOL == null) {
                BUGGERS_POOL = ContextThread.createPool(2, ModelMaker::uncaughtThrowables);
            }
            return BUGGERS_POOL;
        }
        return ContextThread.createPool(2, ModelMaker::uncaughtThrowables);
    }

    private static void uncaughtThrowables(Thread thread, Throwable throwable) {
        traceLog("ALARM: uncaught exception in pool thread %s: %s", thread.getName(), throwable);
        synchronized (UNCAUGHT_THROWABLES) {
            UNCAUGHT_THROWABLES.add(Pair.of(thread, throwable));
        }
    }

    public static void assertNoUncaughtThrowables() {
        assertAll("uncaught in pool", UNCAUGHT_THROWABLES.stream().map(u -> () -> fail("uncaught in " + u.a().getName(), u.b())));
    }

    public void setXyzzy_source(int i) {
        tx.put("set source to " + i, () -> ModelMaker.source.set(xyzzy, i));
    }

    public int getXyzzy_source() {
        return tx.currentState().get(xyzzy, source);
    }

    public int getXyzzy_target() {
        return tx.currentState().get(xyzzy, target);
    }

    public int getXyzzy_target2() {
        return tx.currentState().get(xyzzy, target2);
    }

    public List<String> getXyzzy_aList() {
        return tx.currentState().get(xyzzy, aList);
    }

    public Set<String> getXyzzy_aSet() {
        return tx.currentState().get(xyzzy, aSet);
    }

    public Set<TestObject> getXyzzy_extraSet() {
        return tx.currentState().get(xyzzy, extraSet);
    }

    public Map<String, String> getXyzzy_aMap() {
        return tx.currentState().get(xyzzy, aMap);
    }

    public DefaultMap<String, String> getXyzzy_aDefMap() {
        return tx.currentState().get(xyzzy, aDefMap);
    }

    public QualifiedSet<String, String> getXyzzy_aQuaSet() {
        return tx.currentState().get(xyzzy, aQuaSet);
    }

    public QualifiedDefaultSet<String, String> getXyzzy_aQuaDefSet() {
        return tx.currentState().get(xyzzy, aQuaDefSet);
    }

    public TestObject getXyzzy_extra() {
        return tx.currentState().get(xyzzy, extra);
    }
}
