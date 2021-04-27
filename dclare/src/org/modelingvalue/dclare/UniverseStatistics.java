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

package org.modelingvalue.dclare;

import java.util.Objects;

@SuppressWarnings("unused")
public class UniverseStatistics {
    private final UniverseTransaction tx;
    private final boolean             devMode;
    private final int                 maxInInQueue;
    private final int                 maxTotalNrOfChanges;
    private final int                 maxNrOfChanges;
    private final int                 maxNrOfObserved;
    private final int                 maxNrOfObservers;
    private final int                 maxNrOfHistory;
    //
    private boolean                   debugging;
    private int                       totalChanges;
    private long                      runCount;
    private long                      forwardCount;
    private long                      totalChangesEver;

    public UniverseStatistics(UniverseTransaction tx, int maxInInQueue, boolean devMode, int maxTotalNrOfChanges, int maxNrOfChanges, int maxNrOfObserved, int maxNrOfObservers, int maxNrOfHistory) {
        this.tx = tx;
        this.devMode = devMode;
        this.maxInInQueue = maxInInQueue;
        this.maxTotalNrOfChanges = devMode ? maxTotalNrOfChanges : Integer.MAX_VALUE;
        this.maxNrOfChanges = devMode ? maxNrOfChanges : Integer.MAX_VALUE;
        this.maxNrOfObserved = devMode ? maxNrOfObserved : Integer.MAX_VALUE;
        this.maxNrOfObservers = devMode ? maxNrOfObservers : Integer.MAX_VALUE;
        this.maxNrOfHistory = maxNrOfHistory;
    }

    public UniverseStatistics(UniverseStatistics o) {
        this.tx = o.tx;
        this.devMode = o.devMode;
        this.maxInInQueue = o.maxInInQueue;
        this.maxTotalNrOfChanges = o.maxTotalNrOfChanges;
        this.maxNrOfChanges = o.maxNrOfChanges;
        this.maxNrOfObserved = o.maxNrOfObserved;
        this.maxNrOfObservers = o.maxNrOfObservers;
        this.maxNrOfHistory = o.maxNrOfHistory;
        this.debugging = o.debugging;
        this.totalChanges = o.totalChanges;
        this.runCount = o.runCount;
        this.forwardCount = o.forwardCount;
        this.totalChangesEver = o.totalChangesEver;
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
        return maxInInQueue;
    }

    public int maxTotalNrOfChanges() {
        return maxTotalNrOfChanges;
    }

    public int maxNrOfChanges() {
        return maxNrOfChanges;
    }

    public int maxNrOfObserved() {
        return maxNrOfObserved;
    }

    public int maxNrOfObservers() {
        return maxNrOfObservers;
    }

    public int maxNrOfHistory() {
        return maxNrOfHistory;
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
        if (totalChanges > maxTotalNrOfChanges) {
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

    @Override
    public String toString() {
        return "UniverseStats:\n" + "  debugging         = " + debugging + "\n" + "  runCount          = " + runCount + "\n" + "  forwardCount       = " + forwardCount + "\n" + "  totalChanges      = " + totalChanges + "\n" + "  totalChangesEver  = " + totalChangesEver;
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
        return devMode == that.devMode && maxInInQueue == that.maxInInQueue && maxTotalNrOfChanges == that.maxTotalNrOfChanges && maxNrOfChanges == that.maxNrOfChanges && maxNrOfObserved == that.maxNrOfObserved && maxNrOfObservers == that.maxNrOfObservers && //
                maxNrOfHistory == that.maxNrOfHistory && debugging == that.debugging && totalChanges == that.totalChanges && runCount == that.runCount && forwardCount == that.forwardCount && totalChangesEver == that.totalChangesEver;
    }

    @Override
    public int hashCode() {
        return Objects.hash(devMode, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory, debugging, totalChanges, runCount, forwardCount, totalChangesEver);
    }
}
