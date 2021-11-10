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

package org.modelingvalue.dclare.test;

import org.junit.jupiter.api.*;
import org.modelingvalue.collections.*;
import org.modelingvalue.dclare.*;
import org.modelingvalue.dclare.sync.Converters.*;
import org.modelingvalue.dclare.sync.*;

import java.util.function.*;

public class SerialisationPoolTests {
//    static {
//        System.setProperty("TRACE_SERIALIZATION", "true");
//    }

    TestSerializationHelperWithPool h = new TestSerializationHelperWithPool();

    @Test
    public void stringTests() {
        Assertions.assertEquals("s:xyzzy", h.serializeValue(null, "xyzzy"));
        Assertions.assertEquals("plugh", h.deserializeValue(null, "s:plugh"));

        Assertions.assertEquals("s:x:y:z:z:y", h.serializeValue(null, "x:y:z:z:y"));
        Assertions.assertEquals("::p:l:u:g:h::", h.deserializeValue(null, "s:::p:l:u:g:h::"));
    }

    @Test
    public void absentTests() {
        Assertions.assertEquals("__ERROR__:no Converter for int[]", h.serializeValue(null, new int[3]));
        Assertions.assertNull(h.deserializeValue(null, "unknown:#$%^&"));
        Assertions.assertNull(h.deserializeValue(null, "just some string"));
        Assertions.assertNull(h.deserializeValue(null, ""));
        Assertions.assertNull(h.deserializeValue(null, null));
    }

    @Test
    public void listTests() {
        Assertions.assertEquals("list:[\"s:a\",\"s:b\",\"s:c\"]", h.serializeValue(null, java.util.List.of("a", "b", "c")));
        Assertions.assertEquals(java.util.List.of("x", "y", "z"), h.deserializeValue(null, "list:[\"s:x\",\"s:y\",\"s:z\"]"));

        Assertions.assertEquals("immutable-list:[\"s:a\",\"s:b\",\"s:c\"]", h.serializeValue(null, List.of("a", "b", "c")));
        Assertions.assertEquals(List.of("x", "y", "z"), h.deserializeValue(null, "immutable-list:[\"s:x\",\"s:y\",\"s:z\"]"));
    }

    @Test
    public void setTests() {
        Assertions.assertTrue(h.serializeValue(null, java.util.Set.of("a", "b", "c")).toString().matches("set:\\[\"s:([abc])\",\"s:([abc])\",\"s:([abc])\"]"));
        Assertions.assertEquals(java.util.Set.of("x", "y", "z"), h.deserializeValue(null, "set:[\"s:x\",\"s:y\",\"s:z\"]"));

        Assertions.assertTrue(h.serializeValue(null, Set.of("a", "b", "c")).toString().matches("immutable-set:\\[\"s:([abc])\",\"s:([abc])\",\"s:([abc])\"]"));
        Assertions.assertEquals(Set.of("x", "y", "z"), h.deserializeValue(null, "immutable-set:[\"s:x\",\"s:y\",\"s:z\"]"));
    }

    @Test
    public void mapTests() {
        Assertions.assertEquals("map:{\"s:a\":\"I:21\",\"s:b\":\"I:69\",\"s:c\":\"I:99\"}", h.serializeValue(null, java.util.Map.of("a", 21, "b", 69, "c", 99)));
        Assertions.assertEquals(java.util.Map.of(21, "x", 43, "y", 12, "z"), h.deserializeValue(null, "map:{\"I:21\":\"s:x\",\"I:43\":\"s:y\",\"I:12\":\"s:z\"}"));

        Assertions.assertEquals("immutable-map:{\"C:c\":\"J:88\",\"s:@\":\"Z:true\"}", h.serializeValue(null, Map.of(Entry.of('c', 88L), Entry.of("@", true))));
        Assertions.assertEquals(Map.of(Entry.of(false, "no"), Entry.of(true, "yes")), h.deserializeValue(null, "immutable-map:{\"Z:true\":\"s:yes\",\"Z:false\":\"s:no\"}"));
    }


    private static class TestSerializationHelperWithPool extends SerializationHelperWithPool<MutableClass, Mutable, Setable<Mutable, ?>> {
        public TestSerializationHelperWithPool() {
            super(
                    new ByteConverter(),
                    new ShortConverter(),
                    new IntegerConverter(),
                    new LongConverter(),
                    new FloatConverter(),
                    new DoubleConverter(),
                    new CharacterConverter(),
                    new StringConverter(),
                    new BooleanConverter(),

                    new ListConverter(),
                    new SetConverter(),
                    new MapConverter(),
                    new ImmutableListConverter(),
                    new ImmutableSetConverter(),
                    new ImmutableMapConverter()
            );
        }

        @Override
        public Predicate<Mutable> mutableFilter() {
            return null;
        }

        @Override
        public Predicate<Setable<Mutable, ?>> setableFilter() {
            return null;
        }

        @Override
        public MutableClass getMutableClass(Mutable s) {
            return null;
        }
    }
}
