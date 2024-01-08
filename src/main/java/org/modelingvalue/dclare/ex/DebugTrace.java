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

package org.modelingvalue.dclare.ex;

import org.modelingvalue.dclare.Feature;
import org.modelingvalue.dclare.ObserverTrace;

@SuppressWarnings("unused")
public final class DebugTrace extends ConsistencyError {

    private static final long   serialVersionUID = 8369169825319766128L;

    private final ObserverTrace trace;

    public DebugTrace(Object object, Feature feature, ObserverTrace trace) {
        super(object, feature, Integer.MAX_VALUE, "Run of " + object + "." + feature + ", at " + trace.time());
        this.trace = trace;
    }

    public ObserverTrace trace() {
        return trace;
    }

    @Override
    public int compareTo(ConsistencyError o) {
        return o instanceof DebugTrace ? trace.time().compareTo(((DebugTrace) o).trace.time()) : super.compareTo(o);
    }

}
