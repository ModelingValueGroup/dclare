//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2020 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.*;
import java.util.*;

import org.junit.jupiter.api.*;
import org.modelingvalue.dclare.sync.json.*;

public class JsonRandomTests {
    @RepeatedTest(1)
    public void oneBigObjectToJson() {
        Object original = randomObject(12);

        long   t0    = System.currentTimeMillis();
        String json1 = Json.toJson(original);
        Object copy1 = Json.fromJson(json1);
        String json2 = Json.toJson(copy1);
        Object copy2 = Json.fromJson(json2);
        assertEquals(copy1, copy2);
        assertEquals(json1, json2);
        int chars = json1.length();

        System.err.println("handled " + (chars / 1024) + " kb json in 1 run in " + (System.currentTimeMillis() - t0) + " ms");
    }

    @RepeatedTest(1)
    public void manySmallObjectsToJson() {
        long t0    = System.currentTimeMillis();
        int  chars = 0;
        int  i     = 0;
        while (System.currentTimeMillis() < t0 + 2_000) {

            Object original = randomObject(5);
            String json1    = Json.toJson(original);
            Object copy1    = Json.fromJson(json1);
            String json2    = Json.toJson(copy1);
            Object copy2    = Json.fromJson(json2);

            assertEquals(copy1, copy2);
            assertEquals(json1, json2);
            chars += json1.length();
            i++;
        }
        System.err.println("handled " + (chars / 1024) + " kb json in " + i + " runs in " + (System.currentTimeMillis() - t0) + " ms");
    }

    private static final Random random = new Random();

    public static Object randomObject(int depth) {
        if (((double) depth) < random.nextDouble()) {
            switch (random.nextInt(9)) {
            case 0: // boolean
                return random.nextBoolean();
            case 1: // char
                return (char) random.nextInt(Character.MAX_VALUE + 1);
            case 2: // byte
                return (byte) random.nextInt(Byte.MAX_VALUE + 1);
            case 3: // short
                return (short) random.nextInt(Short.MAX_VALUE + 1);
            case 4: // int
                return random.nextInt();
            case 5: // long
                return random.nextLong();
            case 6: // float
                return random.nextFloat();
            case 7:  // double
                return random.nextDouble();
            case 8: // String
                return randomString();
            }
        } else if (random.nextBoolean()) {
            Map<String, Object> m = new HashMap<>();
            for (int i = 0; i < random.nextInt(10); i++) {
                m.put(randomString(), randomObject(depth - 1));
            }
            return m;
        } else {
            List<Object> l = new ArrayList<>();
            for (int i = 0; i < random.nextInt(10); i++) {
                l.add(randomObject(depth - 1));
            }
            return l;
        }
        throw new Error("huh?");
    }

    private static String randomString() {
        byte[] array = new byte[random.nextInt(4)];
        random.nextBytes(array);
        return new String(array, StandardCharsets.UTF_8);
    }

}
