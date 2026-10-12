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

import com.zeroz4j.server.spi.ServerBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.logging.Logger;

/**
 * Entry point for starting a standalone ZeroZ Stack server, whichever container it runs on.
 *
 * <pre>{@code
 * Zeroz4jServer.start(8080, "My application").join();
 * }</pre>
 *
 * <p>The container is chosen by which binding module is on the classpath, never by this call:
 * {@code zerozstack-server-helidon} (the default) or {@code zerozstack-server-vidocq} (experimental,
 * Java 25). Each registers a {@link ServerBinding}, found here with {@link ServiceLoader}.</p>
 *
 * <ul>
 *   <li><b>One binding:</b> it is used.</li>
 *   <li><b>None:</b> {@link #start(int, String)} throws, naming the two artifacts.</li>
 *   <li><b>More than one:</b> it throws, naming them, unless the system property
 *       {@value #BINDING_PROPERTY} names the one to use.</li>
 * </ul>
 *
 * <p>A WAR does not call this class: the servlet container starts the application, through
 * {@code zerozstack-server-jakarta}.</p>
 */
public final class Zeroz4jServer implements AutoCloseable {

    /** System property naming the binding to use when more than one is on the classpath. */
    public static final String BINDING_PROPERTY = "zeroz.server.binding";

    private static final Logger LOG = Logger.getLogger(Zeroz4jServer.class.getName());

    private final ServerBinding.RunningServer server;

    private Zeroz4jServer(ServerBinding.RunningServer server) {
        this.server = server;
    }

    /**
     * Starts the application on the given HTTP port, on the binding found on the classpath.
     *
     * @param port    the TCP port to listen on
     * @param appName the display name of the application, for logging
     * @return the started server
     * @throws IllegalStateException when no binding is on the classpath, when several are and
     *                               {@value #BINDING_PROPERTY} does not pick one, or when it names
     *                               one that is not there
     */
    public static Zeroz4jServer start(int port, String appName) {
        return start(port, appName, discover(), System.getProperty(BINDING_PROPERTY));
    }

    /** The same, with the bindings and the property handed in; what the tests call. */
    static Zeroz4jServer start(int port, String appName, List<ServerBinding> bindings, String wanted) {
        ServerBinding binding = choose(bindings, wanted);
        LOG.info("[zeroz4j] Starting on the " + binding.name() + " server binding");
        return new Zeroz4jServer(binding.start(port, appName));
    }

    /** Picks the binding to use, or throws a sentence saying what to change. */
    static ServerBinding choose(List<ServerBinding> bindings, String wanted) {
        String name = wanted == null ? "" : wanted.trim();
        if (!name.isEmpty()) {
            for (ServerBinding binding : bindings) {
                if (name.equalsIgnoreCase(binding.name())) {
                    return binding;
                }
            }
            throw new IllegalStateException("-D" + BINDING_PROPERTY + "=" + name
                    + " names a server binding that is not on the classpath. Found: "
                    + (bindings.isEmpty() ? "none" : names(bindings)) + ".");
        }
        if (bindings.isEmpty()) {
            throw new IllegalStateException("No server binding is on the classpath. Add"
                    + " com.zeroz4j:zerozstack-server-helidon, or com.zeroz4j:zerozstack-server-vidocq"
                    + " (experimental, Java 25). A WAR does not call Zeroz4jServer.start: its servlet"
                    + " container starts it, through zerozstack-server-jakarta.");
        }
        if (bindings.size() > 1) {
            throw new IllegalStateException("More than one server binding is on the classpath: "
                    + names(bindings) + ". Remove all but one, or pick one with -D" + BINDING_PROPERTY
                    + "=<name>.");
        }
        return bindings.get(0);
    }

    private static String names(List<ServerBinding> bindings) {
        List<String> names = new ArrayList<>();
        for (ServerBinding binding : bindings) {
            names.add(binding.name());
        }
        return String.join(", ", names);
    }

    private static List<ServerBinding> discover() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = Zeroz4jServer.class.getClassLoader();
        }
        List<ServerBinding> found = new ArrayList<>();
        for (ServerBinding binding : ServiceLoader.load(ServerBinding.class, loader)) {
            found.add(binding);
        }
        return found;
    }

    /**
     * The TCP port the server is listening on.
     *
     * @return the bound port number
     */
    public int port() {
        return server.port();
    }

    /**
     * Keeps the calling thread alive until the server is shut down or the thread is interrupted.
     *
     * <p><b>Under the hood:</b> invokes {@code Thread.currentThread().join()}.</p>
     */
    public void join() {
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Stops the server the binding started. */
    @Override
    public void close() {
        server.close();
    }
}
