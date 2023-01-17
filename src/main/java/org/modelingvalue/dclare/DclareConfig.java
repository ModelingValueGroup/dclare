//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2023 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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
public class DclareConfig {
    private static final int     MAX_TOTAL_NR_OF_CHANGES_DEFAULT = 10000;
    private static final int     MAX_NR_OF_CHANGES_DEFAULT       = 200;
    private static final int     MAX_NR_OF_OBSERVED_DEFAULT      = 1000;
    private static final int     MAX_NR_OF_OBSERVERS_DEFAULT     = 1000;
    private static final int     MAX_IN_IN_QUEUE_DEFAULT         = 100;
    private static final int     MAX_NR_OF_HISTORY_DEFAULT       = 64;

    //============================================================================
    private static final boolean DEV_MODE                        = Boolean.getBoolean("DEV_MODE");
    private static final boolean CHECK_ORPHAN_STATE              = Boolean.getBoolean("CHECK_ORPHAN_STATE");
    private static final boolean RUN_SEQUENTIAL                  = Boolean.getBoolean("RUN_SEQUENTIAL");
    private static final boolean TRACE_UNIVERSE                  = Boolean.getBoolean("TRACE_UNIVERSE");
    private static final boolean TRACE_MUTABLE                   = Boolean.getBoolean("TRACE_MUTABLE");
    private static final boolean TRACE_MATCHING                  = Boolean.getBoolean("TRACE_MATCHING");
    private static final boolean TRACE_ACTIONS                   = Boolean.getBoolean("TRACE_ACTIONS");
    private static final boolean TRACE_RIPPLE_OUT                = Boolean.getBoolean("TRACE_RIPPLE_OUT");
    private static final boolean TRACE_DERIVATION                = Boolean.getBoolean("TRACE_DERIVATION");
    private static final int     MAX_TOTAL_NR_OF_CHANGES         = Integer.getInteger("MAX_TOTAL_NR_OF_CHANGES", MAX_TOTAL_NR_OF_CHANGES_DEFAULT);
    private static final int     MAX_NR_OF_CHANGES               = Integer.getInteger("MAX_NR_OF_CHANGES", MAX_NR_OF_CHANGES_DEFAULT);
    private static final int     MAX_NR_OF_OBSERVED              = Integer.getInteger("MAX_NR_OF_OBSERVED", MAX_NR_OF_OBSERVED_DEFAULT);
    private static final int     MAX_NR_OF_OBSERVERS             = Integer.getInteger("MAX_NR_OF_OBSERVERS", MAX_NR_OF_OBSERVERS_DEFAULT);
    private static final int     MAX_IN_IN_QUEUE                 = Integer.getInteger("MAX_IN_IN_QUEUE", MAX_IN_IN_QUEUE_DEFAULT);
    private static final int     MAX_NR_OF_HISTORY               = Integer.getInteger("MAX_NR_OF_HISTORY", MAX_NR_OF_HISTORY_DEFAULT) + 3;

    //============================================================================
    private final boolean        devMode;
    private final boolean        checkOrphanState;
    private final boolean        runSequential;
    private final boolean        traceUniverse;
    private final boolean        traceMutable;
    private final boolean        traceMatching;
    private final boolean        traceActions;
    private final boolean        traceRippleOut;
    private final boolean        traceDerivation;
    private final int            maxInInQueue;
    private final int            maxTotalNrOfChanges;
    private final int            maxNrOfChanges;
    private final int            maxNrOfObserved;
    private final int            maxNrOfObservers;
    private final int            maxNrOfHistory;

    //============================================================================
    public DclareConfig() {
        this.devMode = DEV_MODE;
        this.checkOrphanState = CHECK_ORPHAN_STATE;
        this.runSequential = RUN_SEQUENTIAL;
        this.traceUniverse = TRACE_UNIVERSE;
        this.traceMutable = TRACE_MUTABLE;
        this.traceMatching = TRACE_MATCHING;
        this.traceActions = TRACE_ACTIONS;
        this.traceRippleOut = TRACE_RIPPLE_OUT;
        this.traceDerivation = TRACE_DERIVATION;
        this.maxInInQueue = MAX_IN_IN_QUEUE;
        this.maxTotalNrOfChanges = MAX_TOTAL_NR_OF_CHANGES;
        this.maxNrOfChanges = MAX_NR_OF_CHANGES;
        this.maxNrOfObserved = MAX_NR_OF_OBSERVED;
        this.maxNrOfObservers = MAX_NR_OF_OBSERVERS;
        this.maxNrOfHistory = MAX_NR_OF_HISTORY;
    }

    protected DclareConfig(boolean devMode, boolean checkOrphanState, boolean runSequential, boolean traceUniverse, boolean traceMutable, boolean traceMatching, boolean traceActions, boolean traceRippleOut, boolean traceDerivation, int maxInInQueue, int maxTotalNrOfChanges, int maxNrOfChanges, int maxNrOfObserved, int maxNrOfObservers, int maxNrOfHistory) {
        this.devMode = devMode;
        this.checkOrphanState = checkOrphanState;
        this.runSequential = runSequential;
        this.traceUniverse = traceUniverse;
        this.traceMutable = traceMutable;
        this.traceMatching = traceMatching;
        this.traceActions = traceActions;
        this.traceRippleOut = traceRippleOut;
        this.traceDerivation = traceDerivation;
        this.maxInInQueue = maxInInQueue;
        this.maxTotalNrOfChanges = maxTotalNrOfChanges;
        this.maxNrOfChanges = maxNrOfChanges;
        this.maxNrOfObserved = maxNrOfObserved;
        this.maxNrOfObservers = maxNrOfObservers;
        this.maxNrOfHistory = maxNrOfHistory;
    }

    protected DclareConfig create(boolean devMode, boolean checkOrphanState, boolean runSequential, boolean traceUniverse, boolean traceMutable, boolean traceMatching, boolean traceActions, boolean traceRippleOut, boolean traceDerivation, int maxInInQueue, int maxTotalNrOfChanges, int maxNrOfChanges, int maxNrOfObserved, int maxNrOfObservers, int maxNrOfHistory) {
        return new DclareConfig(devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, traceRippleOut, traceDerivation, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
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
        return devMode == that.devMode && checkOrphanState == that.checkOrphanState && runSequential == that.runSequential && traceUniverse == that.traceUniverse && traceMutable == that.traceMutable && traceMatching == that.traceMatching && traceActions == that.traceActions && traceRippleOut == that.traceRippleOut && traceDerivation == that.traceDerivation && maxInInQueue == that.maxInInQueue && maxTotalNrOfChanges == that.maxTotalNrOfChanges && maxNrOfChanges == that.maxNrOfChanges && maxNrOfObserved == that.maxNrOfObserved && maxNrOfObservers == that.maxNrOfObservers && maxNrOfHistory == that.maxNrOfHistory;
    }

    @Override
    public int hashCode() {
        return Objects.hash(devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, traceRippleOut, traceDerivation, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    //============================================================================
    // All these with***() methods are designed to give the JIT compiler a pleasant time.
    // The fields are all final to improve inlineability.
    // All the with***() methods themselves are probably not very efficient but that does not matter because they are not used frequently.
    //

    public DclareConfig withDevMode(boolean devMode) {
        return create(devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, traceRippleOut, traceDerivation, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withCheckOrphanState(boolean checkOrphanState) {
        return create(devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, traceRippleOut, traceDerivation, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withRunSequential(boolean runSequential) {
        return create(devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, traceRippleOut, traceDerivation, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withTraceUniverse(boolean traceUniverse) {
        return create(devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, traceRippleOut, traceDerivation, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withTraceMutable(boolean traceMutable) {
        return create(devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, traceRippleOut, traceDerivation, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withTraceMatching(boolean traceMatching) {
        return create(devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, traceRippleOut, traceDerivation, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withTraceActions(boolean traceActions) {
        return create(devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, traceRippleOut, traceDerivation, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withTraceRippleOut(boolean traceRippleOut) {
        return create(devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, traceRippleOut, traceDerivation, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withTraceDerivation(boolean traceDerivation) {
        return create(devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, traceRippleOut, traceDerivation, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withMaxInInQueue(int maxInInQueue) {
        return create(devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, traceRippleOut, traceDerivation, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withMaxTotalNrOfChanges(int maxTotalNrOfChanges) {
        return create(devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, traceRippleOut, traceDerivation, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withMaxNrOfChanges(int maxNrOfChanges) {
        return create(devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, traceRippleOut, traceDerivation, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withMaxNrOfObserved(int maxNrOfObserved) {
        return create(devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, traceRippleOut, traceDerivation, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withMaxNrOfObservers(int maxNrOfObservers) {
        return create(devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, traceRippleOut, traceDerivation, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withMaxNrOfHistory(int maxNrOfHistory) {
        return create(devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, traceRippleOut, traceDerivation, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    //============================================================================

    public boolean isDevMode() {
        return devMode;
    }

    public boolean isCheckOrphanState() {
        return devMode && checkOrphanState;
    }

    public boolean isRunSequential() {
        return devMode && runSequential;
    }

    public boolean isTraceUniverse() {
        return devMode && traceUniverse;
    }

    public boolean isTraceMutable() {
        return devMode && traceMutable;
    }

    public boolean isTraceMatching() {
        return devMode && traceMatching;
    }

    public boolean isTraceActions() {
        return devMode && traceActions;
    }

    public boolean isTraceRippleOut() {
        return devMode && traceRippleOut;
    }

    public boolean isTraceDerivation() {
        return devMode && traceDerivation;
    }

    public int getMaxInInQueue() {
        return maxInInQueue;
    }

    public int getMaxTotalNrOfChanges() {
        return devMode ? maxTotalNrOfChanges : Integer.MAX_VALUE;
    }

    public int getMaxNrOfChanges() {
        return devMode ? maxNrOfChanges : Integer.MAX_VALUE;
    }

    public int getMaxNrOfObserved() {
        return devMode ? maxNrOfObserved : Integer.MAX_VALUE;
    }

    public int getMaxNrOfObservers() {
        return devMode ? maxNrOfObservers : Integer.MAX_VALUE;
    }

    public int getMaxNrOfHistory() {
        return maxNrOfHistory;
    }
}
