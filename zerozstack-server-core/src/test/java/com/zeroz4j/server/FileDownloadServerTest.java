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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The download address: who may fetch, what is sent, and how byte ranges are answered. */
class FileDownloadServerTest {

    private static final Principal DEMO = () -> "demo";
    private static final byte[] DATA = "0123456789abcdefghij".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path folder;

    private Path audio;
    private Path page;
    private Path emptyFile;
    private Principal seenPrincipal;

    @BeforeEach
    void setUp() throws IOException {
        DownloadLinks.clearForTesting();
        audio = folder.resolve("talk.mp3");
        Files.write(audio, DATA);
        page = folder.resolve("page.html");
        Files.writeString(page, "<script>alert(1)</script>");
        emptyFile = folder.resolve("empty.mp3");
        Files.write(emptyFile, new byte[0]);
        RmiRequestContext.setContext(DEMO, Set.of("user"), "session-1", null, null);
        DownloadLinks.setHandlersForTesting(List.of(new FileDownloadHandler() {
            @Override
            public String getKind() {
                return "recording";
            }

            @Override
            public FileDownload open(DownloadRequest request) {
                seenPrincipal = request.getPrincipal();
                switch (request.getId()) {
                    case "talk": return FileDownload.of(audio);
                    case "page": return FileDownload.of(page);
                    case "empty": return FileDownload.of(emptyFile);
                    case "named": return FileDownload.of(audio).withFileName("a \"b\"\r\nSet-Cookie: x/ü.mp3");
                    case "forced": return FileDownload.of(audio).asAttachment();
                    case "secret": return FileDownload.forbidden();
                    case "boom": throw new IllegalStateException("database is down");
                    default: return FileDownload.notFound();
                }
            }
        }));
    }

    @AfterEach
    void tearDown() {
        RmiRequestContext.clear();
        DownloadLinks.setHandlersForTesting(null);
        DownloadLinks.clearForTesting();
    }

    // ------------------------------------------------------------- the address

    @Test
    void anUnknownKindOrAnEmptyIdIsRefusedWhenTheAddressIsRequested() {
        ServerConfig config = ServerConfig.fromSystemProperties();
        assertEquals(404, assertThrows(UploadRefusedException.class,
                () -> DownloadLinks.issue(config, "nope", "talk")).getStatus());
        assertEquals(400, assertThrows(UploadRefusedException.class,
                () -> DownloadLinks.issue(config, "recording", "")).getStatus());
    }

    @Test
    void aMissingOrMadeUpPassIsRefusedAndReachesNoHandler() {
        assertEquals(401, get(null, null).getStatus());
        assertEquals(401, get("made-up", null).getStatus());
        assertNull(seenPrincipal, "no handler ran");
    }

    @Test
    void aPassIssuedToAnotherBrowserIsRefused() {
        RmiRequestContext.setContext(DEMO, Set.of("user"), "session-1", null, "browser-a");
        String pass = pass("talk");
        // The request carries no cookie for browser-a.
        assertEquals(401, get(pass, null).getStatus());
    }

    @Test
    void theHandlerSeesTheCallerTheAddressWasIssuedTo() {
        String pass = pass("talk");
        RmiRequestContext.clear();
        assertEquals(200, get(pass, null).getStatus());
        assertEquals(DEMO, seenPrincipal);
    }

    @Test
    void forbiddenNotFoundAndAThrowingHandlerAreAnsweredWithTheirOwnStatus() {
        assertEquals(403, get(pass("secret"), null).getStatus());
        assertEquals(404, get(pass("missing"), null).getStatus());
        DownloadResponse failed = get(pass("boom"), null);
        assertEquals(500, failed.getStatus());
        assertTrue(text(failed).contains("went wrong"));
        assertTrue(!text(failed).contains("database"), "the exception is not shown to the caller");
    }

    // ------------------------------------------------------------- whole file

    @Test
    void aWholeFileIsSentWithTypeLengthAndTheSignWeAcceptRanges() throws IOException {
        DownloadResponse response = get(pass("talk"), null);
        assertEquals(200, response.getStatus());
        assertEquals("audio/mpeg", response.getHeaders().get("Content-Type"));
        assertEquals("20", response.getHeaders().get("Content-Length"));
        assertEquals("bytes", response.getHeaders().get("Accept-Ranges"));
        assertTrue(response.getHeaders().get("Content-Disposition").startsWith("inline;"));
        assertEquals("nosniff", response.getHeaders().get("X-Content-Type-Options"));
        assertArrayEquals(DATA, body(response));
    }

    @Test
    void headCarriesTheHeadersAndNoBody() throws IOException {
        DownloadResponse response = respond("HEAD", pass("talk"), null);
        assertEquals(200, response.getStatus());
        assertEquals("20", response.getHeaders().get("Content-Length"));
        assertEquals(0, body(response).length);
    }

    @Test
    void aPageIsOnlyEverSentAsADownload() {
        DownloadResponse response = get(pass("page"), null);
        assertTrue(response.getHeaders().get("Content-Disposition").startsWith("attachment;"),
                response.getHeaders().get("Content-Disposition"));
        assertTrue(response.getHeaders().get("Content-Type").startsWith("text/html"));
    }

    @Test
    void anAttachmentCanBeAskedFor() {
        assertTrue(get(pass("forced"), null).getHeaders().get("Content-Disposition")
                .startsWith("attachment;"));
    }

    @Test
    void aFileNameCannotInjectAHeaderOrAPath() {
        String disposition = get(pass("named"), null).getHeaders().get("Content-Disposition");
        assertTrue(!disposition.contains("\r") && !disposition.contains("\n"), disposition);
        assertTrue(disposition.contains("filename=\"a _b_Set-Cookie: x__.mp3\""), disposition);
        assertTrue(disposition.contains("filename*=UTF-8''"), disposition);
        assertTrue(disposition.contains("%C3%BC"), disposition);
    }

    @Test
    void anEmptyFileIsSentAsAnEmptyBody() throws IOException {
        DownloadResponse response = get(pass("empty"), "bytes=0-");
        assertEquals(200, response.getStatus());
        assertEquals("0", response.getHeaders().get("Content-Length"));
        assertEquals(0, body(response).length);
    }

    // ------------------------------------------------------------- ranges

    @Test
    void aClosedRangeSendsOnlyThoseBytes() throws IOException {
        DownloadResponse response = get(pass("talk"), "bytes=5-9");
        assertEquals(206, response.getStatus());
        assertEquals("bytes 5-9/20", response.getHeaders().get("Content-Range"));
        assertEquals("5", response.getHeaders().get("Content-Length"));
        assertEquals("56789", new String(body(response), StandardCharsets.US_ASCII));
    }

    @Test
    void anOpenEndedRangeRunsToTheEndAndAnOverlongOneIsTrimmed() throws IOException {
        assertEquals("fghij", new String(body(get(pass("talk"), "bytes=15-")), StandardCharsets.US_ASCII));
        DownloadResponse trimmed = get(pass("talk"), "bytes=18-500");
        assertEquals("bytes 18-19/20", trimmed.getHeaders().get("Content-Range"));
        assertEquals("ij", new String(body(trimmed), StandardCharsets.US_ASCII));
    }

    @Test
    void aSuffixRangeSendsTheLastBytes() throws IOException {
        DownloadResponse response = get(pass("talk"), "bytes=-3");
        assertEquals(206, response.getStatus());
        assertEquals("hij", new String(body(response), StandardCharsets.US_ASCII));
    }

    @Test
    void aRangeStartingPastTheEndIsAnswered416() {
        DownloadResponse response = get(pass("talk"), "bytes=20-30");
        assertEquals(416, response.getStatus());
        assertEquals("bytes */20", response.getHeaders().get("Content-Range"));
    }

    @Test
    void rangesWeDoNotAnswerPartiallyGetTheWholeFile() throws IOException {
        for (String header : List.of("bytes=0-1,5-6", "items=0-1", "bytes=x-y", "bytes=9-3", "garbage")) {
            DownloadResponse response = get(pass("talk"), header);
            assertEquals(200, response.getStatus(), header);
            assertEquals(20, body(response).length, header);
        }
    }

    @Test
    void aRangeIsIgnoredWhenIfRangeNamesAnotherVersion() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Range", "bytes=0-4");
        headers.put("If-Range", "\"something-else\"");
        assertEquals(200, respond("GET", pass("talk"), headers).getStatus());
        headers.put("If-Range", get(pass("talk"), null).getHeaders().get("ETag"));
        assertEquals(206, respond("GET", pass("talk"), headers).getStatus());
    }

    @Test
    void aMatchingEtagIsAnswered304() {
        String etag = get(pass("talk"), null).getHeaders().get("ETag");
        Map<String, String> headers = new HashMap<>();
        headers.put("If-None-Match", etag);
        assertEquals(304, respond("GET", pass("talk"), headers).getStatus());
    }

    @Test
    void otherMethodsAreRefused() {
        DownloadResponse response = respond("POST", pass("talk"), null);
        assertEquals(405, response.getStatus());
        assertEquals("GET, HEAD", response.getHeaders().get("Allow"));
    }

    // ------------------------------------------------------------- helpers

    private String pass(String id) {
        String address = DownloadLinks.issue(ServerConfig.fromSystemProperties(), "recording", id);
        assertTrue(address.startsWith("zeroz4j-download?pass="), address);
        return address.substring(address.indexOf('=') + 1);
    }

    private DownloadResponse get(String pass, String range) {
        Map<String, String> headers = new HashMap<>();
        if (range != null) {
            headers.put("Range", range);
        }
        return respond("GET", pass, headers);
    }

    private DownloadResponse respond(String method, String pass, Map<String, String> headers) {
        Map<String, String> sent = headers == null ? Map.of() : headers;
        return FileDownloadServer.respond(new DownloadHttpRequest() {
            @Override
            public String method() {
                return method;
            }

            @Override
            public String header(String name) {
                return sent.get(name);
            }

            @Override
            public String queryParameter(String name) {
                return "pass".equals(name) ? pass : null;
            }
        });
    }

    private static byte[] body(DownloadResponse response) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.writeBody(out);
        return out.toByteArray();
    }

    private static String text(DownloadResponse response) {
        try {
            return new String(body(response), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
