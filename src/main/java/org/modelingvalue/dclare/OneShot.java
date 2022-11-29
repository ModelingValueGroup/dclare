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
     * Return the Mutable that should be serialised from the given state.
     * By default this is the whole universe
     *
     * @param state the state that is going to be serialized
     * @return the mutable to serialize
     */
    public Mutable getRootToSerialise(State state) {
        return universe;
    }

    /**
     * get the json string after building the model
     *
     * @return the json string
     */
    public String fromJsonToJson() {
        State endState = getState();
        return getStateToJson(getRootToSerialise(endState), endState).render();
    }

    public State getState() {
        synchronized (this) {
            if (endState == null) {
                List<Action<U>> actionList = List.of();
                if (jsonIn != null) {
                    actionList = actionList.append(Action.of("~readJson", u -> getJsonToState(universe, jsonIn).parse()));
                }
                actionList = actionList.appendList(getActions());
                actionList = actionList.append(Action.of("~build", u -> build(universe)));
                endState   = runAndGetEndState(universe, actionList);
            }
        }
        return endState;
    }

    /**
     * Get all the actions that should be run.
     * The default is an empty list
     *
     * @return the list of actions to perform
     */
    protected List<Action<U>> getActions() {
        return List.of();
    }

    /**
     * This is the body of the last action to run.
     * Overrule if you can use it.
     * Leave empty if you do not need is.
     */
    public void build(U universe) {
    }

    /**
     * get the StateToJson serialiser that is suitable for this mutable root
     *
     * @param root     the mutable root to use
     * @param endState the end state to serialize
     * @return the StateToJson to render with
     */
    protected StateToJson getStateToJson(Mutable root, State endState) {
        return new StateToJson(root, endState);
    }

    /**
     * get the JsonToState deserialiser that is suitable for this Universe
     *
     * @param universe the universe to use
     * @param jsonIn   the input json string
     * @return the JsonToState to accept the json with
     */
    protected JsonToState getJsonToState(U universe, String jsonIn) {
        return new JsonToState(universe, jsonIn);
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
    private State runAndGetEndState(U universe, List<Action<U>> actionList) {
        ContextPool contextPool = getContextPool();
        try {
            UniverseTransaction universeTransaction = new UniverseTransaction(universe, contextPool, getConfig());
            actionList.forEach(a -> {
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
