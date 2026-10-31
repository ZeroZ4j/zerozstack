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
package com.zeroz4j.server.dev;

import com.zeroz4j.api.BinaryRegistry;
import com.zeroz4j.server.ClientErrorRpcImpl;
import com.zeroz4j.server.FileUploadRpcImpl;
import com.zeroz4j.server.LiveMutexManager;
import com.zeroz4j.server.LiveMutexRpcImpl;
import com.zeroz4j.server.ObjectMapperProducer;
import com.zeroz4j.server.ServerRuntime;
import com.zeroz4j.server.SlowCallRpcImpl;
import com.zeroz4j.server.SlowCalls;
import com.zeroz4j.server.SyncEngine;
import com.zeroz4j.server.WasmRmiServerEngine;
import com.zeroz4j.server.jakarta.Zeroz4jServletBootstrap;
import com.zeroz4j.server.jakarta.Zeroz4jShellServlet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.servlet.Servlet;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.server.WsContextListener;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs an application written for a servlet container on embedded Tomcat, for development.
 *
 * <p>A WAR is found and wired by its container, which scans every jar on the classpath. With a few
 * hundred jars that is the difference between a start of four seconds and one of eighteen. This
 * server scans nothing. The beans, the servlets and the framework's own pieces are named, and the
 * result is the same wiring the container would have produced: CDI with injection, the framework's
 * start-up listener, the WebSocket endpoint at {@code <context>/wasm-rmi}, and the shell servlet at
 * {@code /}.</p>
 *
 * <pre>{@code
 * try (Zeroz4jDevServer server = Zeroz4jDevServer.builder()
 *         .port(8080)
 *         .contextPath("/app")
 *         .beans(OrderServiceImpl.class, Orders.class)
 *         .servlet(SignInServlet.class)
 *         .start()) {
 *     server.await();
 * }
 * }</pre>
 *
 * <p><b>Development only.</b> It adds Tomcat and Weld, which belong to no deployed WAR. Take the
 * module with test or provided scope. Settings are the usual {@code zeroz.*} system properties.</p>
 *
 * <p>Each servlet is mapped at the patterns its own {@link WebServlet} declares, so the dev server
 * cannot drift from the WAR. A servlet is created through CDI, so {@code @Inject} works in it.</p>
 *
 * @since 0.11.0
 */
public final class Zeroz4jDevServer implements AutoCloseable {

    private static final AtomicInteger SERIAL = new AtomicInteger();

    private final Tomcat tomcat;
    private final WeldContainer cdi;
    private final String contextPath;
    private final int port;

    private Zeroz4jDevServer(Tomcat tomcat, WeldContainer cdi, String contextPath, int port) {
        this.tomcat = tomcat;
        this.cdi = cdi;
        this.contextPath = contextPath;
        this.port = port;
    }

    /**
     * Starts describing a server.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The port the server listens on. The real one when the builder was given 0.
     *
     * @return the port
     */
    public int port() {
        return port;
    }

    /**
     * The address of the application's root.
     *
     * @return for example {@code http://localhost:8080/app/}
     */
    public String baseUrl() {
        return "http://localhost:" + port + contextPath + "/";
    }

    /**
     * The address of the WebSocket.
     *
     * @return for example {@code ws://localhost:8080/app/wasm-rmi}
     */
    public String webSocketUrl() {
        return "ws://localhost:" + port + contextPath + "/wasm-rmi";
    }

    /** Blocks the calling thread until the server stops. For a {@code main} method. */
    public void await() {
        tomcat.getServer().await();
    }

    /** Stops Tomcat and the CDI container. */
    @Override
    public void close() {
        try {
            tomcat.stop();
            tomcat.destroy();
        } catch (LifecycleException failure) {
            // Stopping a development server: nothing to do about a failure here.
        }
        try {
            cdi.shutdown();
        } catch (RuntimeException failure) {
            // Same.
        }
    }

    /** Describes a server before it starts. */
    public static final class Builder {

        private int port = 8080;
        private String contextPath = "";
        private boolean shell = true;
        private final List<Class<?>> beans = new ArrayList<>();
        private final List<Class<? extends Servlet>> servlets = new ArrayList<>();

        private Builder() {
        }

        /**
         * The port to listen on. Default 8080. 0 picks a free one; read it with {@link #port()}.
         *
         * @param value the port
         * @return this builder
         */
        public Builder port(int value) {
            this.port = value;
            return this;
        }

        /**
         * Where the application sits, as the WAR is deployed. Default the root. Use the same as
         * production, because the session cookie path, the service worker scope and the shell's
         * base address all follow it.
         *
         * @param value for example {@code /app}; {@code /} or empty means the root
         * @return this builder
         */
        public Builder contextPath(String value) {
            String path = value == null ? "" : value.trim();
            if (path.equals("/")) {
                path = "";
            }
            if (!path.isEmpty() && !path.startsWith("/")) {
                path = "/" + path;
            }
            this.contextPath = path;
            return this;
        }

        /**
         * The application's own beans: its {@code @RmiService} implementations and everything they
         * inject. Nothing is discovered, so every class that is injected has to be named.
         *
         * @param types the classes
         * @return this builder
         */
        public Builder beans(Class<?>... types) {
            for (Class<?> type : types) {
                beans.add(type);
            }
            return this;
        }

        /**
         * A servlet of the application, mapped where its {@code @WebServlet} says. Created through
         * CDI, so it is also a bean and need not be named in {@link #beans}.
         *
         * @param type the servlet class
         * @return this builder
         */
        public Builder servlet(Class<? extends Servlet> type) {
            servlets.add(type);
            return this;
        }

        /**
         * Whether the framework's shell servlet answers at {@code /}, which is what makes a reload
         * of a deep link return the application. Default true. Turn it off when the application maps
         * its own.
         *
         * @param value false to leave {@code /} to the application
         * @return this builder
         */
        public Builder shell(boolean value) {
            this.shell = value;
            return this;
        }

        /**
         * Starts the server.
         *
         * @return the running server
         * @throws IllegalStateException when Tomcat or CDI could not start
         */
        public Zeroz4jDevServer start() {
            BinaryRegistry.init();
            WeldContainer cdi = startCdi();
            Tomcat tomcat = null;
            try {
                forceEngineStartup();
                tomcat = startTomcat(cdi);
                int actual = tomcat.getConnector().getLocalPort();
                return new Zeroz4jDevServer(tomcat, cdi, contextPath, actual);
            } catch (RuntimeException | LifecycleException failure) {
                if (tomcat != null) {
                    try {
                        tomcat.stop();
                        tomcat.destroy();
                    } catch (LifecycleException ignored) {
                        // The start failure below is the one worth reporting.
                    }
                }
                cdi.shutdown();
                throw new IllegalStateException("The development server did not start: "
                        + failure.getMessage(), failure);
            }
        }

        private WeldContainer startCdi() {
            List<Class<?>> all = new ArrayList<>();
            all.add(ServerRuntime.class);
            all.add(WasmRmiServerEngine.class);
            all.add(SyncEngine.class);
            all.add(ObjectMapperProducer.class);
            all.add(LiveMutexManager.class);
            all.add(LiveMutexRpcImpl.class);
            all.add(FileUploadRpcImpl.class);
            all.add(SlowCalls.class);
            all.add(SlowCallRpcImpl.class);
            all.add(ClientErrorRpcImpl.class);
            all.addAll(beans);
            all.addAll(servlets);
            return new Weld("zeroz4j-dev-" + SERIAL.incrementAndGet())
                    .disableDiscovery()
                    .addBeanClasses(all.toArray(new Class<?>[0]))
                    .initialize();
        }

        /**
         * Builds the RMI engine now rather than on the first frame, so a service that is missing
         * shows up as a start-up line and not as a browser problem. {@code select().get()} would
         * give a client proxy and instantiate nothing, so the contextual instance is asked for.
         */
        private static void forceEngineStartup() {
            BeanManager manager = CDI.current().getBeanManager();
            Bean<?> bean = manager.resolve(manager.getBeans(WasmRmiServerEngine.class));
            if (bean == null) {
                throw new IllegalStateException("The framework's RMI engine is not a bean here");
            }
            CreationalContext<?> creational = manager.createCreationalContext(bean);
            Object instance = manager.getContext(ApplicationScoped.class).get(bean, cast(creational));
            if (instance == null) {
                throw new IllegalStateException("The framework's RMI engine could not be created");
            }
        }

        @SuppressWarnings("unchecked")
        private static <T> CreationalContext<T> cast(CreationalContext<?> creational) {
            return (CreationalContext<T>) creational;
        }

        private Tomcat startTomcat(WeldContainer cdi) throws LifecycleException {
            Path work;
            try {
                work = Files.createTempDirectory("zeroz4j-dev-tomcat");
                Files.createDirectories(work.resolve("webapp"));
            } catch (IOException failure) {
                throw new IllegalStateException("Could not create a work directory", failure);
            }
            Tomcat tomcat = new Tomcat();
            tomcat.setBaseDir(work.toString());
            tomcat.setPort(port);
            tomcat.getConnector();

            // addContext, not addWebapp: no annotation scanning, no container initializer sweep.
            Context context = tomcat.addContext(contextPath,
                    work.resolve("webapp").toAbsolutePath().toString());
            context.setInstanceManager(new DevInstanceManager());
            // The framework's @WebListener, which a scanning container finds. Without it the wire
            // types are never registered and the first call fails with an "Unsupported type" error.
            context.addApplicationListener(Zeroz4jServletBootstrap.class.getName());
            // Tomcat's listener creates the WebSocket container, then ours publishes the endpoint.
            context.addApplicationListener(WsContextListener.class.getName());
            context.addApplicationListener(DevEndpoints.class.getName());

            if (shell) {
                addServlet(context, "zeroz-shell", new Zeroz4jShellServlet(), "/");
            }
            for (Class<? extends Servlet> type : servlets) {
                WebServlet annotation = type.getAnnotation(WebServlet.class);
                if (annotation == null || annotation.urlPatterns().length == 0
                        && annotation.value().length == 0) {
                    throw new IllegalStateException(type.getName() + " has no @WebServlet url "
                            + "pattern, so the dev server cannot know where the WAR serves it");
                }
                String[] patterns = annotation.urlPatterns().length > 0
                        ? annotation.urlPatterns() : annotation.value();
                String name = annotation.name().isEmpty() ? type.getSimpleName() : annotation.name();
                addServlet(context, name, cdi.select(type).get(), patterns);
            }
            tomcat.start();
            return tomcat;
        }

        private static void addServlet(Context context, String name, Servlet servlet,
                                       String... patterns) {
            Wrapper wrapper = Tomcat.addServlet(context, name, servlet);
            wrapper.setAsyncSupported(true);
            // A container reads @MultipartConfig by scanning. Read it off the class and its
            // superclasses, because CDI may hand back a generated subclass and the annotation is
            // not inherited.
            for (Class<?> type = servlet.getClass(); type != null; type = type.getSuperclass()) {
                MultipartConfig multipart = type.getAnnotation(MultipartConfig.class);
                if (multipart != null) {
                    wrapper.setMultipartConfigElement(
                            new jakarta.servlet.MultipartConfigElement(multipart));
                    break;
                }
            }
            for (String pattern : patterns) {
                context.addServletMappingDecoded(pattern, name);
            }
        }
    }
}
