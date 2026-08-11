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

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A WAR is deployed under a context root, and until this test nothing had ever exercised the shell
 * servlet with one. Both cases here fail without the {@code <base href>}: the deep link because
 * every relative asset in the shell would resolve under the route's own directory, and the landing
 * page because the client reads the application's root from {@code document.baseURI} and would find
 * the site root.
 *
 * <p>The container is faked with a {@link Proxy}, which is enough because the servlet uses four
 * methods of the request and four of the response — and a great deal cheaper than a container.</p>
 */
class ShellServletContextRootTest {

    @Test
    void theShellCarriesTheContextRootAsItsBase() throws Exception {
        Recorded response = get("/coachapp", "/");

        assertEquals(200, response.status);
        assertEquals("text/html", response.contentType);
        assertTrue(response.body().contains("<base href=\"/coachapp/\">"), response.body());
    }

    @Test
    void aDeepLinkUnderTheContextRootGetsTheSameShell() throws Exception {
        // The case a coach's notification produces: an HTTP GET for a path with no file behind it,
        // three segments below the context root. It must answer with the shell, and the shell must
        // still be able to find js/classes.js.
        Recorded response = get("/coachapp", "/thread/xri:@openmdx:org.opencrx/42");

        assertEquals(200, response.status);
        assertTrue(response.body().contains("<base href=\"/coachapp/\">"), response.body());
        assertTrue(response.body().contains("js/classes.js"), "it really is the shell");
    }

    @Test
    void anApplicationAtTheSiteRootIsUnaffected() throws Exception {
        Recorded response = get("", "/messages");

        assertEquals(200, response.status);
        assertTrue(response.body().contains("<base href=\"/\">"), response.body());
    }

    @Test
    void aMissingAssetIsStillA404UnderAContextRoot() throws Exception {
        // The shell fallback must not swallow this: handing back HTML where a script was expected
        // turns a missing file into an unreadable syntax error.
        assertEquals(404, get("/coachapp", "/js/does-not-exist.js").status);
    }

    @Test
    void anExistingAssetIsServedUntouched() throws Exception {
        Recorded response = get("/coachapp", "/test-asset.js");

        assertEquals(200, response.status);
        assertEquals("application/javascript", response.contentType);
        assertTrue(!response.body().contains("<base"), "only the shell is rewritten");
    }

    // ------------------------------------------------------------------ the WAR's own web content

    @Test
    void contentInTheArchiveRootIsServedToo() throws Exception {
        // A WAR keeps index.html and the client bundle in src/main/webapp, which lands in the
        // ARCHIVE ROOT - and a WAR's classloader sees WEB-INF/classes and WEB-INF/lib, not the root.
        // Mapped at "/" this servlet replaces the container's default servlet, so nothing else is
        // left to serve them. Before this, such a WAR answered 404 to every request it received,
        // including its own shell, and nothing but a deployment would have said so.
        Recorded response = get("/coachapp", "/js/classes.js", webContent("/js/classes.js"));

        assertEquals(200, response.status);
        assertEquals("application/javascript", response.contentType);
        assertTrue(response.body().contains("the bundle"), response.body());
    }

    @Test
    void theClasspathWinsOverTheArchiveRoot() throws Exception {
        // So that a file dropped into the archive root cannot shadow an asset a jar ships - the
        // service worker above all, whose version stamp comes from the framework's own build.
        Recorded response = get("/coachapp", "/test-asset.js",
                webContent("/test-asset.js", "this must not be served"));

        assertTrue(response.body().contains("test-asset"), response.body());
        assertTrue(!response.body().contains("must not be served"), response.body());
    }

    @Test
    void webInfIsNeverServedFromTheArchiveRoot() throws Exception {
        // ServletContext.getResourceAsStream will hand over /WEB-INF/web.xml without complaint.
        Recorded response = get("/coachapp", "/WEB-INF/web.xml", webContent("/WEB-INF/web.xml"));

        assertEquals(404, response.status);
    }

    // ------------------------------------------------------------------ the fake container

    private Recorded get(String contextPath, String servletPath) throws IOException {
        return get(contextPath, servletPath, Map.of());
    }

    private Recorded get(String contextPath, String servletPath, Map<String, String> webContent)
            throws IOException {
        Recorded recorded = new Recorded();
        // doGet rather than service: the test lives in the servlet's own package, and going through
        // service would only add the conditional-GET machinery, which is the container's business.
        new Zeroz4jShellServlet().doGet(
                request(contextPath, servletPath, webContent), response(recorded));
        return recorded;
    }

    /** One file in the archive root, the way a WAR's src/main/webapp lands there. */
    private static Map<String, String> webContent(String path) {
        return Map.of(path, "// the bundle");
    }

    private static Map<String, String> webContent(String path, String body) {
        return Map.of(path, body);
    }

    /** What the servlet said, in the four ways it can say it. */
    private static final class Recorded {
        private int status = 200;
        private String contentType;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        String body() {
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }

    private static HttpServletRequest request(String contextPath, String servletPath,
                                              Map<String, String> webContent) {
        Map<String, Object> answers = new HashMap<>();
        answers.put("getMethod", "GET");
        answers.put("getContextPath", contextPath);
        answers.put("getServletPath", servletPath);
        answers.put("getPathInfo", null);
        answers.put("getScheme", "https");
        answers.put("getServletContext", servletContext(webContent));
        return (HttpServletRequest) Proxy.newProxyInstance(
                ShellServletContextRootTest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class },
                (proxy, method, args) -> answers.get(method.getName()));
    }

    /** A servlet context whose only job is to answer getResourceAsStream for the archive root. */
    private static ServletContext servletContext(Map<String, String> webContent) {
        return (ServletContext) Proxy.newProxyInstance(
                ShellServletContextRootTest.class.getClassLoader(),
                new Class<?>[] { ServletContext.class },
                (proxy, method, args) -> {
                    if ("getResourceAsStream".equals(method.getName())) {
                        String body = webContent.get((String) args[0]);
                        return body == null ? null
                                : new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                    }
                    return null;
                });
    }

    private static HttpServletResponse response(Recorded recorded) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "setContentType":
                    recorded.contentType = (String) args[0];
                    return null;
                case "sendError":
                    recorded.status = (Integer) args[0];
                    return null;
                case "setStatus":
                    recorded.status = (Integer) args[0];
                    return null;
                case "getOutputStream":
                    return stream(recorded.bytes);
                default:
                    return null;
            }
        };
        return (HttpServletResponse) Proxy.newProxyInstance(
                ShellServletContextRootTest.class.getClassLoader(),
                new Class<?>[] { HttpServletResponse.class }, handler);
    }

    private static ServletOutputStream stream(ByteArrayOutputStream sink) {
        return new ServletOutputStream() {
            @Override public boolean isReady() {
                return true;
            }

            @Override public void setWriteListener(WriteListener listener) {
                // Nothing here is asynchronous.
            }

            @Override public void write(int b) {
                sink.write(b);
            }
        };
    }
}
