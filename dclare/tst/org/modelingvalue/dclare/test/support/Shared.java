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

import org.modelingvalue.collections.util.ContextThread;
import org.modelingvalue.collections.util.ContextThread.ContextPool;
import org.modelingvalue.dclare.State;
import org.modelingvalue.dclare.UniverseTransaction;

public class Shared {

    public static final boolean     PRINT_STATE = true;
    public static final ContextPool THE_POOL    = ContextThread.createPool();

    public static void printState(UniverseTransaction universeTransaction, State result, String... extraLines) {
        if (PRINT_STATE) {
            int num = result == null ? -1 : result.getObjects(TestObject.class).size();

            System.err.println("**** stats *********************************************************");
            System.err.println(universeTransaction.stats());
            if (0 <= num) {
                System.err.println("**** num DObjects **************************************************");
                System.err.println(num);
                if (num < 100) {
                    System.err.println("**** end-state *****************************************************");
                    System.err.println(result.asString());
                }
            }
            if (extraLines.length > 0) {
                System.err.println("********************************************************************");
                for (String line : extraLines) {
                    System.err.println(line);
                }
            }
            System.err.println("********************************************************************");
        }
    }

    public static Throwable getCause(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}
