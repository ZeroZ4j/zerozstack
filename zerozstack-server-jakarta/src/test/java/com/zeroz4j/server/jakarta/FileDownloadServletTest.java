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

import com.zeroz4j.server.DownloadLinks;
import com.zeroz4j.server.DownloadRequest;
import com.zeroz4j.server.FileDownload;
import com.zeroz4j.server.FileDownloadHandler;
import com.zeroz4j.server.FileDownloadServer;
import com.zeroz4j.server.RmiRequestContext;
import com.zeroz4j.server.ServerConfig;
import jakarta.enterprise.context.ApplicationScoped;
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

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The WAR half of serving a file back. The rules are tested against {@code FileDownloadServer} in
 * the core module; what is tested here is that this entry point reaches them, maps itself at the
 * address the browser asks for, and writes the status, headers and bytes the way a container needs
 * them. CDI is real, because finding the application's handler is what a WAR depends on.
 */
@EnableWeld
class FileDownloadServletTest {

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.of(Recordings.class);

    /** Stands in for the application's handler. */
    @ApplicationScoped
    public static class Recordings implements FileDownloadHandler {

        static volatile Path file;

        @Override
        public String getKind() {
            return "recording";
        }

        @Override
        public FileDownload open(DownloadRequest request) {
            return "talk".equals(request.getId()) ? FileDownload.of(file) : FileDownload.notFound();
        }
    }

    @TempDir
    Path folder;

    private final FileDownloadServlet servlet = new FileDownloadServlet();

    @BeforeEach
    void setUp() throws Exception {
        Recordings.file = folder.resolve("talk.mp3");
        Files.write(Recordings.file, "0123456789abcdefghij".getBytes(StandardCharsets.US_ASCII));
        RmiRequestContext.setContext(() -> "demo", Set.of("user"), "session-1", null, null);
    }

    @AfterEach
    void tearDown() {
        RmiRequestContext.clear();
    }

    @Test
    void itMapsItselfAtTheAddressTheBrowserAsksFor() {
        WebServlet mapping = FileDownloadServlet.class.getAnnotation(WebServlet.class);
        assertEquals("/" + FileDownloadServer.DOWNLOAD_PATH, mapping.urlPatterns()[0]);
    }

    @Test
    void aRequestWithoutAnAddressTokenIsRefused() throws Exception {
        Recorded response = call("GET", null, null);
        assertEquals(401, response.status);
    }

    @Test
    void aRangeIsAnsweredWithJustThoseBytes() throws Exception {
        Recorded response = call("GET", pass("talk"), "bytes=5-9");
        assertEquals(206, response.status);
        assertEquals("56789", response.body());
        assertEquals("bytes 5-9/20", response.headers.get("Content-Range"));
        assertEquals("audio/mpeg", response.contentType);
        assertEquals(5L, response.contentLength);
    }

    @Test
    void aHeadRequestCarriesTheLengthAndNoBody() throws Exception {
        Recorded response = call("HEAD", pass("talk"), null);
        assertEquals(200, response.status);
        assertEquals(20L, response.contentLength);
        assertEquals("", response.body());
        assertTrue(response.headers.get("Accept-Ranges").contains("bytes"));
    }

    @Test
    void anUnknownIdIsA404() throws Exception {
        assertEquals(404, call("GET", pass("nothing"), null).status);
    }

    // ------------------------------------------------------- fake container

    private static String pass(String id) {
        String address = DownloadLinks.issue(ServerConfig.fromSystemProperties(), "recording", id);
        return address.substring(address.indexOf('=') + 1);
    }

    private Recorded call(String method, String pass, String range) throws Exception {
        Recorded recorded = new Recorded();
        HttpServletRequest request = fakeRequest(pass, range);
        HttpServletResponse response = fakeResponse(recorded);
        if ("HEAD".equals(method)) {
            servlet.doHead(request, response);
        } else {
            servlet.doGet(request, response);
        }
        return recorded;
    }

    private static HttpServletRequest fakeRequest(String pass, String range) {
        Map<String, String> headers = new HashMap<>();
        if (range != null) {
            headers.put("Range", range);
        }
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getHeader" -> headers.get((String) args[0]);
            case "getParameter" -> "pass".equals(args[0]) ? pass : null;
            case "toString" -> "fake request";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> null;
        };
        return (HttpServletRequest) Proxy.newProxyInstance(FileDownloadServletTest.class.getClassLoader(),
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
            case "setContentLengthLong" -> {
                recorded.contentLength = (Long) args[0];
                yield null;
            }
            case "setHeader" -> {
                recorded.headers.put((String) args[0], (String) args[1]);
                yield null;
            }
            case "getOutputStream" -> recorded.stream();
            case "toString" -> "fake response";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> null;
        };
        return (HttpServletResponse) Proxy.newProxyInstance(FileDownloadServletTest.class.getClassLoader(),
                new Class<?>[] { HttpServletResponse.class }, handler);
    }

    /** What the servlet told the container. */
    private static final class Recorded {
        int status;
        String contentType;
        long contentLength = -1L;
        final Map<String, String> headers = new HashMap<>();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        ServletOutputStream stream() {
            return new ServletOutputStream() {
                @Override
                public void write(int value) {
                    out.write(value);
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
        }

        String body() {
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
