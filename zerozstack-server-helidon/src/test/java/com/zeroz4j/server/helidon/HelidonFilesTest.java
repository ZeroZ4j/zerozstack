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
package com.zeroz4j.server.helidon;

import com.zeroz4j.server.DownloadLinks;
import com.zeroz4j.server.DownloadRequest;
import com.zeroz4j.server.FileDownload;
import com.zeroz4j.server.FileDownloadHandler;
import com.zeroz4j.server.FileUploadHandler;
import com.zeroz4j.server.ServerConfig;
import com.zeroz4j.server.UploadPass;
import com.zeroz4j.server.UploadPasses;
import com.zeroz4j.server.UploadResult;
import com.zeroz4j.server.UploadedFile;
import com.zeroz4j.server.spi.ServerBinding;
import jakarta.enterprise.context.ApplicationScoped;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uploads of a named kind and files served back, against the real Helidon server over real sockets.
 * Passes and addresses are issued in this process, which is what the live connection does in a real
 * deployment; everything after that is plain HTTP.
 */
class HelidonFilesTest {

    static final CopyOnWriteArrayList<String> RECEIVED = new CopyOnWriteArrayList<>();
    static volatile Path clip;

    private static ServerBinding.RunningServer server;
    private static String host;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /** A named upload handler with a limit above the default. */
    @ApplicationScoped
    public static class Uploads implements FileUploadHandler {

        @Override
        public String getKind() {
            return "recording";
        }

        @Override
        public long getMaxBytes() {
            return 200L * 1024L * 1024L;
        }

        @Override
        public UploadResult onFileUploaded(UploadedFile file) {
            RECEIVED.add(file.getFileName() + ":" + file.getSizeBytes() + ":" + file.getKind());
            return UploadResult.accepted("Kept " + file.getFileName());
        }
    }

    /** Serves the clip. */
    @ApplicationScoped
    public static class Downloads implements FileDownloadHandler {

        @Override
        public String getKind() {
            return "recording";
        }

        @Override
        public FileDownload open(DownloadRequest request) {
            return "clip".equals(request.getId()) ? FileDownload.of(clip) : FileDownload.notFound();
        }
    }

    @BeforeAll
    static void start() throws Exception {
        int port;
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }
        server = new HelidonServerBinding().start(port, "helidon files test");
        host = "localhost:" + server.port();
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    @Test
    void aHundredMegabyteRecordingStreamsToTheHandlerOfItsKind() throws Exception {
        Path big = Files.createTempFile("zeroz4j-big", ".bin");
        try {
            long size = 100L * 1024L * 1024L;
            try (RandomAccessFile file = new RandomAccessFile(big.toFile(), "rw")) {
                file.setLength(size);
            }
            UploadPass pass = UploadPasses.issue(ServerConfig.fromSystemProperties(), "recording",
                    "talk.bin", "audio/mpeg", size);
            HttpResponse<String> response = HTTP.send(
                    HttpRequest.newBuilder(URI.create("http://" + host + "/zeroz4j-upload"))
                            .header("X-Zeroz4j-Upload-Pass", pass.getToken())
                            .POST(HttpRequest.BodyPublishers.ofFile(big)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            assertTrue(RECEIVED.contains("talk.bin:" + size + ":recording"), RECEIVED.toString());
        } finally {
            Files.deleteIfExists(big);
        }
    }

    @Test
    void aFileIsServedBackWithRanges() throws Exception {
        Path file = Files.createTempFile("zeroz4j-clip", ".mp3");
        Files.write(file, "0123456789abcdefghij".getBytes(StandardCharsets.US_ASCII));
        clip = file;
        try {
            String address = DownloadLinks.issue(ServerConfig.fromSystemProperties(), "recording", "clip");

            HttpResponse<byte[]> whole = fetch("GET", address, null);
            assertEquals(200, whole.statusCode());
            assertEquals("audio/mpeg", whole.headers().firstValue("Content-Type").orElse(""));
            assertEquals("0123456789abcdefghij", new String(whole.body(), StandardCharsets.US_ASCII));

            HttpResponse<byte[]> part = fetch("GET", address, "bytes=5-9");
            assertEquals(206, part.statusCode());
            assertEquals("bytes 5-9/20", part.headers().firstValue("Content-Range").orElse(""));
            assertEquals("56789", new String(part.body(), StandardCharsets.US_ASCII));

            HttpResponse<byte[]> head = fetch("HEAD", address, null);
            assertEquals(200, head.statusCode());
            // Helidon answers a HEAD with Content-Length 0 whatever the resource set; the type and the
            // range support still arrive, and a browser fetches media with ranged GETs, not HEAD.
            assertEquals("audio/mpeg", head.headers().firstValue("Content-Type").orElse(""));
            assertEquals("bytes", head.headers().firstValue("Accept-Ranges").orElse(""));
            assertEquals(0, head.body().length);

            assertEquals(416, fetch("GET", address, "bytes=50-60").statusCode());
            assertEquals(401, fetch("GET", "zeroz4j-download?pass=made-up", null).statusCode());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static HttpResponse<byte[]> fetch(String method, String address, String range) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://" + host + "/" + address))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (range != null) {
            builder.header("Range", range);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
