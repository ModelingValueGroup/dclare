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

package org.modelingvalue.dclare.sync.json;

public class Json {
    public static String toJson(Object o) {
        return new ToJson().toJson(o);
    }

    public static Object fromJson(String s) {
        return new FromJson().fromJson(s);
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static String terse(String json) {
        return format(json, null, null);
    }

    public static String pretty(String json) {
        return pretty(json, "  ", "\n");
    }

    public static String pretty(String json, String indent, String eol) {
        return format(terse(json), indent, eol);
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////
    private static String format(String json, String indentString, String eolString) {
        StringBuilder b         = new StringBuilder();
        int           indent    = 0;
        boolean       inQuote   = false;
        char[]        charArray = json.toCharArray();
        for (int i = 0; i < charArray.length; i++) {
            char c = charArray[i];
            if (inQuote) {
                switch (c) {
                case '\\':
                    b.append(c);
                    c = charArray[++i];
                    b.append(c);
                    break;
                case '"':
                    b.append(c);
                    inQuote = false;
                    break;
                default:
                    b.append(c);
                }
            } else {
                switch (c) {
                case '"':
                    b.append(c);
                    inQuote = true;
                    break;
                case '{':
                case '[':
                    b.append(c);
                    ++indent;
                    appendIndent(b, indentString, eolString, indent);
                    break;
                case '}':
                case ']':
                    --indent;
                    appendIndent(b, indentString, eolString, indent);
                    b.append(c);
                    break;
                case ',':
                    b.append(c);
                    appendIndent(b, indentString, eolString, indent);
                    break;
                case ':':
                    b.append(": ");
                    break;
                case ' ':
                case '\r':
                case '\n':
                case '\t':
                    break;
                default:
                    b.append(c);
                }
            }
        }
        return b.toString();
    }

    private static void appendIndent(StringBuilder b, String indentString, String eolString, int indent) {
        if (eolString != null) {
            b.append(eolString);
        }
        if (indentString != null) {
            b.append(indentString.repeat(Math.max(0, indent)));
        }
    }
}
