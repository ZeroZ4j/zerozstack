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

import io.vidocq.cassini.chappe.ChappeHttpAdapter;
import io.vidocq.cassini.internal.RouteRegistry;
import io.vidocq.cassini.internal.UriRouter;
import io.vidocq.cassini.spi.http.CassiniStack;
import io.vidocq.chappe.api.Body;
import io.vidocq.chappe.api.Headers;
import io.vidocq.chappe.api.HttpMethod;
import io.vidocq.chappe.api.HttpVersion;
import io.vidocq.chappe.api.Request;
import io.vidocq.chappe.api.Response;
import io.vidocq.vauban.core.container.VaubanContainer;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Application;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serves the application's own JAX-RS resources, through Cassini (Vidocq's JAX-RS implementation).
 *
 * <p>The resources are found at runtime: every bean class carrying {@code @Path} or
 * {@code @Provider} is registered, and so is what a {@code jakarta.ws.rs.core.Application} bean
 * lists. Nothing is generated at build time. A request is handed to a resource only when a resource
 * method's path matches it (any HTTP method counts as a claim, so a wrong method gets 405 rather
 * than the shell page); everything else is left to the static content handler. The first
 * {@code @ApplicationPath} found is a prefix: resources answer under it and nowhere else.</p>
 *
 * <p>When the application has no {@code @Path} class, {@link #create} returns {@code null} and
 * nothing here runs.</p>
 */
final class RestResources {

    private static final Logger LOG = Logger.getLogger(RestResources.class.getName());
    private static final AnnotationLiteral<Any> ANY = new AnnotationLiteral<Any>() { };

    private final UriRouter claims;
    private final ChappeHttpAdapter adapter;
    private final String prefix;

    private RestResources(UriRouter claims, ChappeHttpAdapter adapter, String prefix) {
        this.claims = claims;
        this.adapter = adapter;
        this.prefix = prefix;
    }

    /**
     * Looks through the container's beans for JAX-RS resources.
     *
     * @param container the running bean container (must be the current Vauban container)
     * @return the handler, or {@code null} when the application has no resource class
     */
    static RestResources create(VaubanContainer container) {
        Set<Class<?>> pathClasses = new LinkedHashSet<>();
        List<Class<?>> applications = new ArrayList<>();
        for (Bean<?> bean : container.getBeanManager().getBeans(Object.class, ANY)) {
            Class<?> type = bean.getBeanClass();
            if (type == null) {
                continue;
            }
            if (type.isAnnotationPresent(Path.class)) {
                pathClasses.add(type);
            }
            if (Application.class.isAssignableFrom(type) && type != Application.class) {
                applications.add(type);
            }
        }

        Application application = null;
        applications.sort((a, b) -> a.getName().compareTo(b.getName()));
        if (!applications.isEmpty()) {
            if (applications.size() > 1) {
                LOG.warning("[zeroz4j] " + applications.size() + " jakarta.ws.rs.core.Application classes found; "
                        + "using " + applications.get(0).getName() + " for the path prefix.");
            }
            try {
                application = (Application) applications.get(0).getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException ex) {
                LOG.log(Level.WARNING, "[zeroz4j] Cannot create " + applications.get(0).getName(), ex);
            }
        }
        if (application != null && application.getClasses() != null) {
            for (Class<?> c : application.getClasses()) {
                if (c.isAnnotationPresent(Path.class)) {
                    pathClasses.add(c);
                }
            }
        }
        if (pathClasses.isEmpty()) {
            return null;
        }

        CassiniStack.Builder builder = CassiniStack.builder();
        if (application != null) {
            builder.application(application);
        }
        CassiniStack stack = builder.build();
        UriRouter claims = new UriRouter(RouteRegistry.discover(pathClasses.toArray(Class<?>[]::new)));
        LOG.info("[zeroz4j] JAX-RS resources: " + pathClasses.size() + " class(es) served by Cassini.");
        return new RestResources(claims, new ChappeHttpAdapter(stack.adapter()), prefixOf(application));
    }

    private static String prefixOf(Application application) {
        if (application == null) {
            return "";
        }
        ApplicationPath path = application.getClass().getAnnotation(ApplicationPath.class);
        if (path == null) {
            return "";
        }
        String value = path.value();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return "/".equals(value) ? "" : value;
    }

    /**
     * Whether a resource method's path matches this request, whatever the HTTP method.
     *
     * @param request the incoming request
     * @return true when {@link #handle} should answer it
     */
    boolean claims(Request request) {
        String path = relativePath(request.path());
        if (path == null) {
            return false;
        }
        String verb = request.method() == HttpMethod.HEAD ? "GET" : request.method().name();
        return !claims.matchAll(verb, path).isEmpty() || !claims.methodsAllowedFor(path).isEmpty();
    }

    /** Dispatches a request that {@link #claims} accepted. */
    Response handle(Request request) throws Exception {
        if (prefix.isEmpty()) {
            return adapter.handle(request);
        }
        return adapter.handle(new Remapped(request, relativePath(request.path()), prefix));
    }

    /** The path below the application prefix, or null when the request is outside it. */
    private String relativePath(String path) {
        String p = path == null ? "/" : path;
        if (prefix.isEmpty()) {
            return p;
        }
        if (!p.equals(prefix) && !p.startsWith(prefix + "/")) {
            return null;
        }
        String rest = p.substring(prefix.length());
        return rest.isEmpty() ? "/" : rest;
    }

    /** The same request with the application prefix moved from the path to the context path. */
    private record Remapped(Request base, String path, String contextPath) implements Request {
        @Override public HttpMethod method() { return base.method(); }
        @Override public URI uri() { return base.uri(); }
        @Override public String query() { return base.query(); }
        @Override public HttpVersion version() { return base.version(); }
        @Override public Headers headers() { return base.headers(); }
        @Override public Body body() { return base.body(); }
        @Override public Map<String, String> pathParams() { return base.pathParams(); }
        @Override public Map<String, String> queryParams() { return base.queryParams(); }
        @Override public String pathInfo() { return path; }
        @Override public Object attribute(String key) { return base.attribute(key); }
        @Override public Request attribute(String key, Object value) { base.attribute(key, value); return this; }
        @Override public InetSocketAddress remoteAddress() { return base.remoteAddress(); }
        @Override public InetSocketAddress localAddress() { return base.localAddress(); }
        @Override public boolean isSecure() { return base.isSecure(); }
    }
}
