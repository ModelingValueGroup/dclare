package org.modelingvalue.dclare.test;

import org.modelingvalue.collections.util.ContextThread;
import org.modelingvalue.collections.util.ContextThread.ContextPool;
import org.modelingvalue.dclare.State;
import org.modelingvalue.dclare.UniverseTransaction;

public class Shared {
    public static final ContextPool THE_POOL    = ContextThread.createPool();
    public static final boolean     PRINT_STATE = false;

    public static void printState(UniverseTransaction universeTransaction, State result) {
        if (PRINT_STATE) {
            int num = result == null ? -1 : result.getObjects(DObject.class).size();

            System.err.println("**** stats *********************************************************");
            System.err.println(universeTransaction.stats());
            if (0 <= num) {
                System.err.println("**** num DObjects **************************************************");
                System.err.println(num);
                if (num < 100) {
                    System.err.println("**** end-state *****************************************************");
                    System.err.println(result);
                }
            }
            System.err.println("********************************************************************");
        }
    }

    public static Throwable getCause(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}
