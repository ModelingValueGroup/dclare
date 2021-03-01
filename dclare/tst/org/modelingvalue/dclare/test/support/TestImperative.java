package org.modelingvalue.dclare.test.support;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public class TestImperative implements Consumer<Runnable> {

    public static final TestImperative of() {
        return new TestImperative();
    }

    private final Thread                  imperativeThread;
    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

    protected TestImperative() {
        imperativeThread = new Thread(() -> {
            while (true) {
                take().run();
            }
        }, "TestUniverse.imperativeThread");
        imperativeThread.setDaemon(true);
        imperativeThread.start();
    }

    @Override
    public void accept(Runnable action) {
        try {
            queue.put(action);
        } catch (InterruptedException e) {
            throw new Error(e);
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

}
