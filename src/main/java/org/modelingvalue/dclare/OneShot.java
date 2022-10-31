package org.modelingvalue.dclare;

import org.modelingvalue.collections.util.ContextThread;
import org.modelingvalue.collections.util.ContextThread.ContextPool;

import java.util.function.Predicate;

/**
 * This class will enable you to build a model in a dclare repo and then finish.
 * You can get the json of the contents of this repo afterwards.
 *
 * @param <U> the Universe class for this repo
 */
@SuppressWarnings("unused")
public abstract class OneShot<U extends Universe> {
    private final ContextPool         contextPool;
    private final U                   universe;
    private final UniverseTransaction universeTransaction;
    private final State               endState;

    public OneShot() {
        universe            = createUniverse();
        contextPool         = ContextThread.createPool();
        universeTransaction = new UniverseTransaction(universe, contextPool, new DclareConfig().withTraceMatching(true).withDevMode(true));
        universeTransaction.put("build-model", this::buildModel);
        universeTransaction.stop();
        endState = universeTransaction.waitForEnd();
    }

    public String getJson() {
        return StateToJson.toJson(getUniverse(), getEndState(), getJsonFilter());
    }

    /**
     * overrule this if you need to further reduce the set of Setables to serialize to json
     *
     * @return a filter that passes all Setables to include
     */
    @SuppressWarnings("rawtypes")
    protected Predicate<Setable> getJsonFilter() {
        return s -> !s.isPlumbing();
    }

    /**
     * @return the ContextPool that was created
     */
    public ContextPool getContextPool() {
        return contextPool;
    }

    /**
     * @return the universe that was created
     */
    public U getUniverse() {
        return universe;
    }

    /**
     * @return the universe transactionthat was created
     */
    public UniverseTransaction getUniverseTransaction() {
        return universeTransaction;
    }

    /**
     * @return the final state after the model was build.
     */
    public State getEndState() {
        return endState;
    }

    /**
     * you need to create the correct Universe here
     *
     * @return the Universe for this repo
     */
    protected abstract U createUniverse();

    /**
     * do the actual model building
     */
    protected abstract void buildModel();
}
