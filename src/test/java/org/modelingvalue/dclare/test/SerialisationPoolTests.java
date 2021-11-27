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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.Set;
import org.modelingvalue.dclare.Mutable;
import org.modelingvalue.dclare.MutableClass;
import org.modelingvalue.dclare.Setable;
import org.modelingvalue.dclare.sync.Converters;
import org.modelingvalue.dclare.sync.SerializationHelperWithPool;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SerialisationPoolTests {
    private static final String BIG_INTEGER_VALUE = "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890";
    private static final String BIG_DECIMAL_VALUE = "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890.1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890";

    static {
        System.setProperty("TRACE_SERIALIZATION", "true");
    }

    TestSerializationHelperWithPool h = new TestSerializationHelperWithPool();

    @Test
    public void absentTests() {
        assertEquals("[DESERIALIZE] no deserialisation possible for null or empty string",
                assertThrows(IllegalArgumentException.class, () -> h.deserializeValue(null, null)).getMessage());
        assertEquals("[DESERIALIZE] no deserialisation possible for null or empty string",
                assertThrows(IllegalArgumentException.class, () -> h.deserializeValue(null, "")).getMessage());
        assertEquals("[DESERIALIZE] missing converter for 'just some string' in \"just some string\" (no deserialisation possible)",
                assertThrows(IllegalArgumentException.class, () -> h.deserializeValue(null, "just some string")).getMessage());
        assertEquals("[DESERIALIZE] missing converter for 'unknown' in \"unknown:#$%^&\" (no deserialisation possible)",
                assertThrows(IllegalArgumentException.class, () -> h.deserializeValue(null, "unknown:#$%^&")).getMessage());
        assertEquals("[  SERIALIZE] null                     : no converter available for class int[]\n",
                assertThrows(IllegalArgumentException.class, () -> h.serializeValue(null, new int[3])).getMessage());
    }

    @Test
    public void stringTests() {
        assertEquals("s:xyzzy", h.serializeValue(null, "xyzzy"));
        assertEquals("plugh", h.deserializeValue(null, "s:plugh"));

        assertEquals("s:x:y:z:z:y", h.serializeValue(null, "x:y:z:z:y"));
        assertEquals("::p:l:u:g:h::", h.deserializeValue(null, "s:::p:l:u:g:h::"));
    }

    @Test
    public void bigIntegerTests() {
        assertEquals("II:" + BIG_INTEGER_VALUE, h.serializeValue(null, new BigInteger(BIG_INTEGER_VALUE)));
        assertEquals(new BigInteger(BIG_INTEGER_VALUE), h.deserializeValue(null, "II:" + BIG_INTEGER_VALUE));
    }

    @Test
    public void bigDecimalTests() {
        assertEquals("DD:" + BIG_DECIMAL_VALUE, h.serializeValue(null, new BigDecimal(BIG_DECIMAL_VALUE)));
        assertEquals(new BigDecimal(BIG_DECIMAL_VALUE), h.deserializeValue(null, "DD:" + BIG_DECIMAL_VALUE));
    }

    @Test
    public void listTests() {
        assertEquals("List:[\"s:a\",\"s:b\",\"s:c\"]", h.serializeValue(null, java.util.List.of("a", "b", "c")));
        assertEquals(java.util.List.of("x", "y", "z"), h.deserializeValue(null, "List:[\"s:x\",\"s:y\",\"s:z\"]"));

        assertEquals("immutableList:[\"s:a\",\"s:b\",\"s:c\"]", h.serializeValue(null, List.of("a", "b", "c")));
        assertEquals(List.of("x", "y", "z"), h.deserializeValue(null, "immutableList:[\"s:x\",\"s:y\",\"s:z\"]"));
    }

    @Test
    public void setTests() {
        Assertions.assertTrue(h.serializeValue(null, java.util.Set.of("a", "b", "c")).toString().matches("Set:\\[\"s:([abc])\",\"s:([abc])\",\"s:([abc])\"]"));
        assertEquals(java.util.Set.of("x", "y", "z"), h.deserializeValue(null, "Set:[\"s:x\",\"s:y\",\"s:z\"]"));

        Assertions.assertTrue(h.serializeValue(null, Set.of("a", "b", "c")).toString().matches("immutableSet:\\[\"s:([abc])\",\"s:([abc])\",\"s:([abc])\"]"));
        assertEquals(Set.of("x", "y", "z"), h.deserializeValue(null, "immutableSet:[\"s:x\",\"s:y\",\"s:z\"]"));
    }

    @Test
    public void mapTests() {
        assertEquals("Map:{\"s:a\":\"I:21\",\"s:b\":\"I:69\",\"s:c\":\"I:99\"}", h.serializeValue(null, java.util.Map.of("a", 21, "b", 69, "c", 99)));
        assertEquals(java.util.Map.of(21, "x", 43, "y", 12, "z"), h.deserializeValue(null, "Map:{\"I:21\":\"s:x\",\"I:43\":\"s:y\",\"I:12\":\"s:z\"}"));

        assertEquals("immutableMap:{\"C:c\":\"J:88\",\"s:@\":\"Z:true\"}", h.serializeValue(null, Map.of(Entry.of('c', 88L), Entry.of("@", true))));
        assertEquals(Map.of(Entry.of(false, "no"), Entry.of(true, "yes")), h.deserializeValue(null, "immutableMap:{\"Z:true\":\"s:yes\",\"Z:false\":\"s:no\"}"));
    }


    private static class TestSerializationHelperWithPool extends SerializationHelperWithPool<MutableClass, Mutable, Setable<Mutable, ?>> {
        public TestSerializationHelperWithPool() {
            super(Converters.ALL);
        }

        @Override
        public Predicate<Mutable> mutableFilter() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Predicate<Setable<Mutable, ?>> setableFilter() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MutableClass getMutableClass(Mutable s) {
            throw new UnsupportedOperationException();
        }
    }
}
