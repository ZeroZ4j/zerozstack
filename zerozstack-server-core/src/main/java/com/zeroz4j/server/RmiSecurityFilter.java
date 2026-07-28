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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.security.Principal;
import java.net.URLEncoder;

/**
 * Servlet filter enforcing authentication before serving application views or static assets.
 *
 * <p>In dev mode ({@code zeroz.security.mode=dev}), redirects unauthenticated HTTP GET requests to {@code /dev-login}.
 * Excludes static assets and the {@code /wasm-rmi} WebSocket endpoint from filtering.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Path Exclusions:</b> Bypasses requests starting with {@code /dev-login}, {@code /wasm-rmi}, or ending in common static file extensions (.js, .css, .wasm, etc.).</li>
 *   <li><b>Dev Mode Handling:</b> Inspects HTTP session for {@code DevLoginServlet.DEV_PRINCIPAL_KEY} or container principal via {@link #getEffectivePrincipal(HttpServletRequest)}.</li>
 * </ul>
 */
@WebFilter("/*")
public class RmiSecurityFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No init required
    }

    /**
     * Filters incoming HTTP requests, enforcing authentication redirects or 401 statuses for protected resources.
     *
     * @param request  servlet request
     * @param response servlet response
     * @param chain    filter chain
     * @throws IOException      if I/O error occurs
     * @throws ServletException if servlet error occurs
     *
     * <p><b>Under the hood:</b> Checks request URI path exclusions. Checks effective principal via {@link #getEffectivePrincipal(HttpServletRequest)}.
     * If unauthenticated, redirects to `/dev-login?redirect=...` in dev mode or sends 401 status in production OIDC mode.</p>
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;
        String path = httpReq.getRequestURI().substring(httpReq.getContextPath().length());

        // Skip filtering for these paths
        if (path.startsWith("/dev-login")
                || path.startsWith("/wasm-rmi")
                || path.endsWith(".css")
                || path.endsWith(".js")
                || path.endsWith(".wasm")
                || path.endsWith(".ico")
                || path.endsWith(".png")
                || path.endsWith(".jpg")
                || path.endsWith(".svg")) {
            chain.doFilter(request, response);
            return;
        }

        // Check if already authenticated
        Principal principal = getEffectivePrincipal(httpReq);
        if (principal != null) {
            chain.doFilter(request, response);
            return;
        }

        // Not authenticated — redirect
        if (DevLoginServlet.isDevMode()) {
            httpResp.sendRedirect(httpReq.getContextPath() + "/dev-login?redirect="
                + URLEncoder.encode(httpReq.getRequestURI(), "UTF-8"));
        } else {
            // In OIDC mode, the Jakarta Security mechanism handles the redirect
            // automatically via the container. We just send a 401 to trigger it.
            httpResp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    /**
     * Resolves the effective principal for an HTTP request from container security or dev session attributes.
     *
     * @param req HttpServletRequest
     * @return {@link Principal} object, or {@code null} if unauthenticated
     *
     * <p><b>Under the hood:</b> Checks {@code req.getUserPrincipal()}. If null, checks {@code session.getAttribute(DevLoginServlet.DEV_PRINCIPAL_KEY)}.</p>
     */
    static Principal getEffectivePrincipal(HttpServletRequest req) {
        // Container-managed principal (OIDC / WildFly Elytron)
        if (req.getUserPrincipal() != null) {
            return req.getUserPrincipal();
        }
        // Dev mode principal from session
        HttpSession session = req.getSession(false);
        if (session != null) {
            Object devPrincipal = session.getAttribute(DevLoginServlet.DEV_PRINCIPAL_KEY);
            if (devPrincipal instanceof Principal) {
                return (Principal) devPrincipal;
            }
        }
        return null;
    }

    @Override
    public void destroy() {
        // No cleanup required
    }
}
