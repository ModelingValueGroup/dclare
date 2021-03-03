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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.fail;
import static org.modelingvalue.collections.util.TraceTimer.traceLog;

import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.QualifiedDefaultSet;
import org.modelingvalue.collections.QualifiedSet;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.ContextThread;
import org.modelingvalue.collections.util.ContextThread.ContextPool;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.Constant;
import org.modelingvalue.dclare.Mutable;
import org.modelingvalue.dclare.Observed;
import org.modelingvalue.dclare.Setable;
import org.modelingvalue.dclare.SetableModifier;
import org.modelingvalue.dclare.UniverseTransaction;
import org.modelingvalue.dclare.sync.SerializationHelper;
import org.modelingvalue.dclare.sync.Util;

@SuppressWarnings({"FieldCanBeLocal", "unchecked", "rawtypes"})
public class ModelMaker {
    // TODO: need to fix the bug and remove this workaround:
    public static final boolean                                                                               BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER = true;
    public static final int                                                                                   SOURCE_DEFAULT                           = 11;
    public static final int                                                                                   TARGET_DEFAULT                           = 12;
    public static final int                                                                                   TARGET2_DEFAULT                          = 13;
    private static final java.util.List<Pair<Thread, Throwable>>                                              UNCAUGHT_THROWABLES                      = new ArrayList<>();
    //
    private static final Observed<TestMutable, Integer>                                                       source                                   = TestObserved.of("#source", ModelMaker::id, ModelMaker::desInt, SOURCE_DEFAULT);
    private static final Observed<TestMutable, Integer>                                                       target                                   = TestObserved.of("#target", ModelMaker::id, ModelMaker::desInt, TARGET_DEFAULT);
    private static final Observed<TestMutable, Integer>                                                       target2                                  = TestObserved.of("#target2", ModelMaker::id, ModelMaker::desInt, TARGET2_DEFAULT);
    private static final Observed<TestMutable, TestMutable>                                                   extra                                    = TestObserved.of("#extraRef", ModelMaker::serTestObject, ModelMaker::desTestObject, null, SetableModifier.containment);
    private static final Observed<TestMutable, Set<TestMutable>>                                              extraSet                                 = TestObserved.of("#extraSet", ModelMaker::serTestObjectSet, ModelMaker::desTestObjectSet, Set.of(), SetableModifier.containment);
    private static final Observed<TestMutable, String>                                                        extraString                              = TestObserved.of("#extra\n\"String", ModelMaker::id, ModelMaker::desString, "default");
    private static final Observed<TestMutable, List<String>>                                                  aList                                    = TestObserved.of("#aList", ModelMaker::id, ModelMaker::desList, List.of());
    private static final Observed<TestMutable, Set<String>>                                                   aSet                                     = TestObserved.of("#aSet", ModelMaker::id, ModelMaker::desSet, Set.of());
    private static final Observed<TestMutable, Map<String, String>>                                           aMap                                     = TestObserved.of("#aMap", ModelMaker::id, ModelMaker::desMap, Map.of());
    private static final Observed<TestMutable, DefaultMap<String, String>>                                    aDefMap                                  = TestObserved.of("#aDefMap", ModelMaker::id, ModelMaker::desDefMap, DefaultMap.of(k -> "zut"));
    private static final Observed<TestMutable, QualifiedSet<String, String>>                                  aQuaSet                                  = TestObserved.of("#aQuaSet", ModelMaker::id, ModelMaker::desQuaSet, QualifiedSet.of(v -> v));
    private static final Observed<TestMutable, QualifiedDefaultSet<String, String>>                           aQuaDefSet                               = TestObserved.of("#aQuaDefSet", ModelMaker::id, ModelMaker::desQuaDefSet, QualifiedDefaultSet.of(v -> v, k -> "zutje"));

    public static final SerializationHelper<TestMutableClass, TestMutable, TestObserved<TestMutable, Object>> SERIALIZATION_HELPER                     = new SerializationHelper<>() {
                                                                                                                                                           ////////////////////////////////////////////////////////////////////////////////////////////////////////////
                                                                                                                                                           @Override
                                                                                                                                                           public Predicate<Mutable> mutableFilter() {
                                                                                                                                                               return o -> o instanceof TestMutable;
                                                                                                                                                           }

                                                                                                                                                           @Override
                                                                                                                                                           public Predicate<Setable<TestMutable, ?>> setableFilter() {
                                                                                                                                                               return s -> s instanceof TestObserved;
                                                                                                                                                           }

                                                                                                                                                           ////////////////////////////////////////////////////////////////////////////////////////////////////////////
                                                                                                                                                           @Override
                                                                                                                                                           public String serializeClass(TestMutableClass clazz) {
                                                                                                                                                               return clazz.serializeClass();
                                                                                                                                                           }

                                                                                                                                                           @Override
                                                                                                                                                           public String serializeMutable(TestMutable mutable) {
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
                                                                                                                                                           public TestMutableClass deserializeClass(String s) {
                                                                                                                                                               return TestMutableClass.existing(s);
                                                                                                                                                           }

                                                                                                                                                           @Override
                                                                                                                                                           public TestObserved<TestMutable, Object> deserializeSetable(TestMutableClass clazz, String s) {
                                                                                                                                                               return TestObserved.existing(s);
                                                                                                                                                           }

                                                                                                                                                           @Override
                                                                                                                                                           public TestMutable deserializeMutable(String s) {
                                                                                                                                                               String[] parts = Util.decodeFromLength(s, 2);
                                                                                                                                                               return TestMutable.of(parts[1], deserializeClass(parts[0]));
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

    private static Object serTestObject(TestObserved<TestMutable, TestMutable> s, TestMutable a) {
        return SERIALIZATION_HELPER.serializeMutable(a);
    }

    private static Object serTestObjectSet(TestObserved<TestMutable, Set<TestMutable>> s, Set<TestMutable> a) {
        return a.map(SERIALIZATION_HELPER::serializeMutable);
    }

    private static Integer desInt(TestObserved<TestMutable, Integer> obs, Object a) {
        return ((Number) a).intValue();
    }

    private static TestMutable desTestObject(TestObserved<TestMutable, TestMutable> obs, Object a) {
        return SERIALIZATION_HELPER.deserializeMutable((String) a);
    }

    private static Set<TestMutable> desTestObjectSet(TestObserved<TestMutable, Set<TestMutable>> obs, Object a) {
        List<String> oo = (List<String>) a;
        return oo.map(SERIALIZATION_HELPER::deserializeMutable).toSet();
    }

    private static String desString(TestObserved<TestMutable, String> obs, Object a) {
        return (String) a;
    }

    private static List<String> desList(TestObserved<TestMutable, List<String>> obs, Object a) {
        return (List<String>) a;
    }

    private static Set<String> desSet(TestObserved<TestMutable, Set<String>> obs, Object a) {
        List<String> oo = (List<String>) a;
        return oo.toSet();
    }

    private static Map<String, String> desMap(TestObserved<TestMutable, Map<String, String>> obs, Object a) {
        return (Map<String, String>) a;
    }

    private static DefaultMap<String, String> desDefMap(TestObserved<TestMutable, DefaultMap<String, String>> obs, Object a) {
        List<List<String>> oo = (List<List<String>>) a;
        return obs.getDefault().addAll(oo.map(l -> Entry.of(l.get(0), l.get(1))).toList());
    }

    private static QualifiedSet<String, String> desQuaSet(TestObserved<TestMutable, QualifiedSet<String, String>> obs, Object a) {
        List<String> oo = (List<String>) a;
        return obs.getDefault().addAll(oo);
    }

    private static QualifiedDefaultSet<String, String> desQuaDefSet(TestObserved<TestMutable, QualifiedDefaultSet<String, String>> obs, Object o) {
        List<String> oo = (List<String>) o;
        return obs.getDefault().addAll(oo);
    }

    private static final TestMutableClass            extraClass      = TestMutableClass.of("ExtraClass");
    private static final TestMutableClass            plughClassMain  = TestMutableClass.of("PlughClass").observe(                                  //
            o -> target.set(o, source.get(o)),                                                                                                     //
            o -> extraString.set(o, "@@@@\n\"@@@" + source.get(o) + "@@@"),                                                                        //
            o -> extra.set(o, TestMutable.of("" + source.get(o), extraClass)));
    private static final TestMutableClass            plughClassRobot = TestMutableClass.of("PlughClass").observe(                                  //
            o -> target.set(o, source.get(o)),                                                                                                     //
            o -> target2.set(o, source.get(o)),                                                                                                    //
            o -> extraString.set(o, "@@@@\n\"@@@" + source.get(o) + "@@@"),                                                                        //
            o -> extra.set(o, TestMutable.of("" + source.get(o), extraClass)),                                                                     //
            o -> extraSet.set(o, Collection.range(0, source.get(o)).flatMap(i -> Stream.of(TestMutable.of("TO-" + i, extraClass))).toSet()),       //
            o -> target.set(extra.get(o), target.get(o)),                                                                                          //
            o -> aList.set(o, Collection.range(0, source.get(o)).map(i -> "~" + i).toList()),                                                      //
            o -> aSet.set(o, Collection.range(0, source.get(o)).flatMap(i -> Stream.of("&" + i, "@" + i * 2)).toSet()),                            //
            o -> aMap.set(o, Collection.range(0, source.get(o)).toMap(i -> Entry.of(i + "!m!k!", i + "!m!v!"))),                                   //
            o -> aDefMap.set(o, aDefMap.getDefault().addAll(Collection.range(0, source.get(o)).map(i -> Entry.of(i + "!dm!k!", i + "!dm!v!")))),   //
            o -> aQuaSet.set(o, aQuaSet.getDefault().addAll(Collection.range(0, source.get(o)).map(i -> "QS" + i))),                               //
            o -> aQuaDefSet.set(o, aQuaDefSet.getDefault().addAll(Collection.range(0, source.get(o)).map(i -> "QDS" + i))));
    private final String                             name;
    private final TestMutable                        xyzzy;
    private final Constant<TestMutable, TestMutable> plugConst;
    private final TestMutableClass                   universeClass;
    private final TestUniverse                       universe;
    private final ContextPool                        pool;
    private final UniverseTransaction                tx;

    public ModelMaker(String name, boolean isRobot) {
        this.name = name;
        xyzzy = TestMutable.of("xyzzy\n\"", isRobot ? plughClassRobot : plughClassMain);
        plugConst = Constant.of("plugConst", u -> xyzzy, SetableModifier.containment);
        universeClass = TestMutableClass.of("Universe-" + name, plugConst);
        universe = TestUniverse.of("universe-" + name, universeClass, TestImperative.of());
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

    public Set<TestMutable> getXyzzy_extraSet() {
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

    public TestMutable getXyzzy_extra() {
        return tx.currentState().get(xyzzy, extra);
    }
}
