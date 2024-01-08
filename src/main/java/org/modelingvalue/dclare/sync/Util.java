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

package org.modelingvalue.dclare.sync;

import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Map;

import java.math.BigDecimal;
import java.math.BigInteger;

@SuppressWarnings("unused")
public class Util {
    public static final Map<Class<?>, Class<?>> UNBOX_MAP  = Map.of(    //
            Entry.of(Boolean.class, boolean.class),     //
            Entry.of(Byte.class, byte.class),           //
            Entry.of(Character.class, char.class),      //
            Entry.of(Double.class, double.class),       //
            Entry.of(Float.class, float.class),         //
            Entry.of(Integer.class, int.class),         //
            Entry.of(Long.class, long.class),           //
            Entry.of(Short.class, short.class)          //
    );
    public static final Map<Class<?>, Class<?>> BOX_MAP    = Map.of( //
            Entry.of(boolean.class, Boolean.class),     //
            Entry.of(byte.class, Byte.class),           //
            Entry.of(char.class, Character.class),      //
            Entry.of(double.class, Double.class),       //
            Entry.of(float.class, Float.class),         //
            Entry.of(int.class, Integer.class),         //
            Entry.of(long.class, Long.class),           //
            Entry.of(short.class, Short.class)          //
    );
    public static final Map<Class<?>, String>   PREFIX_MAP = Map.of( //
            Entry.of(byte.class, "B"),          //
            Entry.of(Byte.class, "B"),          //
            Entry.of(char.class, "C"),          //
            Entry.of(Character.class, "C"),     //
            Entry.of(double.class, "D"),        //
            Entry.of(Double.class, "D"),        //
            Entry.of(float.class, "F"),         //
            Entry.of(Float.class, "F"),         //
            Entry.of(int.class, "I"),           //
            Entry.of(Integer.class, "I"),       //
            Entry.of(long.class, "J"),          //
            Entry.of(Long.class, "J"),          //
            Entry.of(short.class, "S"),         //
            Entry.of(Short.class, "S"),         //
            Entry.of(boolean.class, "Z"),       //
            Entry.of(Boolean.class, "Z"),       //
            Entry.of(String.class, "s"),        //
            Entry.of(BigInteger.class, "BI"),   //
            Entry.of(BigDecimal.class, "BD")    //
    );

    /////////////////////////////////////////////////////////////////////////////////
    public static String encodeWithLength(String... ss) {
        return encodeWithLength(new StringBuilder(), ss).toString();
    }

    public static StringBuilder encodeWithLength(StringBuilder b, String... ss) {
        for (String s : ss) {
            if (b.length() != 0) {
                b.append(',');
            }
            b.append(s.length()).append(':').append(s);
        }
        return b;
    }

    public static String[] decodeFromLength(String s, int num) {
        String[] parts = new String[num];
        for (int i = 0; i < num && s != null; i++) {
            String[] split  = s.split("[^0-9]", 2);
            int      length = Integer.parseInt(split[0]);
            parts[i] = split[1].substring(0, length);
            s        = i + 1 == num ? null : split[1].substring(length + 1);
        }
        return parts;
    }
}
