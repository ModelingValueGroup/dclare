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
import java.util.function.Consumer;

public class DclareConfig {
    private static final int MAX_TOTAL_NR_OF_CHANGES_DEFAULT = 10000;
    private static final int MAX_NR_OF_CHANGES_DEFAULT       = 200;
    private static final int MAX_NR_OF_OBSERVED_DEFAULT      = 1000;
    private static final int MAX_NR_OF_OBSERVERS_DEFAULT     = 1000;
    private static final int MAX_IN_IN_QUEUE_DEFAULT         = 100;
    private static final int MAX_NR_OF_HISTORY_DEFAULT       = 64;

    //============================================================================
    private static final boolean DEV_MODE                = Boolean.getBoolean("DEV_MODE");
    private static final boolean CHECK_ORPHAN_STATE      = Boolean.getBoolean("CHECK_ORPHAN_STATE");
    private static final boolean RUN_SEQUENTIAL          = Boolean.getBoolean("RUN_SEQUENTIAL");
    private static final boolean TRACE_UNIVERSE          = Boolean.getBoolean("TRACE_UNIVERSE");
    private static final boolean TRACE_MUTABLE           = Boolean.getBoolean("TRACE_MUTABLE");
    private static final boolean TRACE_MATCHING          = Boolean.getBoolean("TRACE_MATCHING");
    private static final boolean TRACE_ACTIONS           = Boolean.getBoolean("TRACE_ACTIONS");
    private static final int     MAX_TOTAL_NR_OF_CHANGES = Integer.getInteger("MAX_TOTAL_NR_OF_CHANGES", MAX_TOTAL_NR_OF_CHANGES_DEFAULT);
    private static final int     MAX_NR_OF_CHANGES       = Integer.getInteger("MAX_NR_OF_CHANGES", MAX_NR_OF_CHANGES_DEFAULT);
    private static final int     MAX_NR_OF_OBSERVED      = Integer.getInteger("MAX_NR_OF_OBSERVED", MAX_NR_OF_OBSERVED_DEFAULT);
    private static final int     MAX_NR_OF_OBSERVERS     = Integer.getInteger("MAX_NR_OF_OBSERVERS", MAX_NR_OF_OBSERVERS_DEFAULT);
    private static final int     MAX_IN_IN_QUEUE         = Integer.getInteger("MAX_IN_IN_QUEUE", MAX_IN_IN_QUEUE_DEFAULT);
    private static final int     MAX_NR_OF_HISTORY       = Integer.getInteger("MAX_NR_OF_HISTORY", MAX_NR_OF_HISTORY_DEFAULT) + 3;

    //============================================================================
    private final State                         start;
    private final Consumer<UniverseTransaction> cycle;
    private final boolean                       devMode;
    private final boolean                       checkOrphanState;
    private final boolean                       runSequential;
    private final boolean                       traceUniverse;
    private final boolean                       traceMutable;
    private final boolean                       traceMatching;
    private final boolean                       traceActions;
    private final int                           maxInInQueue;
    private final int                           maxTotalNrOfChanges;
    private final int                           maxNrOfChanges;
    private final int                           maxNrOfObserved;
    private final int                           maxNrOfObservers;
    private final int                           maxNrOfHistory;

    //============================================================================
    public DclareConfig() {
        this.start               = null;
        this.cycle               = null;
        this.devMode             = DEV_MODE;
        this.checkOrphanState    = CHECK_ORPHAN_STATE;
        this.runSequential       = RUN_SEQUENTIAL;
        this.traceUniverse       = TRACE_UNIVERSE;
        this.traceMutable        = TRACE_MUTABLE;
        this.traceMatching       = TRACE_MATCHING;
        this.traceActions        = TRACE_ACTIONS;
        this.maxInInQueue        = MAX_IN_IN_QUEUE;
        this.maxTotalNrOfChanges = MAX_TOTAL_NR_OF_CHANGES;
        this.maxNrOfChanges      = MAX_NR_OF_CHANGES;
        this.maxNrOfObserved     = MAX_NR_OF_OBSERVED;
        this.maxNrOfObservers    = MAX_NR_OF_OBSERVERS;
        this.maxNrOfHistory      = MAX_NR_OF_HISTORY;
    }

    protected DclareConfig(State start, Consumer<UniverseTransaction> cycle, boolean devMode, boolean checkOrphanState, boolean runSequential, boolean traceUniverse, boolean traceMutable, boolean traceMatching, boolean traceActions, int maxInInQueue, int maxTotalNrOfChanges, int maxNrOfChanges, int maxNrOfObserved, int maxNrOfObservers, int maxNrOfHistory) {
        this.start               = start;
        this.cycle               = cycle;
        this.devMode             = devMode;
        this.checkOrphanState    = checkOrphanState;
        this.runSequential       = runSequential;
        this.traceUniverse       = traceUniverse;
        this.traceMutable        = traceMutable;
        this.traceMatching       = traceMatching;
        this.traceActions        = traceActions;
        this.maxInInQueue        = maxInInQueue;
        this.maxTotalNrOfChanges = maxTotalNrOfChanges;
        this.maxNrOfChanges      = maxNrOfChanges;
        this.maxNrOfObserved     = maxNrOfObserved;
        this.maxNrOfObservers    = maxNrOfObservers;
        this.maxNrOfHistory      = maxNrOfHistory;
    }

    protected DclareConfig create(State start, Consumer<UniverseTransaction> cycle, boolean devMode, boolean checkOrphanState, boolean runSequential, boolean traceUniverse, boolean traceMutable, boolean traceMatching, boolean traceActions, int maxInInQueue, int maxTotalNrOfChanges, int maxNrOfChanges, int maxNrOfObserved, int maxNrOfObservers, int maxNrOfHistory) {
        return new DclareConfig(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    //============================================================================
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DclareConfig that = (DclareConfig) o;
        return devMode == that.devMode && checkOrphanState == that.checkOrphanState && runSequential == that.runSequential && traceUniverse == that.traceUniverse && traceMutable == that.traceMutable && traceMatching == that.traceMatching && traceActions == that.traceActions && maxInInQueue == that.maxInInQueue && maxTotalNrOfChanges == that.maxTotalNrOfChanges && maxNrOfChanges == that.maxNrOfChanges && maxNrOfObserved == that.maxNrOfObserved && maxNrOfObservers == that.maxNrOfObservers && maxNrOfHistory == that.maxNrOfHistory && Objects.equals(start, that.start) && Objects.equals(cycle, that.cycle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    //============================================================================
    // All these with***() methods are designed to give the JIT compiler a pleasant time.
    // The fields are all final to improve inlineability.
    // All the with***() methods themselves are probably not very efficient but that does not matter because they are not used frequently.
    //
    public DclareConfig withStart(State start) {
        return create(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withCycle(Consumer<UniverseTransaction> cycle) {
        return create(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withDevMode(boolean devMode) {
        return create(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withCheckOrphanState(boolean checkOrphanState) {
        return create(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withRunSequential(boolean runSequential) {
        return create(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withTraceUniverse(boolean traceUniverse) {
        return create(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withTraceMutable(boolean traceMutable) {
        return create(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withTraceMatching(boolean traceMatching) {
        return create(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withTraceActions(boolean traceActions) {
        return create(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withMaxInInQueue(int maxInInQueue) {
        return create(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withMaxTotalNrOfChanges(int maxTotalNrOfChanges) {
        return create(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withMaxNrOfChanges(int maxNrOfChanges) {
        return create(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withMaxNrOfObserved(int maxNrOfObserved) {
        return create(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withMaxNrOfObservers(int maxNrOfObservers) {
        return create(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withMaxNrOfHistory(int maxNrOfHistory) {
        return create(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    //============================================================================
    public State getStart() {
        return start;
    }

    public Consumer<UniverseTransaction> getCycle() {
        return cycle;
    }

    public boolean isDevMode() {
        return devMode;
    }

    public boolean isCheckOrphanState() {
        return checkOrphanState;
    }

    public boolean isRunSequential() {
        return runSequential;
    }

    public boolean isTraceUniverse() {
        return traceUniverse;
    }

    public boolean isTraceMutable() {
        return traceMutable;
    }

    public boolean isTraceMatching() {
        return traceMatching;
    }

    public boolean isTraceActions() {
        return traceActions;
    }

    public int getMaxInInQueue() {
        return maxInInQueue;
    }

    public int getMaxTotalNrOfChanges() {
        return maxTotalNrOfChanges;
    }

    public int getMaxNrOfChanges() {
        return maxNrOfChanges;
    }

    public int getMaxNrOfObserved() {
        return maxNrOfObserved;
    }

    public int getMaxNrOfObservers() {
        return maxNrOfObservers;
    }

    public int getMaxNrOfHistory() {
        return maxNrOfHistory;
    }
}