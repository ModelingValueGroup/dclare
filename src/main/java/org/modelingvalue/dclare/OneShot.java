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

import org.modelingvalue.collections.util.ContextThread;
import org.modelingvalue.collections.util.ContextThread.ContextPool;

/**
 * This class will enable you to build a model in a dclare repo and then finish.
 * You can get the json of the contents of this repo afterwards.
 *
 * @param <U> the Universe class for this repo
 */
@SuppressWarnings("unused")
public abstract class OneShot<U extends Universe> {
    private final State endState;

    public OneShot() {
        ContextPool contextPool = getContextPool();
        try {
            UniverseTransaction universeTransaction = new UniverseTransaction(getUniverse(), contextPool, getConfig());
            universeTransaction.put("build-model", this::buildModel);
            universeTransaction.stop();
            endState = universeTransaction.waitForEnd();
        } finally {
            contextPool.shutdownNow();
        }
    }

    public String getJson() {
        return getStateToJson().render();
    }

    protected abstract StateToJson getStateToJson();

    public static DclareConfig getConfig() {
        return new DclareConfig().withTraceMatching(true).withDevMode(true);
    }

    /**
     * @return the ContextPool that was created
     */
    public ContextPool getContextPool() {
        return ContextThread.createPool();
    }

    /**
     * @return the final state after the model was build.
     */
    public State getEndState() {
        return endState;
    }

    /**
     * @return the universe
     */
    public abstract U getUniverse();

    /**
     * do the actual model building
     */
    protected abstract void buildModel();
}
