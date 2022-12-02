//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2022 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import org.modelingvalue.collections.List;
import org.modelingvalue.collections.util.ContextThread;
import org.modelingvalue.collections.util.ContextThread.ContextPool;
import org.modelingvalue.json.FromJsonBase;

/**
 * This class will enable you to build a model in a dclare repo and then finish.
 * You can get the json of the contents of this repo afterwards.
 *
 * @param <U> the Universe class for this repo
 */
@SuppressWarnings("unused")
public class OneShot<U extends Universe> {
    private final U      universe;
    private final String jsonIn;
    private       State  endState;

    public OneShot(U universe, String jsonIn) {
        this.universe = universe;
        this.jsonIn   = jsonIn;
    }

    /**
     * get the universe passed at creation.
     *
     * @return the universe
     */
    public U getUniverse() {
        return universe;
    }

    /**
     * get the jsonIn passed at creation
     *
     * @return the json input string
     */
    public String getJsonIn() {
        return jsonIn;
    }

    /**
     * Return the Mutable that should be serialised from the given state.
     * By default this is the whole universe
     *
     * @return the mutable to serialize
     */
    public Mutable getRootToSerialise() {
        return universe;
    }

    /**
     * get the end state after all actions are done.
     * if the actions have not run yet they will first be executed.
     *
     * @return the end state
     */
    public State getEndState() {
        synchronized (this) {
            if (endState == null) {
                endState = runAndGetEndState();
            }
        }
        return endState;
    }

    /**
     * get the json string after building the model
     *
     * @return the json string
     */
    public String fromJsonToJson() {
        getEndState(); // force all to run first
        return getStateToJson().render();
    }

    /**
     * Get all the actions that should be run.
     * The default is: preread/read/postread/build/last
     *
     * @return the list of actions to perform
     */
    protected List<Action<U>> getAllActions() {
        List<Action<U>> actionList = List.of();
        actionList = actionList.append(Action.of("~preread", u -> preread()));
        if (jsonIn != null) {
            actionList = actionList.append(Action.of("~read", u -> read()));
        }
        actionList = actionList.append(Action.of("~postread", u -> postread()));
        actionList = actionList.append(Action.of("~action1", u -> action1()));
        actionList = actionList.append(Action.of("~action2", u -> action2()));
        actionList = actionList.append(Action.of("~action3", u -> action3()));
        return actionList;
    }

    /**
     * This is the body of the pre-read action to run.
     * Overrule if you can use it.
     * Leave empty if you do not need is.
     */
    protected void preread() {
    }

    /**
     * read the json input into the state
     */
    protected void read() {
        if (getJsonIn() != null) {
            FromJsonBase<Object, Object> jsonToState = getJsonToState();
            if (jsonToState == null) {
                throw new IllegalArgumentException("no json reader available while json should be read");
            }
            jsonToState.parse();
        }
    }

    /**
     * This is the body of the post-read action to run.
     * Overrule if you can use it.
     * Leave empty if you do not need it.
     */
    protected void postread() {
    }

    /**
     * This is the body of the one of the action to run.
     * Overrule if you can use it.
     * Leave empty if you do not need it.
     */
    protected void action1() {
    }

    /**
     * This is the body of the one of the action to run.
     * Overrule if you can use it.
     * Leave empty if you do not need it.
     */
    protected void action2() {
    }

    /**
     * This is the body of the one of the action to run.
     * Overrule if you can use it.
     * Leave empty if you do not need it.
     */
    protected void action3() {
    }

    /**
     * get the StateToJson serialiser that is suitable for this mutable root
     *
     * @return the StateToJson to render with
     */
    protected StateToJson getStateToJson() {
        return new StateToJson(getRootToSerialise(), getEndState());
    }

    /**
     * get the JsonToState deserialiser that is suitable for this Universe.
     * the default is null, which means "no json read"
     *
     * @return the JsonToState to accept the json with
     */
    protected <L, M> FromJsonBase<L, M> getJsonToState() {
        return null;
    }

    /**
     * overrule where needed
     *
     * @return the config
     */
    public DclareConfig getConfig() {
        return new DclareConfig().withTraceMatching(true).withDevMode(true);
    }

    /**
     * overrule where needed
     *
     * @return the ContextPool that was created
     */
    public ContextPool getContextPool() {
        return ContextThread.createPool();
    }

    /**
     * run the list of actions in sequence with wait for idles in between
     * then stop the universe tx and return the resulting state
     *
     * @return the final state after the model was build.
     */
    @SuppressWarnings("unchecked")
    private State runAndGetEndState() {
        ContextPool contextPool = getContextPool();
        try {
            UniverseTransaction universeTransaction = new UniverseTransaction(getUniverse(), contextPool, getConfig());
            getAllActions().forEach(a -> {
                universeTransaction.put((Action<Universe>) a);
                universeTransaction.waitForIdle();
            });
            universeTransaction.stop();
            return universeTransaction.waitForEnd();
        } finally {
            contextPool.shutdownNow();
        }
    }
}
