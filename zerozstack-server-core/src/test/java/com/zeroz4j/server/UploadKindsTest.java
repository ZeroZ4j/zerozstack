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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Several handlers chosen by kind, a limit per kind, and big files that stay off the heap. */
class UploadKindsTest {

    private static final Principal DEMO = () -> "demo";

    @TempDir
    Path uploadTemp;

    private final List<String> received = new ArrayList<>();

    private FileUploadHandler named(String kind, long maxBytes) {
        return new FileUploadHandler() {
            @Override
            public String getKind() {
                return kind;
            }

            @Override
            public long getMaxBytes() {
                return maxBytes;
            }

            @Override
            public UploadResult onFileUploaded(UploadedFile file) {
                received.add(kind + ":" + file.getKind() + ":" + file.getSizeBytes());
                return UploadResult.accepted("ok");
            }
        };
    }

    @BeforeEach
    void setUp() {
        UploadPasses.clearForTesting();
        System.setProperty(UploadLimits.TEMP_DIR_PROPERTY, uploadTemp.toString());
        System.setProperty(UploadLimits.MAX_BYTES_PROPERTY, "1024");
        RmiRequestContext.setContext(DEMO, Set.of("user"), "session-1", null, null);
        UploadReceiver.setHandlersForTesting(List.of(named("", 0L), named("recording", 200L * 1024L * 1024L),
                named("avatar", 0L)));
    }

    @AfterEach
    void tearDown() {
        RmiRequestContext.clear();
        UploadPasses.clearForTesting();
        UploadReceiver.setHandlersForTesting(null);
        System.clearProperty(UploadLimits.TEMP_DIR_PROPERTY);
        System.clearProperty(UploadLimits.MAX_BYTES_PROPERTY);
        System.clearProperty("zeroz.upload.maxBytes.avatar");
    }

    @Test
    void thePageChoosesTheHandlerByKind() {
        send("", 10);
        send("recording", 20);
        send("avatar", 30);
        assertEquals(List.of("::10", "recording:recording:20", "avatar:avatar:30"), received);
    }

    @Test
    void aKindWithItsOwnLimitAcceptsWhatTheServerWideLimitRefuses() {
        ServerConfig config = ServerConfig.fromSystemProperties();
        // Over the server-wide 1024, under the recording handler's own limit.
        UploadPasses.issue(config, "recording", "talk.mp3", "audio/mpeg", 5L * 1024L * 1024L);
        // The kind with no limit of its own is held to the server-wide one.
        UploadRefusedException refused = assertThrows(UploadRefusedException.class,
                () -> UploadPasses.issue(config, "avatar", "me.png", "image/png", 5000L));
        assertEquals(413, refused.getStatus());
        // The limit applies to the default handler too.
        assertThrows(UploadRefusedException.class,
                () -> UploadPasses.issue(config, "", "a.bin", "application/octet-stream", 5000L));
    }

    @Test
    void thePropertyForAKindBeatsTheHandlersOwnLimit() {
        System.setProperty("zeroz.upload.maxBytes.avatar", "4096");
        ServerConfig config = ServerConfig.fromSystemProperties();
        assertEquals(4096L, UploadKinds.maxBytes(config, "avatar"));
        assertEquals(200L * 1024L * 1024L, UploadKinds.maxBytes(config, "recording"));
        assertEquals(1024L, UploadKinds.maxBytes(config, ""));
        assertEquals(200L * 1024L * 1024L, UploadKinds.largestMaxBytes(config));
    }

    @Test
    void anUnknownOrMalformedKindGetsNoPass() {
        ServerConfig config = ServerConfig.fromSystemProperties();
        assertEquals(404, assertThrows(UploadRefusedException.class,
                () -> UploadPasses.issue(config, "photos", "a.png", "image/png", 5L)).getStatus());
        assertEquals(404, assertThrows(UploadRefusedException.class,
                () -> UploadPasses.issue(config, "../etc", "a.png", "image/png", 5L)).getStatus());
    }

    @Test
    void theServerChecksTheKindLimitAgainstTheBytesItCounts() {
        ServerConfig config = ServerConfig.fromSystemProperties();
        // An honest pass for a small file, then a body that claims no length and sends more.
        UploadPass pass = UploadPasses.issue(config, "avatar", "me.png", "image/png", 10L);
        UploadOutcome outcome = UploadReceiver.receive(request(pass.getToken(), null, new Zeroes(5000)));
        assertEquals(413, outcome.getStatus());
        assertEquals(0, filesIn(uploadTemp), "nothing is left behind");
    }

    @Test
    void aFileFarLargerThanAnyBufferIsCopiedInSmallPiecesAndKept() {
        ServerConfig config = ServerConfig.fromSystemProperties();
        long size = 100L * 1024L * 1024L;
        UploadPass pass = UploadPasses.issue(config, "recording", "talk.mp3", "audio/mpeg", size);
        Zeroes body = new Zeroes(size);
        UploadOutcome outcome = UploadReceiver.receive(request(pass.getToken(), Long.toString(size), body));
        assertEquals(200, outcome.getStatus(), outcome.getMessage());
        assertEquals(List.of("recording:recording:" + size), received);
        assertTrue(body.largestRead <= 64 * 1024, "read in pieces of at most 64 KB, was " + body.largestRead);
        assertEquals(0, filesIn(uploadTemp), "the temporary file is deleted");
    }

    // ------------------------------------------------------------- helpers

    private void send(String kind, long size) {
        UploadPass pass = UploadPasses.issue(ServerConfig.fromSystemProperties(), kind, "f", "text/plain", size);
        UploadOutcome outcome = UploadReceiver.receive(request(pass.getToken(), Long.toString(size),
                new Zeroes(size)));
        assertEquals(200, outcome.getStatus(), outcome.getMessage());
    }

    private static UploadRequest request(String pass, String contentLength, InputStream body) {
        return new UploadRequest() {
            @Override
            public String header(String name) {
                if (UploadReceiver.PASS_HEADER.equalsIgnoreCase(name)) {
                    return pass;
                }
                return "Content-Length".equalsIgnoreCase(name) ? contentLength : null;
            }

            @Override
            public InputStream body() {
                return body;
            }
        };
    }

    private static long filesIn(Path folder) {
        try (Stream<Path> files = java.nio.file.Files.list(folder)) {
            return files.count();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** A body of zero bytes of a given size that is never held in memory. */
    private static final class Zeroes extends InputStream {
        private long remaining;
        int largestRead;

        Zeroes(long size) {
            this.remaining = size;
        }

        @Override
        public int read() {
            return remaining-- > 0 ? 0 : -1;
        }

        @Override
        public int read(byte[] buffer, int off, int len) {
            if (remaining <= 0) {
                return -1;
            }
            int count = (int) Math.min(len, remaining);
            largestRead = Math.max(largestRead, len);
            remaining -= count;
            return count;
        }
    }
}
