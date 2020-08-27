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

package org.modelingvalue.dclare.test.support;

import static org.modelingvalue.collections.util.TraceTimer.*;

import java.util.function.Predicate;

import org.modelingvalue.dclare.Setable;
import org.modelingvalue.dclare.UniverseTransaction;
import org.modelingvalue.dclare.delta.DeltaAdaptor;

@SuppressWarnings("rawtypes")
public class TestDeltaAdaptor extends DeltaAdaptor {
    public TestDeltaAdaptor(String name, UniverseTransaction tx, Predicate<Object> objectFilter, Predicate<Setable> setableFilter) {
        super(name, tx, objectFilter, setableFilter);
    }

    protected AdaptorThread makeThread(String name) {
        return new TestAdaptorThread(name);
    }

    public boolean isBusy() {
        return ((TestAdaptorThread) adaptorThread).isBusy() || !deltaQueue.isEmpty() || tx.isHandling() || tx.numInQueue() != 0;
    }

    public String isBusyExplaining() {
        StringBuilder b = new StringBuilder();
        if (((TestAdaptorThread) adaptorThread).isBusy()) {
            b.append(" adaptorThread busy");
        }
        if (!deltaQueue.isEmpty()) {
            b.append(" deltaQueue not empty");
        }
        if (tx.isHandling()) {
            b.append(" tx is handling");
        }
        if (tx.numInQueue() != 0) {
            b.append(" tx queue not empty");
        }
        return b.toString();
    }

    private static class TestAdaptorThread extends AdaptorThread {
        private boolean busy;

        public TestAdaptorThread(String name) {
            super(name);
        }

        public boolean isBusy() {
            return !runnableQueue.isEmpty() || busy;
        }

        protected Runnable next() throws InterruptedException {
            traceLog("***DeltaAdaptor %s: wait for Runnable...", getName());
            busy = false;
            Runnable r = super.next();
            // TODO: there is a small period that the queue could be empty and that 'handling' is false but we still have work todo...
            busy = true;
            traceLog("***DeltaAdaptor %s: got Runnable...", getName());
            return r;
        }
    }
}
