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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the upload address is allowed to do with disk, and what it must refuse to do.
 *
 * <p>Three of these guard things that would be silent if they broke: an unauthenticated request that
 * still gets a file written for it, a temporary file left behind by a handler that threw, and a file
 * name from the browser reaching the filesystem.</p>
 */
class UploadReceiverTest {

    private static final Principal DEMO = () -> "demo";

    @TempDir
    Path uploadTemp;

    @BeforeEach
    void setUp() {
        UploadPasses.clearForTesting();
        System.setProperty(UploadLimits.TEMP_DIR_PROPERTY, uploadTemp.toString());
        RmiRequestContext.setContext(DEMO, Set.of("user"), "session-1", null, null);
    }

    @AfterEach
    void tearDown() {
        RmiRequestContext.clear();
        UploadPasses.clearForTesting();
        UploadReceiver.setHandlersForTesting(null);
        System.clearProperty(UploadLimits.TEMP_DIR_PROPERTY);
        System.clearProperty(UploadLimits.MAX_BYTES_PROPERTY);
    }

    // ------------------------------------------------------------- no pass, no disk

    @Test
    void aRequestWithNoPassIsRefusedBeforeAnythingIsWritten() throws IOException {
        RecordingHandler handler = install();
        CountingStream body = new CountingStream(bytes(4096));

        UploadOutcome outcome = UploadReceiver.receive(request(null, null, "4096", body));

        assertEquals(401, outcome.getStatus());
        assertEquals(0, body.read, "the body must not be read at all without a valid pass");
        assertEquals(0, filesIn(uploadTemp), "no unauthenticated request may consume disk");
        assertFalse(handler.called);
    }

    @Test
    void aPassThatWasAlreadySpentIsRefusedBeforeAnythingIsWritten() throws IOException {
        install();
        UploadPass pass = UploadPasses.issue("a.txt", "text/plain", 4L);
        UploadReceiver.receive(request(pass.getToken(), null, "4", new ByteArrayInputStream(bytes(4))));

        CountingStream body = new CountingStream(bytes(4));
        UploadOutcome outcome = UploadReceiver.receive(request(pass.getToken(), null, "4", body));

        assertEquals(401, outcome.getStatus());
        assertEquals(0, body.read);
        assertEquals(0, filesIn(uploadTemp));
    }

    // ------------------------------------------------------------- size

    @Test
    void anOversizedUploadIsRefusedFromItsDeclaredLengthWithoutReadingIt() throws IOException {
        System.setProperty(UploadLimits.MAX_BYTES_PROPERTY, "1024");
        install();
        // The pass itself was issued honestly; the request is the thing that overreaches.
        UploadPass pass = UploadPasses.issue("a.bin", "application/octet-stream", 1024L);
        CountingStream body = new CountingStream(bytes(4096));

        UploadOutcome outcome = UploadReceiver.receive(request(pass.getToken(), null, "4096", body));

        assertEquals(413, outcome.getStatus());
        assertTrue(outcome.getMessage().contains("too big"), outcome.getMessage());
        assertEquals(0, body.read, "an oversized upload must not be transferred to be refused");
        assertEquals(0, filesIn(uploadTemp));
    }

    @Test
    void anOversizedUploadIsRefusedAgainWhenTheDeclarationLied() throws IOException {
        System.setProperty(UploadLimits.MAX_BYTES_PROPERTY, "1024");
        RecordingHandler handler = install();
        UploadPass pass = UploadPasses.issue("a.bin", "application/octet-stream", 1024L);
        // Says 1024, sends 8192, and there is no Content-Length to catch it either.
        CountingStream body = new CountingStream(bytes(8192));

        UploadOutcome outcome = UploadReceiver.receive(request(pass.getToken(), null, null, body));

        assertEquals(413, outcome.getStatus());
        assertFalse(handler.called, "a file over the limit must never reach the application");
        assertTrue(body.read <= 1024 + 64 * 1024,
                "reading must stop at the limit rather than draining the request");
        assertEquals(0, filesIn(uploadTemp), "the part-written file must be cleaned up");
    }

    @Test
    void anIncompleteUploadIsRefusedAndNeverReachesTheApplication() throws IOException {
        RecordingHandler handler = install();
        UploadPass pass = UploadPasses.issue("a.txt", "text/plain", 100L);

        // What a cancelled upload looks like: fewer bytes than were promised.
        UploadOutcome outcome = UploadReceiver.receive(request(pass.getToken(), null, "100",
                new ByteArrayInputStream(bytes(40))));

        assertEquals(400, outcome.getStatus());
        assertFalse(handler.called);
        assertEquals(0, filesIn(uploadTemp));
    }

    // ------------------------------------------------------------- hostile names

    @Test
    void aHostileFileNameCannotInfluenceWhereAnythingIsWritten() throws IOException {
        AtomicReference<Path> seen = new AtomicReference<>();
        AtomicReference<String> seenName = new AtomicReference<>();
        UploadReceiver.setHandlersForTesting(List.of(file -> {
            seen.set(file.getTempFile());
            seenName.set(file.getFileName());
            return UploadResult.accepted("Saved.");
        }));

        List<String> hostile = List.of(
                "../../etc/passwd",
                "..\\..\\Windows\\System32\\drivers\\etc\\hosts",
                "/etc/shadow",
                "C:\\Windows\\win.ini",
                "CON",
                "NUL.txt",
                "with\u0000null.txt",
                "with\nnewline.txt");

        for (String name : hostile) {
            UploadPass pass = UploadPasses.issue(name, "text/plain", 4L);
            UploadOutcome outcome = UploadReceiver.receive(request(pass.getToken(), null, "4",
                    new ByteArrayInputStream(bytes(4))));

            assertEquals(200, outcome.getStatus(), name);
            Path written = seen.get();
            assertNotNull(written, name);
            assertEquals(uploadTemp.toAbsolutePath().normalize(),
                    written.getParent().toAbsolutePath().normalize(),
                    "the temporary file must be inside the configured directory: " + name);
            assertTrue(written.getFileName().toString().startsWith("zeroz4j-"),
                    "the framework names the file, not the browser: " + written.getFileName());
            assertTrue(written.getFileName().toString().endsWith(".upload"),
                    "the framework names the file, not the browser: " + written.getFileName());
            assertEquals(name, seenName.get(),
                    "the reported name is passed through verbatim as information");
        }

        assertFalse(Files.exists(Paths.get("etc", "passwd")));
        assertEquals(0, filesIn(uploadTemp), "every temporary file is deleted afterwards");
    }

    // ------------------------------------------------------------- cleanup

    @Test
    void theTemporaryFileIsDeletedWhenTheHandlerThrows() throws IOException {
        UploadReceiver.setHandlersForTesting(List.of(file -> {
            assertTrue(Files.exists(file.getTempFile()), "the handler is given a real file");
            throw new IllegalStateException("the database was down");
        }));
        UploadPass pass = UploadPasses.issue("a.txt", "text/plain", 16L);

        UploadOutcome outcome = UploadReceiver.receive(request(pass.getToken(), null, "16",
                new ByteArrayInputStream(bytes(16))));

        assertEquals(500, outcome.getStatus());
        assertFalse(outcome.getMessage().contains("database"),
                "an internal fault must not be described to the browser: " + outcome.getMessage());
        assertEquals(0, filesIn(uploadTemp));
    }

    @Test
    void theTemporaryFileIsDeletedWhenTheHandlerSucceeds() throws IOException {
        UploadReceiver.setHandlersForTesting(List.of(file -> UploadResult.accepted("Saved.")));
        UploadPass pass = UploadPasses.issue("a.txt", "text/plain", 16L);

        UploadOutcome outcome = UploadReceiver.receive(request(pass.getToken(), null, "16",
                new ByteArrayInputStream(bytes(16))));

        assertEquals(200, outcome.getStatus());
        assertEquals("Saved.", outcome.getMessage());
        assertEquals(0, filesIn(uploadTemp));
    }

    // ------------------------------------------------------------- the ordinary path

    @Test
    void aCompleteFileReachesTheApplicationWithItsIdentity() throws IOException {
        AtomicReference<UploadedFile> received = new AtomicReference<>();
        AtomicReference<String> contents = new AtomicReference<>();
        UploadReceiver.setHandlersForTesting(List.of(file -> {
            received.set(file);
            contents.set(Files.readString(file.getTempFile(), StandardCharsets.UTF_8));
            return UploadResult.accepted("Saved.");
        }));

        byte[] payload = "hello upload".getBytes(StandardCharsets.UTF_8);
        UploadPass pass = UploadPasses.issue("notes.txt", "text/plain", payload.length);

        UploadOutcome outcome = UploadReceiver.receive(request(pass.getToken(), null,
                Integer.toString(payload.length), new ByteArrayInputStream(payload)));

        assertEquals(200, outcome.getStatus());
        UploadedFile file = received.get();
        assertEquals("hello upload", contents.get());
        assertEquals("notes.txt", file.getFileName());
        assertEquals("text/plain", file.getContentType());
        assertEquals(payload.length, file.getSizeBytes());
        assertEquals(DEMO, file.getPrincipal());
        assertEquals(Set.of("user"), file.getRoles());
        assertEquals("session-1", file.getSessionId());
    }

    @Test
    void theBrowserIdentityCookieIsWhatBindsThePassToThisBrowser() throws IOException {
        install();
        String token = ClientIdentity.issue();
        String clientId = ClientIdentity.verify(token);
        RmiRequestContext.setContext(DEMO, Set.of("user"), "session-1", null, clientId);

        UploadPass pass = UploadPasses.issue("a.txt", "text/plain", 4L);

        UploadOutcome refused = UploadReceiver.receive(request(pass.getToken(),
                ClientIdentity.COOKIE_NAME + "=" + ClientIdentity.issue(), "4",
                new ByteArrayInputStream(bytes(4))));
        assertEquals(403, refused.getStatus());
        assertEquals(0, filesIn(uploadTemp));

        UploadPass second = UploadPasses.issue("a.txt", "text/plain", 4L);
        UploadOutcome accepted = UploadReceiver.receive(request(second.getToken(),
                ClientIdentity.COOKIE_NAME + "=" + token, "4", new ByteArrayInputStream(bytes(4))));
        assertEquals(200, accepted.getStatus());
    }

    @Test
    void anApplicationWithNoHandlerRefusesRatherThanKeepingTheFile() throws IOException {
        UploadReceiver.setHandlersForTesting(new ArrayList<>());
        UploadPass pass = UploadPasses.issue("a.txt", "text/plain", 4L);

        UploadOutcome outcome = UploadReceiver.receive(request(pass.getToken(), null, "4",
                new ByteArrayInputStream(bytes(4))));

        assertEquals(501, outcome.getStatus());
        assertEquals(0, filesIn(uploadTemp));
    }

    @Test
    void aRejectingHandlerIsShownToThePersonWhoUploaded() throws IOException {
        UploadReceiver.setHandlersForTesting(List.of(file ->
                UploadResult.rejected("That is not a picture. Please choose a JPEG or a PNG.")));
        UploadPass pass = UploadPasses.issue("virus.exe", "application/octet-stream", 4L);

        UploadOutcome outcome = UploadReceiver.receive(request(pass.getToken(), null, "4",
                new ByteArrayInputStream(bytes(4))));

        assertEquals(422, outcome.getStatus());
        assertEquals("That is not a picture. Please choose a JPEG or a PNG.", outcome.getMessage());
        assertEquals(0, filesIn(uploadTemp));
    }

    // ------------------------------------------------------------- origin

    @Test
    void aPageOnAnotherSiteCannotPostFilesHere() throws IOException {
        RecordingHandler handler = install();
        UploadPass pass = UploadPasses.issue("a.txt", "text/plain", 4L);
        CountingStream body = new CountingStream(bytes(4));

        // A browser attaches this deployment's cookies to any request to it, including one started by
        // a page the victim happens to be visiting. Same rule the WebSocket handshake applies.
        UploadOutcome outcome = UploadReceiver.receive(request(pass.getToken(), null, "4", body,
                "https://evil.example.com", "localhost:8080"));

        assertEquals(403, outcome.getStatus());
        assertEquals(0, body.read, "the origin is checked before anything is read");
        assertEquals(0, filesIn(uploadTemp));
        assertFalse(handler.called);
    }

    @Test
    void aPageOnThisSiteCan() throws IOException {
        install();
        UploadPass pass = UploadPasses.issue("a.txt", "text/plain", 4L);

        UploadOutcome outcome = UploadReceiver.receive(request(pass.getToken(), null, "4",
                new ByteArrayInputStream(bytes(4)), "https://localhost:8080", "localhost:8080"));

        assertEquals(200, outcome.getStatus());
    }

    // ------------------------------------------------------------- helpers

    /**
     * A request carrying exactly the headers a test is about.
     *
     * <p>No {@code Origin}, because {@link OriginPolicy} treats its absence as a non-browser caller —
     * which is what these tests are. The origin rule has its own test below.</p>
     */
    private static UploadRequest request(String pass, String cookie, String contentLength,
                                         InputStream body) {
        return request(pass, cookie, contentLength, body, null, null);
    }

    private static UploadRequest request(String pass, String cookie, String contentLength,
                                         InputStream body, String origin, String host) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(UploadReceiver.PASS_HEADER, pass);
        headers.put("Cookie", cookie);
        headers.put("Content-Length", contentLength);
        headers.put("Origin", origin);
        headers.put("Host", host);
        return new UploadRequest() {

            @Override
            public String header(String name) {
                return headers.get(name);
            }

            @Override
            public InputStream body() {
                return body;
            }
        };
    }

    private RecordingHandler install() {
        RecordingHandler handler = new RecordingHandler();
        UploadReceiver.setHandlersForTesting(List.of(handler));
        return handler;
    }

    private static byte[] bytes(int count) {
        byte[] payload = new byte[count];
        for (int i = 0; i < count; i++) {
            payload[i] = (byte) ('a' + (i % 26));
        }
        return payload;
    }

    private static long filesIn(Path directory) throws IOException {
        try (Stream<Path> tree = Files.list(directory)) {
            return tree.count();
        }
    }

    /** Counts what was actually pulled off the wire, which is how "refused early" is proved. */
    private static final class CountingStream extends InputStream {
        private final ByteArrayInputStream delegate;
        int read;

        CountingStream(byte[] payload) {
            this.delegate = new ByteArrayInputStream(payload);
        }

        @Override
        public int read() {
            int value = delegate.read();
            if (value >= 0) {
                read++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                read += count;
            }
            return count;
        }
    }

    private static final class RecordingHandler implements FileUploadHandler {
        boolean called;

        @Override
        public UploadResult onFileUploaded(UploadedFile file) {
            called = true;
            return UploadResult.accepted("Saved.");
        }
    }
}
