package org.modelingvalue.dclare.test.support;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import org.modelingvalue.collections.util.ContextThread.*;
import org.modelingvalue.dclare.sync.*;

public class CommunicationHelper {
    private static final int                                           IDLE_DETECT_TIMEOUT                         = 5_000;
    private static final int                                           IDLE_SAMPLES_FOR_DEFINITIVE_IDLE_CONCLUSION = 10;
    //
    private static final List<CommunicationModelMakerWithDeltaAdaptor> ALL_MODEL_MAKERS                            = new ArrayList<>();
    private static final List<DeltaTransport>                          ALL_TRANSPORTS                              = new ArrayList<>();
    private static final List<ContextPool>                             ALL_POOLS                                   = new ArrayList<>();
    private static final List<WorkDaemon<?>>                           ALL_OTHER_DAEMONS                           = new ArrayList<>();

    public static void add(CommunicationModelMakerWithDeltaAdaptor r) {
        ALL_MODEL_MAKERS.add(r);
    }

    public static void add(DeltaTransport testDeltaTransport) {
        ALL_TRANSPORTS.add(testDeltaTransport);
    }

    public static void add(ContextPool pool) {
        ALL_POOLS.add(pool);
    }

    public static void add(WorkDaemon<?> daemon) {
        ALL_OTHER_DAEMONS.add(daemon);
    }

    public static void tearDownAll() {
        ALL_MODEL_MAKERS.forEach(mm -> mm.getDeltaAdaptor().forceStop());
        ALL_TRANSPORTS.forEach(DeltaTransport::forceStop);
        ALL_OTHER_DAEMONS.forEach(WorkDaemon::forceStop);

        ALL_MODEL_MAKERS.forEach(mm -> mm.getDeltaAdaptor().join());
        ALL_TRANSPORTS.forEach(DeltaTransport::join);
        ALL_OTHER_DAEMONS.forEach(WorkDaemon::join_);

        ALL_MODEL_MAKERS.forEach(mm -> {
            if (mm.getTx() != null) {
                mm.getTx().stop();
            }
        });
        ALL_MODEL_MAKERS.forEach(mm -> {
            if (mm.getTx() != null) {
                mm.getTx().waitForEnd();
            }
        });
        ALL_POOLS.forEach(pool -> {
            pool.shutdownNow();
            assertDoesNotThrow(() -> assertTrue(pool.awaitTermination(1, TimeUnit.SECONDS)));
        });

        ALL_MODEL_MAKERS.clear();
        ALL_TRANSPORTS.clear();
        ALL_OTHER_DAEMONS.clear();
        ALL_POOLS.clear();

        rethrowAny();
    }

    private static void rethrowAny() {
        assertAll(Stream.concat(
                ALL_TRANSPORTS.stream().flatMap(DeltaTransport::getThrowables),
                ALL_MODEL_MAKERS.stream().map(mm -> mm.getDeltaAdaptor().getThrowable())
        )
                .filter(Objects::nonNull)
                .map(t -> () -> {
                    throw t;
                }));
    }

    public static void busyWaitAllForIdle() {
        final long t0 = System.currentTimeMillis();
        boolean    busy;
        do {
            // probe isBusy() 10 times and 1 ms apart until we find a busy sample or conclude that we are idle
            busy = false;
            for (int i = 0; i < IDLE_SAMPLES_FOR_DEFINITIVE_IDLE_CONCLUSION && !busy; i++) {
                nap();
                rethrowAny();
                busy = ALL_MODEL_MAKERS.stream().anyMatch(r -> r.getDeltaAdaptor() == null || r.getDeltaAdaptor().isBusy())
                        || ALL_TRANSPORTS.stream().anyMatch(DeltaTransport::isBusy);
            }
        } while (System.currentTimeMillis() < t0 + IDLE_DETECT_TIMEOUT && busy);
        //System.err.printf("busyWait ended after %d ms\n", System.currentTimeMillis() - t0);
        if (busy) {
            // darn,
            System.err.println("this test did not get idle in time (" + ALL_MODEL_MAKERS.size() + " rigs, " + ALL_TRANSPORTS.size() + " transports):");
            for (CommunicationModelMakerWithDeltaAdaptor r : ALL_MODEL_MAKERS) {
                StringBuilder adWhy  = new StringBuilder();
                boolean       adBusy = r.getDeltaAdaptor().isBusy(adWhy);
                System.err.printf(" - modelmaker %s: %s (%s)\n", r.getName(), adBusy, adWhy);
            }
            for (DeltaTransport t : ALL_TRANSPORTS) {
                StringBuilder producerWhy  = new StringBuilder();
                StringBuilder consumerWhy  = new StringBuilder();
                String        name         = t.transportDaemon.getName();
                boolean       ttBusy       = t.transportDaemon.isBusy();
                boolean       producerBusy = t.producer.isBusy(producerWhy);
                boolean       consumerBusy = t.consumer.isBusy(consumerWhy);

                System.err.printf(" - transport %s.transportDaemon: %s\n", name, ttBusy ? "BUSY" : "idle");
                System.err.printf(" - transport %s.producer       : %s (%s)\n", name, producerBusy ? "BUSY" : "idle", producerWhy);
                System.err.printf(" - transport %s.consumer       : %s (%s)\n", name, consumerBusy ? "BUSY" : "idle", consumerWhy);
            }
            fail();
        }
        CommunicationModelMaker.assertNoUncaughts();
    }

    private static void nap() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            fail(e);
        }
    }
}
