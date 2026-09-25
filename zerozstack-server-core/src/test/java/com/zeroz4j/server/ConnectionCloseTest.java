/*
 * Copyright 2026 Franz Schöning
 * Project: https://www.zeroz4j.com
 * Author: Franz Schöning - Principal Enterprise Architect (https://www.franzschoning.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zeroz4j.server;

import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.api.RmiService;
import com.zeroz4j.api.SyncFrameTypes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the server does and says when a connection closes (0.9.1+).
 *
 * <p>Written after a phone reconnected every 16 to 35 seconds for hours. Each close interrupted the
 * call the page had just made; the interrupt landed in the database pool, the application logged a
 * burst of errors, and the engine logged SEVERE for a reply nobody could receive. Nothing logged
 * why the connection had closed.</p>
 */
@EnableWeld
public class ConnectionCloseTest {

    @RmiService
    public interface SlowService {
        /** Waits at a gate the test opens, and records whether it was interrupted. */
        String slow(String mark);

        /** Records that it ran. */
        String fast(String mark);

        /** Fails as a call does when something under it was interrupted. */
        String interrupted();
    }

    @ApplicationScoped
    public static class SlowServiceImpl implements SlowService {
        static volatile CountDownLatch entered = new CountDownLatch(1);
        static volatile CountDownLatch gate = new CountDownLatch(1);
        static final AtomicBoolean wasInterrupted = new AtomicBoolean();
        static final List<String> finished = new CopyOnWriteArrayList<>();

        @Override
        public String slow(String mark) {
            entered.countDown();
            try {
                if (!gate.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("the test never opened the gate");
                }
            } catch (InterruptedException interrupted) {
                wasInterrupted.set(true);
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting", interrupted);
            }
            finished.add(mark);
            return mark;
        }

        @Override
        public String fast(String mark) {
            finished.add(mark);
            return mark;
        }

        @Override
        public String interrupted() {
            throw new IllegalStateException("IJ031013: Interrupted attempting lock",
                    new InterruptedException());
        }
    }

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.of(
            ServerRuntime.class,
            WasmRmiServerEngine.class,
            SyncEngine.class,
            ObjectMapperProducer.class,
            LiveMutexManager.class,
            SlowServiceImpl.class
    );

    @Inject
    WasmRmiServerEngine engine;

    @Inject
    ObjectMapper mapper;

    /** Every record the engine and the frame queue log. */
    static final class Captured extends Handler {
        final List<LogRecord> records = new CopyOnWriteArrayList<>();

        @Override public void publish(LogRecord record) { records.add(record); }
        @Override public void flush() { }
        @Override public void close() { }

        List<LogRecord> at(Level level) {
            List<LogRecord> matching = new ArrayList<>();
            for (LogRecord record : records) {
                if (record.getLevel().equals(level)) {
                    matching.add(record);
                }
            }
            return matching;
        }

        LogRecord containing(String text) {
            for (LogRecord record : records) {
                if (record.getMessage() != null && record.getMessage().contains(text)) {
                    return record;
                }
            }
            return null;
        }
    }

    private final Captured log = new Captured();
    private final List<Logger> watched = new ArrayList<>();
    private WasmRmiServerEngineTest.FakeSession session;

    @BeforeEach
    public void setup() {
        engine.scanServiceRegistry();
        engine.clearKeepaliveBudgetForTesting();
        SlowServiceImpl.entered = new CountDownLatch(1);
        SlowServiceImpl.gate = new CountDownLatch(1);
        SlowServiceImpl.wasInterrupted.set(false);
        SlowServiceImpl.finished.clear();
        for (Class<?> source : new Class<?>[] { WasmRmiServerEngine.class, SessionFrameQueue.class }) {
            Logger logger = Logger.getLogger(source.getName());
            logger.addHandler(log);
            watched.add(logger);
        }
        session = open(new WasmRmiServerEngineTest.FakeSession("close-1"));
    }

    @AfterEach
    public void teardown() {
        SlowServiceImpl.gate.countDown();
        engine.onClose(session);
        for (Logger logger : watched) {
            logger.removeHandler(log);
        }
    }

    @Test
    public void closingDoesNotInterruptARunningCallAndDropsItsReplyWithoutAnError() throws Exception {
        engine.processIncomingBinaryPayload(ByteBuffer.wrap(call(1, "slow", "running")), session);
        assertTrue(SlowServiceImpl.entered.await(5, TimeUnit.SECONDS), "the call never started");
        // Queued behind the running call; it must be thrown away, not run.
        engine.processIncomingBinaryPayload(ByteBuffer.wrap(call(2, "fast", "queued")), session);

        engine.onClose(session, new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, ""));
        SlowServiceImpl.gate.countDown();

        LogRecord dropped = awaitLog("Reply to SlowService.slow dropped: connection close-1 closed");
        assertNotNull(dropped, "one line says the reply was dropped: " + messages());
        assertEquals(Level.INFO, dropped.getLevel());
        assertNull(dropped.getThrown(), "with no stack trace");

        assertFalse(SlowServiceImpl.wasInterrupted.get(),
                "closing the connection must not interrupt the call: the interrupt lands in the "
                        + "database pool and turns a closed tab into a burst of errors");
        assertEquals(List.of("running"), SlowServiceImpl.finished,
                "the running call finished; the one queued behind it never ran");
        assertTrue(log.at(Level.SEVERE).isEmpty(), "nothing SEVERE: " + messages());
        assertEquals(0, responses(session), "no reply was written to a closed connection");
    }

    @Test
    public void theCloseIsLoggedOnceWithItsCodeAndHowLongItWasOpen() {
        engine.onClose(session, new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "tab hidden"));

        LogRecord closed = log.containing("Connection closed: session close-1");
        assertNotNull(closed, messages());
        assertEquals(Level.INFO, closed.getLevel());
        String line = closed.getMessage();
        assertTrue(line.contains("user alice"), line);
        assertTrue(line.contains("code 1001 GOING_AWAY"), line);
        assertTrue(line.contains("reason \"tab hidden\""), line);
        assertTrue(line.matches(".*, open \\d+ ms$"), line);
    }

    @Test
    public void theClientsAccountOfItsPreviousCloseIsOnTheConnectedLine() {
        WasmRmiServerEngineTest.FakeSession reconnected = new WasmRmiServerEngineTest.FakeSession("close-2") {
            @Override
            public Map<String, List<String>> getRequestParameterMap() {
                return Map.of(
                        ConnectionDiagnostics.PARAM_CLOSE_CODE, List.of("1006"),
                        ConnectionDiagnostics.PARAM_CLOSE_AFTER_MS, List.of("830"),
                        ConnectionDiagnostics.PARAM_CLOSE_HIDDEN, List.of("1"),
                        ConnectionDiagnostics.PARAM_ATTEMPT, List.of("3"));
            }
        };
        log.records.clear();
        open(reconnected);
        try {
            LogRecord connected = log.containing("Client connected: alice");
            assertNotNull(connected, messages());
            assertTrue(connected.getMessage().endsWith("session=close-2; previous connection closed "
                            + "with code 1006 after 830 ms, page hidden, reconnect attempt 3"),
                    connected.getMessage());
        } finally {
            engine.onClose(reconnected);
        }
    }

    @Test
    public void aPeerThatHasGoneIsOneWarningWithNoStackTrace() {
        engine.onError(session, new IOException("Broken pipe"));
        engine.onError(session, new IllegalStateException("something else"));

        List<LogRecord> warnings = log.at(Level.WARNING);
        assertEquals(2, warnings.size(), messages());
        assertTrue(warnings.get(0).getMessage().contains("Connection error: session close-1, user alice"));
        assertNull(warnings.get(0).getThrown(), "a gone peer is ordinary: no stack trace");
        assertNotNull(warnings.get(1).getThrown(), "anything else keeps its stack trace");
    }

    @Test
    public void anInterruptedCallOnAnOpenConnectionIsAnsweredAndLoggedWithoutSevere() throws Exception {
        engine.processIncomingBinaryPayload(ByteBuffer.wrap(call(3, "interrupted")), session);

        session.basic.awaitFrames(2, 5_000);
        assertEquals(1, responses(session), "the caller is still answered, so it is not left waiting");
        LogRecord line = awaitLog("SlowService.interrupted on connection close-1 was interrupted");
        assertNotNull(line, messages());
        assertEquals(Level.INFO, line.getLevel());
        assertNull(line.getThrown());
        assertTrue(log.at(Level.SEVERE).isEmpty(), "nothing SEVERE: " + messages());
    }

    // ---------------------------------------------------------------- helpers

    private WasmRmiServerEngineTest.FakeSession open(WasmRmiServerEngineTest.FakeSession opened) {
        WasmRmiServerEngineTest.FakeEndpointConfig config =
                new WasmRmiServerEngineTest.FakeEndpointConfig();
        config.getUserProperties().put(RmiEndpointConfigurator.PRINCIPAL_KEY,
                (Principal) () -> "alice");
        config.getUserProperties().put(RmiEndpointConfigurator.ROLES_KEY, Set.of("user"));
        engine.onOpen(opened, config);
        return opened;
    }

    private byte[] call(int messageId, String method, Object... args) {
        GrowableBuffer buffer = new GrowableBuffer();
        buffer.putInt(messageId);
        BinarySerializer.writeString(buffer, SlowService.class.getName());
        BinarySerializer.writeString(buffer, method);
        buffer.putInt(args.length);
        for (Object arg : args) {
            BinarySerializer.writeValue(buffer, arg, mapper);
        }
        return buffer.toByteArray();
    }

    private LogRecord awaitLog(String text) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            LogRecord found = log.containing(text);
            if (found != null) {
                return found;
            }
            Thread.sleep(10);
        }
        return null;
    }

    private String messages() {
        List<String> lines = new ArrayList<>();
        for (LogRecord record : log.records) {
            lines.add(record.getLevel() + " " + record.getMessage());
        }
        return String.join(" | ", lines);
    }

    private static int responses(WasmRmiServerEngineTest.FakeSession s) {
        int count = 0;
        for (ByteBuffer frame : new ArrayList<>(Collections.unmodifiableList(s.basic.sentBuffers()))) {
            if (frame.limit() >= 5 && (frame.get(4) == SyncFrameTypes.RPC_RESPONSE
                    || frame.get(4) == SyncFrameTypes.RPC_ERROR)) {
                count++;
            }
        }
        return count;
    }
}
