//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2024 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.modelingvalue.dclare.sync.JsonIC.fromJson;
import static org.modelingvalue.dclare.sync.JsonIC.toJson;

import java.util.Arrays;

import org.junit.jupiter.api.RepeatedTest;
import org.modelingvalue.collections.*;

public class JsonICTests {
    @RepeatedTest(1)
    public void listsToJson() {
        assertEquals("[]", toJson(List.of()));
        assertEquals("[1,2,3]", toJson(List.of(1, 2, 3)));
        assertEquals("[1,2,3,[1,2,3,[1,2,3]]]", toJson(List.of(1L, 2L, 3L, List.of(1L, 2L, 3L, List.of(1L, 2L, 3L)))));
        assertEquals("[1,2,3]", toJson(Arrays.asList(1, 2, 3)));
        assertEquals("[1,\"a\",3,12.6,\"q\"]", toJson(List.of(1, "a", 3L, 12.6, 'q')));
    }

    @RepeatedTest(1)
    public void listsFromJson() {
        assertEquals("[]", toJson(List.of()));
        assertEquals("[1,2,3]", toJson(List.of(1, 2, 3)));
        assertEquals("[1,2,3,[1,2,3,[1,2,3]]]", toJson(List.of(1L, 2L, 3L, List.of(1L, 2L, 3L, List.of(1L, 2L, 3L)))));
        assertEquals("[1,2,3]", toJson(Arrays.asList(1, 2, 3)));
        assertEquals("[1,\"a\",3,12.6,\"q\"]", toJson(List.of(1, "a", 3L, 12.6, 'q')));
    }

    @RepeatedTest(1)
    public void arraysFromJson() {
        assertThrows(IllegalArgumentException.class, () -> fromJson("[1,2 3,[1,2,3,[1,2,3]]]"));
        assertThrows(IllegalArgumentException.class, () -> fromJson("[1,2 3,[1,2,3,[1,2,3]],]"));

        assertEquals(List.of(1L, 2L, 3L, List.of(1L, 2L, 3L, List.of(1L, 2L, 3L))), fromJson("[1,2,3,[1,2,3,[1,2,3]]]"));
        assertEquals(List.of(1L, 2L, 3L, List.of(1L, 2L, 3L, List.of(1L, 2L, 3L))), fromJson("   [\n  1,2,\n  3,[\r\t\n    1,\n    2,\n    3,\n    [\n      1,\n      2,\n      3\n    ]\n  ]\n]\n\n\n"));
    }

    @RepeatedTest(1)
    public void mapsFromJson() {
        assertThrows(IllegalArgumentException.class, () -> fromJson("{\"a\":1,\"b\":2,}"));
        assertThrows(IllegalArgumentException.class, () -> fromJson("{\"a\":1,\"b\":2,\"x\"}"));
        assertThrows(IllegalArgumentException.class, () -> fromJson("{\"a\":1,\"b\":2,\"x\";}"));
        assertThrows(IllegalArgumentException.class, () -> fromJson("{\"a\":1,\"b\":2;}"));
        assertEquals(Map.of(Entry.of("a", 1L), Entry.of("b", 2L)), fromJson("{\"a\":1,\"b\":2}"));
    }
}
