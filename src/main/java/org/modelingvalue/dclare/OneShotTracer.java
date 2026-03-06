package org.modelingvalue.dclare;

import org.modelingvalue.dclare.OneShot.PoolPoolInfo;

public interface OneShotTracer {
    boolean TRACE_ONE_SHOT     = Boolean.getBoolean("TRACE_ONE_SHOT");
    boolean NO_POOL_POOL_TRACE = Boolean.getBoolean("NO_POOL_POOL_TRACE");

    void traceStart(String oneShotName, int numActions);

    void traceDone(String oneShotName, long durationMs);

    void traceCacheSkip(String oneShotName, Object actionId);

    void traceActionBegin(String oneShotName, Object actionId);

    void traceCacheWrite(String oneShotName, Object actionId);

    void traceActionEnd(String oneShotName, Object actionId, long overallMs, long methodMs, long rulesMs);

    void tracePoolPoolInit(String threadName, int idleCount, int busyCount, PoolPoolInfo poolPoolInfo);

    void tracePoolPoolGet(String threadName, int idleCount, int busyCount, PoolPoolInfo poolPoolInfo);

    void tracePoolPoolTimeout(String threadName, int idleCount, int busyCount, PoolPoolInfo poolPoolInfo);

    void tracePoolPoolWaited(String threadName, long waitedMs, int idleCount, int busyCount, PoolPoolInfo poolPoolInfo);

    void tracePoolPoolNotBusy(String threadName, int idleCount, int busyCount, PoolPoolInfo poolPoolInfo);

    void tracePoolPoolDone(String threadName, int idleCount, int busyCount, PoolPoolInfo poolPoolInfo);

    void tracePoolPoolAlarmStuck(String threadName, int idleCount, int busyCount, PoolPoolInfo poolPoolInfo, long durationMs, String poolName);

    void tracePoolPoolMonitorQuit();

    class ToStderr implements OneShotTracer {
        @Override
        public void traceStart(String oneShotName, int numActions) {
            if (TRACE_ONE_SHOT) {
                System.err.printf("TRACE_ONE_SHOT: %-12s %-40s  -  #actions=%d\n", "START", oneShotName, numActions);
            }
        }

        @Override
        public void traceDone(String oneShotName, long durationMs) {
            if (TRACE_ONE_SHOT) {
                System.err.printf("TRACE_ONE_SHOT: %-12s %-40s  -  duration=%5d ms\n", "DONE", oneShotName, durationMs);
            }
        }

        @Override
        public void traceCacheSkip(String oneShotName, Object actionId) {
            if (TRACE_ONE_SHOT) {
                System.err.printf("TRACE_ONE_SHOT: %-12s %-40s  -  %s\n", "CACHE-SKIP", oneShotName, actionId);
            }
        }

        @Override
        public void traceActionBegin(String oneShotName, Object actionId) {
            if (TRACE_ONE_SHOT) {
                System.err.printf("TRACE_ONE_SHOT: %-12s %-40s  -  %s\n", "ACTION-BEGIN", oneShotName, actionId);
            }
        }

        @Override
        public void traceCacheWrite(String oneShotName, Object actionId) {
            if (TRACE_ONE_SHOT) {
                System.err.printf("TRACE_ONE_SHOT: %-12s %-40s  -  %s\n", "CACHE-WRITE", oneShotName, actionId);
            }
        }

        @Override
        public void traceActionEnd(String oneShotName, Object actionId, long overallMs, long methodMs, long rulesMs) {
            if (TRACE_ONE_SHOT) {
                System.err.printf("TRACE_ONE_SHOT: %-12s %-40s  -  %-25s took %5d ms (m+r=%5d + %5d)\n", "ACTION-END", oneShotName, actionId, overallMs, methodMs, rulesMs);
            }
        }

        @Override
        public void tracePoolPoolInit(String threadName, int idleCount, int busyCount, PoolPoolInfo poolPoolInfo) {
            if (!NO_POOL_POOL_TRACE) {
                System.err.printf("TRACE: ContextPoolPool: [%-25s] %-25s: idle/busy=%3d/%3d: %s\n", threadName, "PP_INIT", idleCount, busyCount, poolPoolInfo);
            }
        }

        @Override
        public void tracePoolPoolGet(String threadName, int idleCount, int busyCount, PoolPoolInfo poolPoolInfo) {
            if (!NO_POOL_POOL_TRACE) {
                System.err.printf("TRACE: ContextPoolPool: [%-25s] %-25s: idle/busy=%3d/%3d: %s\n", threadName, "PP_GET", idleCount, busyCount, poolPoolInfo);
            }
        }

        @Override
        public void tracePoolPoolTimeout(String threadName, int idleCount, int busyCount, PoolPoolInfo poolPoolInfo) {
            if (!NO_POOL_POOL_TRACE) {
                System.err.printf("TRACE: ContextPoolPool: [%-25s] %-25s: idle/busy=%3d/%3d: %s\n", threadName, "PP_TIMEOUT", idleCount, busyCount, poolPoolInfo);
            }
        }

        @Override
        public void tracePoolPoolWaited(String threadName, long waitedMs, int idleCount, int busyCount, PoolPoolInfo poolPoolInfo) {
            if (!NO_POOL_POOL_TRACE) {
                System.err.printf("TRACE: ContextPoolPool: [%-25s] waited %6d ms         : idle/busy=%3d/%3d: %s\n", threadName, waitedMs, idleCount, busyCount, poolPoolInfo);
            }
        }

        @Override
        public void tracePoolPoolNotBusy(String threadName, int idleCount, int busyCount, PoolPoolInfo poolPoolInfo) {
            if (!NO_POOL_POOL_TRACE) {
                System.err.printf("TRACE: ContextPoolPool: [%-25s] %-25s: idle/busy=%3d/%3d: %s\n", threadName, "PP_NOT_BUSY", idleCount, busyCount, poolPoolInfo);
            }
        }

        @Override
        public void tracePoolPoolDone(String threadName, int idleCount, int busyCount, PoolPoolInfo poolPoolInfo) {
            if (!NO_POOL_POOL_TRACE) {
                System.err.printf("TRACE: ContextPoolPool: [%-25s] %-25s: idle/busy=%3d/%3d: %s\n", threadName, "PP_DONE", idleCount, busyCount, poolPoolInfo);
            }
        }

        @Override
        public void tracePoolPoolAlarmStuck(String threadName, int idleCount, int busyCount, PoolPoolInfo poolPoolInfo, long durationMs, String poolName) {
            System.err.printf("ALARM: ContextPool probably stuck (busy for %-8d ms): %s\n", durationMs, poolName);
        }

        @Override
        public void tracePoolPoolMonitorQuit() {
            System.err.print("ALARM: PoolPool monitor interupted and quit\n");
        }
    }
}
