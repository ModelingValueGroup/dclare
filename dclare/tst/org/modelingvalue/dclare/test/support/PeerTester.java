package org.modelingvalue.dclare.test.support;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.TimeUnit;

public class PeerTester {
    private Process        process;
    private BufferedReader in;
    private BufferedWriter out;

    public PeerTester(Class<?> peerClass) {
        assertDoesNotThrow(() -> {
            String         dirOrJar      = peerClass.getProtectionDomain().getCodeSource().getLocation().getFile();
            String         peerClassName = peerClass.getName();
            ProcessBuilder pb            = new ProcessBuilder("java", "-cp", dirOrJar, peerClassName);
            process = pb.start();
            in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        });
    }

    public void expectExit(int exitCode, long maxMs) {
        assertDoesNotThrow(() -> {
            out.flush();
            assertTrue(process.waitFor(maxMs, TimeUnit.MILLISECONDS));
            assertEquals(exitCode, process.exitValue());
        });
    }

    public BufferedReader getIn() {
        return in;
    }

    public BufferedWriter getOut() {
        return out;
    }
}
