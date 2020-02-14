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

package org.modelingvalue.dclare;

@SuppressWarnings("unused")
public class UniverseStatistics {
    private final UniverseTransaction tx;
    private final int                 maxInInQueue;
    private final int                 maxTotalNrOfChanges;
    private final int                 maxNrOfChanges;
    private final int                 maxNrOfObserved;
    private final int                 maxNrOfObservers;
    private final int                 maxNrOfHistory;
    //
    private       boolean             debugging;
    private       int                 totalChanges;
    private       long                runCount;
    private       long                totalChangesEver;


    UniverseStatistics(UniverseTransaction tx, int maxInInQueue, int maxTotalNrOfChanges, int maxNrOfChanges, int maxNrOfObserved, int maxNrOfObservers, int maxNrOfHistory) {
        this.tx = tx;
        this.maxInInQueue = maxInInQueue;
        this.maxTotalNrOfChanges = maxTotalNrOfChanges;
        this.maxNrOfChanges = maxNrOfChanges;
        this.maxNrOfObserved = maxNrOfObserved;
        this.maxNrOfObservers = maxNrOfObservers;
        this.maxNrOfHistory = maxNrOfHistory;
    }

    void completeRun() {
        int n = totalChanges;
        totalChanges = 0;
        totalChangesEver += n;
        runCount++;
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
        return "UniverseStats:\n" +
                "  debugging         = " + debugging + "\n" +
                "  runCount          = " + runCount + "\n" +
                "  totalChanges      = " + totalChanges + "\n" +
                "  totalChangesEver  = " + totalChangesEver;
    }
}
