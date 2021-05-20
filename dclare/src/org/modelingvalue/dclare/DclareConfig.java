package org.modelingvalue.dclare;

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
        this(null, null, DEV_MODE, CHECK_ORPHAN_STATE, RUN_SEQUENTIAL, TRACE_UNIVERSE, TRACE_MUTABLE, TRACE_MATCHING, TRACE_ACTIONS, MAX_IN_IN_QUEUE, MAX_TOTAL_NR_OF_CHANGES, MAX_NR_OF_CHANGES, MAX_NR_OF_OBSERVED, MAX_NR_OF_OBSERVERS, MAX_NR_OF_HISTORY);
    }

    private DclareConfig(State start, Consumer<UniverseTransaction> cycle, boolean devMode, boolean checkOrphanState, boolean runSequential, boolean traceUniverse, boolean traceMutable, boolean traceMatching, boolean traceActions, int maxInInQueue, int maxTotalNrOfChanges, int maxNrOfChanges, int maxNrOfObserved, int maxNrOfObservers, int maxNrOfHistory) {
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

    //============================================================================
    // All these with***() methods are designed to give the JIT compiler a pleasant time.
    // The fields are all final to improve inlineability.
    // All the with***() methods are probably not very efficient but that does not matter because they are not used frequently.
    //
    public DclareConfig withStart(State start) {
        return new DclareConfig(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withCycle(Consumer<UniverseTransaction> cycle) {
        return new DclareConfig(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withDevMode(boolean devMode) {
        return new DclareConfig(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withCheckOrphanState(boolean checkOrphanState) {
        return new DclareConfig(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withRunSequential(boolean runSequential) {
        return new DclareConfig(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withTraceUniverse(boolean traceUniverse) {
        return new DclareConfig(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withTraceMutable(boolean traceMutable) {
        return new DclareConfig(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withTraceMatching(boolean traceMatching) {
        return new DclareConfig(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withTraceActions(boolean traceActions) {
        return new DclareConfig(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withMaxInInQueue(int maxInInQueue) {
        return new DclareConfig(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withMaxTotalNrOfChanges(int maxTotalNrOfChanges) {
        return new DclareConfig(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withMaxNrOfChanges(int maxNrOfChanges) {
        return new DclareConfig(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withMaxNrOfObserved(int maxNrOfObserved) {
        return new DclareConfig(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withMaxNrOfObservers(int maxNrOfObservers) {
        return new DclareConfig(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
    }

    public DclareConfig withMaxNrOfHistory(int maxNrOfHistory) {
        return new DclareConfig(start, cycle, devMode, checkOrphanState, runSequential, traceUniverse, traceMutable, traceMatching, traceActions, maxInInQueue, maxTotalNrOfChanges, maxNrOfChanges, maxNrOfObserved, maxNrOfObservers, maxNrOfHistory);
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
