//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2024 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

package org.modelingvalue.dclare.test.support;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public class TestScheduler implements Consumer<Runnable> {

    public static TestScheduler of() {
        return new TestScheduler();
    }

    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private boolean                       stop;

    protected TestScheduler() {
    }

    @Override
    public void accept(Runnable action) {
        if (!stop) {
            try {
                queue.put(action);
            } catch (InterruptedException e) {
                throw new Error(e);
            }
        }
    }

    private Runnable take() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            throw new Error(e);
        }
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void start() {
        queue.clear();
        stop = false;
        Thread imperativeThread = new Thread(() -> {
            while (!stop) {
                take().run();
            }
        }, "TestUniverse.imperativeThread");
        imperativeThread.setDaemon(true);
        imperativeThread.start();
    }

    public void stop() {
        stop = true;
        queue.clear();
        try {
            queue.put(() -> {
            });
        } catch (InterruptedException e) {
            throw new Error(e);
        }
    }

}
