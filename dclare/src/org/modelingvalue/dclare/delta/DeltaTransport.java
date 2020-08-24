package org.modelingvalue.dclare.delta;

import static org.modelingvalue.collections.util.TraceTimer.*;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DeltaTransport {
    protected final DeltaAdaptor    producer;
    protected final DeltaAdaptor    consumer;
    protected final TransportThread transportThread;

    public DeltaTransport(String name, DeltaAdaptor producer, DeltaAdaptor consumer) {
        this.producer = producer;
        this.consumer = consumer;
        transportThread = makeTransportThread(name);
        transportThread.start();
    }

    protected TransportThread makeTransportThread(String name) {
        return new TransportThread(name);
    }

    public void stop() {
        producer.stop();
        consumer.stop();
        transportThread.stop = true;
    }

    public void interrupt() {
        producer.interrupt();
        consumer.interrupt();
        transportThread.interrupt();
    }

    public void join() {
        producer.join();
        consumer.join();
        transportThread.join_();
    }

    public static void stopAllDeltaTransports(DeltaTransport... transports) {
        Arrays.stream(transports).forEach(DeltaTransport::stop);
        Arrays.stream(transports).forEach(DeltaTransport::interrupt);
        Arrays.stream(transports).forEach(DeltaTransport::join);

        List<Throwable> problems = Arrays.stream(transports)
                .flatMap(DeltaTransport::getThrowables)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!problems.isEmpty()) {
            if (problems.size() == 1) {
                throw new Error(problems.get(0));
            } else {
                throw new MultiError("problems after stop of support threads", problems);
            }
        }
    }

    public Stream<Throwable> getThrowables() {
        return Stream.of(producer.getThrowable(), producer.getThrowable(), transportThread.getThrowable());
    }

    protected class TransportThread extends Thread {
        private boolean   stop;
        private Throwable throwable;

        public TransportThread(String name) {
            super("transport-" + name);
        }

        @Override
        public void run() {
            traceLog("***Transport    %s: START", getName());
            while (!stop) {
                try {
                    handle(next());
                } catch (InterruptedException e) {
                    if (!stop) {
                        throwable = new Error("unexpected interrupt", e);
                    }
                } catch (Error e) {
                    if (!(e.getCause() instanceof InterruptedException)) {
                        throwable = new Error("unexpected interrupt", e);
                    }
                } catch (Throwable t) {
                    throwable = new Error("unexpected throwable", t);
                }
            }
            traceLog("***Transport    %s: STOP", getName());
        }

        protected Delta next() {
            return producer.get();
        }

        protected void handle(Delta delta) throws InterruptedException {
            consumer.accept(delta);
        }

        public void join_() {
            try {
                join();
            } catch (InterruptedException e) {
                throw new Error(e);
            }
        }

        public Throwable getThrowable() {
            return throwable;
        }
    }
}
