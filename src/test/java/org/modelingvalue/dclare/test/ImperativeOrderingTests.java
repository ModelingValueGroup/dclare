package org.modelingvalue.dclare.test;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.collections.util.StatusProvider;
import org.modelingvalue.dclare.*;
import org.modelingvalue.dclare.test.support.*;

import java.time.Duration;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.modelingvalue.dclare.CoreSetableModifier.containment;
import static org.modelingvalue.dclare.test.support.Shared.THE_POOL;

public class ImperativeOrderingTests {
    private static final DclareConfig   BASE_CONFIG        = new DclareConfig()    //
            .withDevMode(true)                                                     //
            .withCheckOrphanState(true)                                            //
            .withMaxNrOfChanges(16)                                                //
            .withMaxTotalNrOfChanges(1000)                                         //
            .withMaxNrOfObserved(40)                                               //
            .withMaxNrOfObservers(40)                                              //
            .withTraceUniverse(false)                                              //
            .withTraceMutable(false)                                               //
            .withTraceActions(false)                                               //
            .withTraceMatching(false)                                              //
            .withTraceRippleOut(false)                                             //
            .withTraceDerivation(false);
    private static final DclareConfig[] CONFIGS            = new DclareConfig[]{   //
            BASE_CONFIG,                                                           //
            BASE_CONFIG                                                            //
                    .withDevMode(true)                                             //
                    .withRunSequential(true)                                       //
    };

    private boolean imperativeTest(DclareConfig config, int size, Set<Pair<Integer, Integer>> edges) {
        return imperativeTest(config, size, edges, edges);
    }

    private boolean imperativeTest(DclareConfig config, int size, Set<Pair<Integer, Integer>> expectedEdges, Set<Pair<Integer, Integer>> actualEdges) {
        Observed<TestMutable, List<TestNewable>> cs = Observed.of("cs", List.of(), containment);
        TestMutableClass U = TestMutableClass.of("Universe", cs);

        OrderedTestUniverse universe = OrderedTestUniverse.of("universe", U, size, expectedEdges, actualEdges);
        UniverseTransaction utx = new UniverseTransaction(universe, THE_POOL, config);

        Set<Pair<Integer, Runnable>> actions = Set.of(IntStream.range(0, size)
                .mapToObj(i -> Pair.of(i, (Runnable) () -> {})).toList());

        run(utx, "init", actions);

        run(utx, "stop", Set.of(Pair.of(0, utx::stop)));
        utx.waitForEnd();
        return universe.passed();
    }

    @RepeatedTest(64)
    public void basicOrdering(RepetitionInfo repetitionInfo) {
        DclareConfig config = CONFIGS[(repetitionInfo.getCurrentRepetition() - 1) / 32];

        assertTrue(imperativeTest(config, 1, Set.of()));
        assertTrue(imperativeTest(config, 2, Set.of()));
        assertTrue(imperativeTest(config, 2, Set.of(Pair.of(0, 1))));
    }

    @RepeatedTest(64)
    public void complexOrdering(RepetitionInfo repetitionInfo) {
        DclareConfig config = CONFIGS[(repetitionInfo.getCurrentRepetition() - 1) / 32];

        assertTrue(imperativeTest(config, 6, Set.of(
                Pair.of(0, 2), Pair.of(0, 3), Pair.of(1, 3),
                Pair.of(2, 4), Pair.of(3, 4), Pair.of(3, 5)
        )));
    }

    @RepeatedTest(64)
    public void cyclicOrdering(RepetitionInfo repetitionInfo) {
        DclareConfig config = CONFIGS[(repetitionInfo.getCurrentRepetition() - 1) / 32];

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThrows(Error.class, () ->
                imperativeTest(config, 2, Set.of(Pair.of(0, 1), Pair.of(1, 0)))));
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThrows(Error.class, () ->
                imperativeTest(config, 1, Set.of(Pair.of(0, 0)))));
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThrows(Error.class, () ->
                imperativeTest(config, 4, Set.of(
                    Pair.of(0, 1), Pair.of(1, 2), Pair.of(2, 3), Pair.of(3, 1)
                ))));
    }

    @RepeatedTest(64)
    public void wrongOrdering(RepetitionInfo repetitionInfo) {
        DclareConfig config = CONFIGS[(repetitionInfo.getCurrentRepetition() - 1) / 32];

        assertFalse(imperativeTest(config, 2, Set.of(Pair.of(0, 1)), Set.of(Pair.of(1, 0))));
        assertFalse(imperativeTest(config, 3,
                Set.of(Pair.of(0, 1), Pair.of(0, 2), Pair.of(1, 2)),
                Set.of(Pair.of(0, 1), Pair.of(0, 2), Pair.of(2, 1))
        ));
    }

    private void run(UniverseTransaction utx, String id, Set<Pair<Integer, Runnable>> actions) {
        StatusProvider.StatusIterator<UniverseTransaction.Status> it = utx.getStatusIterator();
        UniverseTransaction.Status status = it.waitForStoppedOr(UniverseTransaction.Status::isIdle);
        if (!status.isStopped()) {
            if (utx.getConfig().isTraceUniverse()) {
                System.err.println("-------------------------- " + id + " -------------------------------------------");
            }
            OrderedTestUniverse u = (OrderedTestUniverse) utx.universe();
            u.schedule(actions);
            it.waitForStoppedOr(s -> !s.active.isEmpty());
        }
    }

}
