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
package com.zeroz4j.server.vidocq;

import com.zeroz4j.api.BinaryRegistry;
import com.zeroz4j.server.UploadLimits;
import com.zeroz4j.server.WasmRmiServerEngine;
import io.vidocq.chappe.api.Handler;
import io.vidocq.chappe.api.Response;
import io.vidocq.chappe.api.Server;
import io.vidocq.chappe.api.StatusCode;
import io.vidocq.vauban.core.container.VaubanContainer;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs a ZeroZ Stack application on the Vidocq runtime instead of Helidon.
 *
 * <p>Application code does not call this class: it calls
 * {@code com.zeroz4j.server.Zeroz4jServer.start}, which finds {@link VidocqServerBinding} when this
 * module is on the classpath, and that binding starts this. Calling it directly still works.
 * <b>Experimental, and Java 25 only</b> (Vidocq 0.3.0 needs it). See
 * {@code docs/guides/vidocq-server.md}.</p>
 *
 * <p>Vidocq is a set of libraries. This binding uses two: <i>Vauban</i>, its CDI container, finds
 * the application's beans and the framework's engine; <i>Chappe</i>, its HTTP and WebSocket server,
 * takes the requests. The adapter between Chappe's WebSocket and the Jakarta WebSocket
 * {@code Session} the engine is written against lives in this module, so
 * {@code zerozstack-server-core} runs here unchanged.</p>
 *
 * <p>Two addresses are the framework's, as under Helidon: {@code /wasm-rmi} (the live connection)
 * and {@code /zeroz4j-upload} (file upload). The application's own JAX-RS resources come next (see
 * {@link RestResources}), then everything else as static content with the shell as fallback.</p>
 */
public final class Zeroz4jVidocqServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(Zeroz4jVidocqServer.class.getName());

    /** Chappe's default request ceiling, 10 MB; the upload limit is added on top when it is larger. */
    private static final long DEFAULT_MAX_REQUEST_BYTES = 10L * 1024L * 1024L;

    /** Room for request line, headers and framing around an upload at its limit. */
    private static final long REQUEST_SLACK_BYTES = 64L * 1024L;

    private final Server server;
    private final VaubanContainer container;
    private final IdleReaper reaper;

    private Zeroz4jVidocqServer(Server server, VaubanContainer container, IdleReaper reaper) {
        this.server = server;
        this.container = container;
        this.reaper = reaper;
    }

    /**
     * Starts the application on the given HTTP port.
     *
     * <p><b>Under the hood:</b> calls {@code BinaryRegistry.init()}, builds a Vauban container from
     * the classpath (both plain classes and bean archives with a {@code beans.xml}), takes the
     * engine from it and starts a Chappe server whose handler routes the three addresses.</p>
     *
     * @param port    the TCP port to listen on; 0 picks a free one, readable from {@link #port()}
     * @param appName the display name of the application, for logging
     * @return the started server
     */
    public static Zeroz4jVidocqServer start(int port, String appName) {
        BinaryRegistry.init();

        VaubanContainer container = VaubanContainer.builder()
                .scanClasspath()
                .scanBeanArchivesFromClasspath()
                .build();
        IdleReaper reaper = new IdleReaper();
        Server server;
        try {
            WasmRmiServerEngine engine = container.select(WasmRmiServerEngine.class);
            WebSocketBridge bridge = new WebSocketBridge(engine, reaper);
            StaticContentHandler statics = new StaticContentHandler();
            UploadHandler uploads = new UploadHandler();
            RestResources rest = RestResources.create(container);

            Handler handler = request -> {
                try {
                    String path = request.path();
                    if (WebSocketBridge.PATH.equals(path)) {
                        if (request.header("Upgrade").isEmpty()) {
                            return Response.of(StatusCode.of(426, "Upgrade Required"));
                        }
                        return bridge.upgrade(request);
                    }
                    if (UploadHandler.PATH.equals(path)) {
                        return uploads.receive(request);
                    }
                    if (rest != null && rest.claims(request)) {
                        return rest.handle(request);
                    }
                    return statics.serve(request);
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "[zeroz4j] A request failed: " + request.path(), ex);
                    return Response.of(StatusCode.INTERNAL_SERVER_ERROR);
                }
            };

            long maxRequest = Math.max(DEFAULT_MAX_REQUEST_BYTES, UploadLimits.maxBytes() + REQUEST_SLACK_BYTES);
            server = Server.builder().port(port).maxRequestSize(maxRequest).handler(handler).build();
            server.start();
        } catch (RuntimeException | Error failure) {
            reaper.close();
            container.close();
            throw failure;
        }
        LOG.info(appName + " listening on http://localhost:" + server.port() + "/ (RMI at /wasm-rmi, Vidocq runtime)");
        return new Zeroz4jVidocqServer(server, container, reaper);
    }

    /** The bean container, for tests that look at the engine's state. */
    VaubanContainer container() {
        return container;
    }

    /**
     * The TCP port the server is listening on.
     *
     * @return the bound port number
     */
    public int port() {
        return server.port();
    }

    /** Keeps the calling thread alive until the server is shut down or the thread is interrupted. */
    public void join() {
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Stops the HTTP server, closes the connections' watcher, and shuts the bean container down. */
    @Override
    public void close() {
        try {
            server.stop();
        } finally {
            reaper.close();
            container.close();
        }
    }
}
