//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2022 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

package org.modelingvalue.dclare;

import java.util.Objects;

@SuppressWarnings("unused")
public class UniverseStatistics {
    private final UniverseTransaction tx;
    //
    private boolean                   debugging;
    private int                       totalChanges;
    private long                      runCount;
    private long                      forwardCount;
    private long                      totalChangesEver;

    public UniverseStatistics(UniverseTransaction tx) {
        this.tx = tx;
    }

    private UniverseStatistics(UniverseStatistics o) {
        this.tx = o.tx;
        this.debugging = o.debugging;
        this.totalChanges = o.totalChanges;
        this.runCount = o.runCount;
        this.forwardCount = o.forwardCount;
        this.totalChangesEver = o.totalChangesEver;
    }

    @Override
    public UniverseStatistics clone() {
        return new UniverseStatistics(this);
    }

    void completeRun() {
        int n = totalChanges;
        totalChanges = 0;
        totalChangesEver += n;
        runCount++;
    }

    void completeForward() {
        forwardCount++;
    }

    public int maxInInQueue() {
        return tx.getConfig().getMaxInInQueue();
    }

    public int maxTotalNrOfChanges() {
        DclareConfig config = tx.getConfig();
        return config.isDevMode() ? config.getMaxTotalNrOfChanges() : Integer.MAX_VALUE;
    }

    public int maxNrOfChanges() {
        DclareConfig config = tx.getConfig();
        return config.isDevMode() ? tx.getConfig().getMaxNrOfChanges() : Integer.MAX_VALUE;
    }

    public int maxNrOfObserved() {
        DclareConfig config = tx.getConfig();
        return config.isDevMode() ? tx.getConfig().getMaxNrOfObserved() : Integer.MAX_VALUE;
    }

    public int maxNrOfObservers() {
        DclareConfig config = tx.getConfig();
        return config.isDevMode() ? tx.getConfig().getMaxNrOfObservers() : Integer.MAX_VALUE;
    }

    public int maxNrOfHistory() {
        return tx.getConfig().getMaxNrOfHistory();
    }

    public boolean debugging() {
        return debugging;
    }

    public void setDebugging(boolean on) {
        debugging = on;
    }

    public long runCount() {
        return runCount;
    }

    public long forwardCount() {
        return forwardCount;
    }

    public int bumpAndGetTotalChanges() {
        if (totalChanges > maxTotalNrOfChanges()) {
            synchronized (tx) {
                return totalChanges++;
            }
        } else {
            return totalChanges++;
        }
    }

    public int totalChanges() {
        tx.throwIfError();
        return totalChanges;
    }

    public long totalChangesEver() {
        tx.throwIfError();
        return totalChangesEver;
    }

    public UniverseTransaction universeTransaction() {
        return tx;
    }

    @Override
    public String toString() {
        return "UniverseStats:\n" //
                + "    runCount         = " + runCount + "\n" //
                + "    forwardCount     = " + forwardCount + "\n" //
                + "    totalChanges     = " + totalChanges + "\n" //
                + "    totalChangesEver = " + totalChangesEver//
                + "    debugging        = " + debugging; //
    }

    public String shortString() {
        return String.format("[run=%6d forward=%6d total=%6d ever=%6d debug=%-5s]", runCount, forwardCount, totalChanges, totalChangesEver, debugging);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UniverseStatistics that = (UniverseStatistics) o;
        return tx == that.tx && debugging == that.debugging && totalChanges == that.totalChanges && runCount == that.runCount && forwardCount == that.forwardCount && totalChangesEver == that.totalChangesEver;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tx, debugging, totalChanges, runCount, forwardCount, totalChangesEver);
    }
}
