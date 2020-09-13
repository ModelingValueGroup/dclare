//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2019 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import java.util.*;

import org.junit.jupiter.api.*;
import org.modelingvalue.dclare.sync.converter.*;

public class JsonTests {
    @Test
    public void jsonTest() {
        ConvertJson conv = new ConvertJson();

        Map<String, Map<String, Object>> o0 =
                Map.of(
                        "john", Map.of(
                                "111", "y1",
                                "222", "y2"
                        ),
                        "paul", Map.of(
                                "333", "y3",
                                "444", "y4",
                                "555", "y5"
                        ),
                        "george", Map.of(
                                "666", "y6",
                                "777", "y7",
                                "888", "y8"
                        ),
                        "ringo", Map.of(
                                "999", "y9"
                        )
                );

        Map<String, Map<String, Object>> o2 = conv.convertBackward(conv.convertForward(o0));
        Map<String, Map<String, Object>> o4 = conv.convertBackward(conv.convertForward(o2));
        Map<String, Map<String, Object>> o6 = conv.convertBackward(conv.convertForward(o4));

        Assertions.assertEquals(o0, o2);
        Assertions.assertEquals(o2, o4);
        Assertions.assertEquals(o4, o6);

        System.err.println("json= " + conv.convertForward(o0));
    }
}
