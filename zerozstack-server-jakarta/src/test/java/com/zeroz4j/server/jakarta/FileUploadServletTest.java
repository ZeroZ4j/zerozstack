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
package com.zeroz4j.server.jakarta;

import com.zeroz4j.server.FileUploadHandler;
import com.zeroz4j.server.RmiRequestContext;
import com.zeroz4j.server.UploadLimits;
import com.zeroz4j.server.UploadPass;
import com.zeroz4j.server.UploadPasses;
import com.zeroz4j.server.UploadReceiver;
import com.zeroz4j.server.UploadResult;
import com.zeroz4j.server.UploadedFile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The WAR half of file upload.
 *
 * <p>The framework supports two deployment shapes and this feature has an entry point in each. The
 * risk worth testing is not that the bytes get written — that is {@code UploadReceiverTest}'s job,
 * against the receiver both entry points share — but that <b>this</b> entry point reaches that
 * receiver at all, and answers with the same status and the same sentence a standalone server would.
 * A servlet that refused a missing pass with a different code than the JAX-RS resource is a bug
 * nobody finds until they deploy to the other container.</p>
 *
 * <p>Two halves are supplied differently, on purpose. The servlet request and response are faked
 * with a {@link Proxy}, the same way {@link ShellServletContextRootTest} does it — the servlet uses
 * two methods of the request and four of the response, which is far cheaper than a container and
 * proves nothing less. <b>CDI is real</b>: the handler is an actual {@code @ApplicationScoped} bean
 * in an actual Weld container, because finding the application's handler is the one integration a
 * WAR genuinely depends on, and a faked lookup would have left it untested.</p>
 *
 * <p>What this does <em>not</em> cover is a real servlet container routing a real socket to this
 * class. The mapping is asserted from the annotation rather than by deploying a WAR.</p>
 */
@EnableWeld
class FileUploadServletTest {

    private static final Principal DEMO = () -> "demo";

    /**
     * A real CDI container holding a real handler, because how the receiver finds the application's
     * handler is part of what this entry point has to get right. Faking that lookup would leave the
     * one integration a WAR actually depends on untested.
     */
    @WeldSetup
    public WeldInitiator weld = WeldInitiator.of(RecordingHandler.class);

    /** Stands in for the application's own handler. */
    @ApplicationScoped
    public static class RecordingHandler implements FileUploadHandler {

        /** Set per test, because Weld owns this instance and the test only reaches it statically. */
        static volatile UploadResult answer = UploadResult.accepted("Saved.");
        static volatile UploadedFile lastSeen;

        @Override
        public UploadResult onFileUploaded(UploadedFile file) {
            lastSeen = file;
            return answer;
        }
    }

    @TempDir
    Path uploadTemp;

    private final FileUploadServlet servlet = new FileUploadServlet();

    @BeforeEach
    void setUp() {
        System.setProperty(UploadLimits.TEMP_DIR_PROPERTY, uploadTemp.toString());
        RmiRequestContext.setContext(DEMO, Set.of("user"), "session-1", null, null);
        RecordingHandler.answer = UploadResult.accepted("Saved.");
        RecordingHandler.lastSeen = null;
    }

    @AfterEach
    void tearDown() {
        RmiRequestContext.clear();
        System.clearProperty(UploadLimits.TEMP_DIR_PROPERTY);
        System.clearProperty(UploadLimits.MAX_BYTES_PROPERTY);
    }

    // ------------------------------------------------------- it is reachable at all

    @Test
    void itMapsItselfAtTheOnePathTheBrowserWillAskFor() {
        WebServlet mapping = FileUploadServlet.class.getAnnotation(WebServlet.class);

        assertEquals(1, mapping.urlPatterns().length);
        assertEquals("/" + UploadReceiver.UPLOAD_PATH, mapping.urlPatterns()[0],
                "the client builds this address from the shell's base href; any other mapping is a "
                        + "silent 404 on every upload");
    }

    // ------------------------------------------------------- the same refusals

    @Test
    void aRequestWithNoPassIsRefusedBeforeAnythingIsWritten() throws Exception {
        Counted body = new Counted(bytes(4096));

        Recorded response = post(headers(null, "4096"), body);

        assertEquals(401, response.status);
        assertEquals("We could not accept that file. Reload the page and try again.", response.body());
        assertEquals("text/plain;charset=UTF-8", response.contentType);
        assertEquals(0, body.read, "the body must not be read at all without a valid pass");
        assertEquals(0, filesIn(uploadTemp), "no unauthenticated request may consume disk");
    }

    @Test
    void aPassThatWasAlreadySpentIsRefusedTheSameWay() throws Exception {
        UploadPass pass = UploadPasses.issue("a.txt", "text/plain", 4L);
        assertEquals(200, post(headers(pass.getToken(), "4"), new Counted(bytes(4))).status);

        Counted body = new Counted(bytes(4));
        Recorded response = post(headers(pass.getToken(), "4"), body);

        assertEquals(401, response.status);
        assertEquals("We could not accept that file. Reload the page and try again.", response.body());
        assertEquals(0, body.read);
        assertEquals(0, filesIn(uploadTemp));
    }

    @Test
    void anOversizedDeclaredLengthIsRefusedBeforeTheBodyIsRead() throws Exception {
        System.setProperty(UploadLimits.MAX_BYTES_PROPERTY, "1024");
        UploadPass pass = UploadPasses.issue("a.bin", "application/octet-stream", 1024L);
        Counted body = new Counted(bytes(4096));

        Recorded response = post(headers(pass.getToken(), "4096"), body);

        assertEquals(413, response.status);
        assertEquals("That file is too big. The largest we can take is 1 KB.", response.body());
        assertEquals(0, body.read, "an oversized upload must not be transferred to be refused");
        assertEquals(0, filesIn(uploadTemp));
    }

    @Test
    void aPageOnAnotherSiteIsRefusedBeforeTheBodyIsRead() throws Exception {
        UploadPass pass = UploadPasses.issue("a.txt", "text/plain", 4L);
        Counted body = new Counted(bytes(4));
        Map<String, String> headers = headers(pass.getToken(), "4");
        headers.put("Origin", "https://evil.example.com");
        headers.put("Host", "localhost:8080");

        Recorded response = post(headers, body);

        assertEquals(403, response.status);
        assertEquals(0, body.read);
        assertEquals(0, filesIn(uploadTemp));
    }

    // ------------------------------------------------------- and the ordinary path

    @Test
    void aCompleteFileIsAcceptedAndTheHandlerSentenceIsTheResponseBody() throws Exception {
        RecordingHandler.answer = UploadResult.accepted("Saved to your documents.");
        byte[] payload = "hello from a WAR".getBytes(StandardCharsets.UTF_8);
        UploadPass pass = UploadPasses.issue("notes.txt", "text/plain", payload.length);

        Recorded response = post(headers(pass.getToken(), Integer.toString(payload.length)),
                new Counted(payload));

        assertEquals(200, response.status);
        assertEquals("Saved to your documents.", response.body());
        assertEquals("notes.txt", RecordingHandler.lastSeen.getFileName());
        assertEquals(payload.length, RecordingHandler.lastSeen.getSizeBytes());
        assertEquals(DEMO, RecordingHandler.lastSeen.getPrincipal(),
                "the identity from the live connection reaches the handler through a WAR too");
        assertEquals(0, filesIn(uploadTemp), "the temporary file is deleted afterwards");
    }

    @Test
    void aRejectedFileCarriesTheHandlerReasonRatherThanAContainerErrorPage() throws Exception {
        RecordingHandler.answer =
                UploadResult.rejected("That is not a picture. Please choose a JPEG or a PNG.");
        UploadPass pass = UploadPasses.issue("virus.exe", "application/octet-stream", 4L);

        Recorded response = post(headers(pass.getToken(), "4"), new Counted(bytes(4)));

        assertEquals(422, response.status);
        // sendError would have replaced this with the container's HTML page, and the upload box shows
        // the body verbatim.
        assertEquals("That is not a picture. Please choose a JPEG or a PNG.", response.body());
        assertEquals("text/plain;charset=UTF-8", response.contentType);
    }

    // ------------------------------------------------------- fake container

    private static Map<String, String> headers(String pass, String contentLength) {
        Map<String, String> headers = new HashMap<>();
        headers.put(UploadReceiver.PASS_HEADER, pass);
        headers.put("Content-Length", contentLength);
        return headers;
    }

    private Recorded post(Map<String, String> headers, InputStream body) throws Exception {
        Recorded recorded = new Recorded();
        servlet.doPost(fakeRequest(headers, body), fakeResponse(recorded));
        return recorded;
    }

    private static HttpServletRequest fakeRequest(Map<String, String> headers, InputStream body) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getHeader" -> headers.get((String) args[0]);
            case "getInputStream" -> asServletStream(body);
            case "toString" -> "fake request";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> defaultFor(method.getReturnType());
        };
        return (HttpServletRequest) Proxy.newProxyInstance(
                FileUploadServletTest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, handler);
    }

    private static HttpServletResponse fakeResponse(Recorded recorded) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "setStatus" -> {
                recorded.status = (Integer) args[0];
                yield null;
            }
            case "setContentType" -> {
                recorded.contentType = (String) args[0];
                yield null;
            }
            case "setContentLength" -> {
                recorded.contentLength = (Integer) args[0];
                yield null;
            }
            case "getOutputStream" -> recorded.out;
            case "toString" -> "fake response";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> defaultFor(method.getReturnType());
        };
        return (HttpServletResponse) Proxy.newProxyInstance(
                FileUploadServletTest.class.getClassLoader(),
                new Class<?>[] { HttpServletResponse.class }, handler);
    }

    private static Object defaultFor(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == void.class) {
            return null;
        }
        return 0;
    }

    private static ServletInputStream asServletStream(InputStream delegate) {
        return new ServletInputStream() {

            @Override
            public int read() throws IOException {
                return delegate.read();
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                return delegate.read(buffer, offset, length);
            }

            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(jakarta.servlet.ReadListener listener) {
                throw new UnsupportedOperationException();
            }
        };
    }

    /** What the servlet told the container. */
    private static final class Recorded {
        int status;
        String contentType;
        int contentLength;
        final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        final ServletOutputStream out = new ServletOutputStream() {

            @Override
            public void write(int b) {
                captured.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
                throw new UnsupportedOperationException();
            }
        };

        String body() {
            return captured.toString(StandardCharsets.UTF_8);
        }
    }

    /** Counts what was actually pulled off the wire, which is how "refused early" is proved. */
    private static final class Counted extends InputStream {
        private final ByteArrayInputStream delegate;
        int read;

        Counted(byte[] payload) {
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
}
