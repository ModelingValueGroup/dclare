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

package org.modelingvalue.dclare.sync;

public class Util {
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
            s = i + 1 == num ? null : split[1].substring(length + 1);
        }
        return parts;
    }
}
