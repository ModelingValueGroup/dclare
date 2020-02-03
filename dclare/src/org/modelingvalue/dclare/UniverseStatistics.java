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
    private       int                 numChangesInRun;
    private       long                runCount;
    private       long                numChangesEver;


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
        int n = numChangesInRun;
        numChangesInRun = 0;
        numChangesEver += n;
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
        if (numChangesInRun > maxTotalNrOfChanges) {
            synchronized (tx) {
                return numChangesInRun++;
            }
        } else {
            return numChangesInRun++;
        }
    }

    public int totalChanges() {
        tx.throwIfError();
        return numChangesInRun;
    }

    public long totalChangesEver() {
        tx.throwIfError();
        return numChangesEver;
    }

    @Override
    public String toString() {
        return "UniverseStats:\n" +
                " debugging       = " + debugging + "\n" +
                " runCount        = " + runCount + "\n" +
                " numChangesEver  = " + numChangesEver + "\n" +
                " numChangesInRun = " + numChangesInRun;
    }
}
