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

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Development-mode login servlet providing a simple form-based authentication bypass.
 * Active only when the system property {@code zeroz.security.mode} is set to {@code "dev"}.
 *
 * <p>Provides hardcoded demo credentials: {@code demo/demo} (role: {@code user}) and {@code admin/admin} (roles: {@code user, admin}).</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Security Scope:</b> Restricted to development mode via {@link #isDevMode()}. Returns 404 NOT_FOUND if invoked in OIDC mode.</li>
 *   <li><b>Session Mutation:</b> Mutates HTTP session attributes {@link #DEV_PRINCIPAL_KEY} and {@link #DEV_ROLES_KEY}.</li>
 * </ul>
 */
@WebServlet("/dev-login")
public class DevLoginServlet extends HttpServlet {

    /** Key for storing the dev principal in the HTTP session ("zeroz.dev.principal"). */
    public static final String DEV_PRINCIPAL_KEY = "zeroz.dev.principal";
    /** Key for storing dev roles in the HTTP session ("zeroz.dev.roles"). */
    public static final String DEV_ROLES_KEY = "zeroz.dev.roles";

    private static final Map<String, DevUser> DEV_USERS = new LinkedHashMap<>();

    static {
        DEV_USERS.put("demo", new DevUser("demo", "demo", new HashSet<>(Arrays.asList("user"))));
        DEV_USERS.put("admin", new DevUser("admin", "admin", new HashSet<>(Arrays.asList("user", "admin"))));
    }

    /**
     * Serves the HTML login page for development authentication.
     *
     * @param req  http request
     * @param resp http response
     * @throws ServletException if servlet error occurs
     * @throws IOException      if I/O error occurs
     *
     * <p><b>Under the hood:</b> Checks {@link #isDevMode()}. Renders embedded HTML form template.</p>
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!isDevMode()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String error = req.getParameter("error");
        resp.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.write(LOGIN_PAGE.replace("{{ERROR}}",
                error != null ? "<p class='error'>Invalid username or password</p>" : ""));
        }
    }

    /**
     * Processes submission of dev login credentials.
     *
     * @param req  http request
     * @param resp http response
     * @throws ServletException if servlet error occurs
     * @throws IOException      if I/O error occurs
     *
     * <p><b>Under the hood:</b> Validates username and password against {@code DEV_USERS}. On success, populates session attributes
     * and redirects to target URI. On failure, redirects to {@code /dev-login?error=1}.</p>
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!isDevMode()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String username = req.getParameter("username");
        String password = req.getParameter("password");

        DevUser user = DEV_USERS.get(username);
        if (user != null && user.password.equals(password)) {
            HttpSession session = req.getSession(true);
            session.setAttribute(DEV_PRINCIPAL_KEY, (Principal) () -> user.username);
            session.setAttribute(DEV_ROLES_KEY, user.roles);

            String redirect = req.getParameter("redirect");
            resp.sendRedirect(redirect != null && !redirect.isEmpty() ? redirect : req.getContextPath() + "/");
        } else {
            resp.sendRedirect(req.getContextPath() + "/dev-login?error=1");
        }
    }

    /**
     * Evaluates whether the application is running in development security mode.
     *
     * @return true if system property {@code zeroz.security.mode} equals {@code "dev"} (case-insensitive)
     */
    static boolean isDevMode() {
        return "dev".equalsIgnoreCase(System.getProperty("zeroz.security.mode", "oidc"));
    }

    private static class DevUser {
        final String username;
        final String password;
        final Set<String> roles;

        DevUser(String username, String password, Set<String> roles) {
            this.username = username;
            this.password = password;
            this.roles = roles;
        }
    }

    private static final String LOGIN_PAGE = 
        "<!DOCTYPE html>\n" +
        "<html lang=\"en\">\n" +
        "<head>\n" +
        "    <meta charset=\"UTF-8\">\n" +
        "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
        "    <title>zeroz4j Dev Login</title>\n" +
        "    <style>\n" +
        "        * { box-sizing: border-box; margin: 0; padding: 0; }\n" +
        "        body {\n" +
        "            font-family: 'Segoe UI', system-ui, sans-serif;\n" +
        "            background: linear-gradient(135deg, #0f0c29, #302b63, #24243e);\n" +
        "            min-height: 100vh;\n" +
        "            display: flex;\n" +
        "            align-items: center;\n" +
        "            justify-content: center;\n" +
        "        }\n" +
        "        .login-card {\n" +
        "            background: rgba(255,255,255,0.05);\n" +
        "            backdrop-filter: blur(20px);\n" +
        "            border: 1px solid rgba(255,255,255,0.1);\n" +
        "            border-radius: 16px;\n" +
        "            padding: 40px;\n" +
        "            width: 380px;\n" +
        "            box-shadow: 0 20px 60px rgba(0,0,0,0.5);\n" +
        "        }\n" +
        "        h1 {\n" +
        "            color: #fff;\n" +
        "            font-size: 24px;\n" +
        "            text-align: center;\n" +
        "            margin-bottom: 8px;\n" +
        "        }\n" +
        "        .subtitle {\n" +
        "            color: rgba(255,255,255,0.5);\n" +
        "            text-align: center;\n" +
        "            font-size: 13px;\n" +
        "            margin-bottom: 30px;\n" +
        "        }\n" +
        "        .dev-badge {\n" +
        "            background: #ff6b35;\n" +
        "            color: #fff;\n" +
        "            padding: 3px 10px;\n" +
        "            border-radius: 12px;\n" +
        "            font-size: 11px;\n" +
        "            font-weight: 600;\n" +
        "            letter-spacing: 0.5px;\n" +
        "        }\n" +
        "        label {\n" +
        "            display: block;\n" +
        "            color: rgba(255,255,255,0.7);\n" +
        "            font-size: 13px;\n" +
        "            margin-bottom: 6px;\n" +
        "        }\n" +
        "        input[type='text'], input[type='password'] {\n" +
        "            width: 100%;\n" +
        "            padding: 12px 16px;\n" +
        "            background: rgba(255,255,255,0.08);\n" +
        "            border: 1px solid rgba(255,255,255,0.15);\n" +
        "            border-radius: 8px;\n" +
        "            color: #fff;\n" +
        "            font-size: 15px;\n" +
        "            margin-bottom: 18px;\n" +
        "            outline: none;\n" +
        "            transition: border-color 0.2s;\n" +
        "        }\n" +
        "        input:focus {\n" +
        "            border-color: #7c6cf0;\n" +
        "        }\n" +
        "        button {\n" +
        "            width: 100%;\n" +
        "            padding: 14px;\n" +
        "            background: linear-gradient(135deg, #7c6cf0, #5a4fcf);\n" +
        "            border: none;\n" +
        "            border-radius: 8px;\n" +
        "            color: #fff;\n" +
        "            font-size: 16px;\n" +
        "            font-weight: 600;\n" +
        "            cursor: pointer;\n" +
        "            transition: transform 0.15s, box-shadow 0.15s;\n" +
        "        }\n" +
        "        button:hover {\n" +
        "            transform: translateY(-1px);\n" +
        "            box-shadow: 0 8px 24px rgba(124,108,240,0.4);\n" +
        "        }\n" +
        "        .error {\n" +
        "            background: rgba(255,59,48,0.15);\n" +
        "            border: 1px solid rgba(255,59,48,0.3);\n" +
        "            color: #ff6b6b;\n" +
        "            padding: 10px 14px;\n" +
        "            border-radius: 8px;\n" +
        "            font-size: 13px;\n" +
        "            margin-bottom: 18px;\n" +
        "            text-align: center;\n" +
        "        }\n" +
        "        .hints {\n" +
        "            margin-top: 24px;\n" +
        "            padding-top: 20px;\n" +
        "            border-top: 1px solid rgba(255,255,255,0.08);\n" +
        "            color: rgba(255,255,255,0.4);\n" +
        "            font-size: 12px;\n" +
        "        }\n" +
        "        .hints code {\n" +
        "            background: rgba(255,255,255,0.1);\n" +
        "            padding: 2px 6px;\n" +
        "            border-radius: 4px;\n" +
        "            color: rgba(255,255,255,0.6);\n" +
        "        }\n" +
        "    </style>\n" +
        "</head>\n" +
        "<body>\n" +
        "    <div class=\"login-card\">\n" +
        "        <h1>zeroz4j <span class=\"dev-badge\">DEV</span></h1>\n" +
        "        <p class=\"subtitle\">Development Mode Authentication</p>\n" +
        "        {{ERROR}}\n" +
        "        <form method=\"POST\">\n" +
        "            <label for=\"username\">Username</label>\n" +
        "            <input type=\"text\" id=\"username\" name=\"username\" placeholder=\"demo or admin\" autofocus>\n" +
        "            <label for=\"password\">Password</label>\n" +
        "            <input type=\"password\" id=\"password\" name=\"password\" placeholder=\"same as username\">\n" +
        "            <button type=\"submit\">Sign In</button>\n" +
        "        </form>\n" +
        "        <div class=\"hints\">\n" +
        "            <p><code>demo / demo</code> - role: user</p>\n" +
        "            <p><code>admin / admin</code> - roles: user, admin</p>\n" +
        "        </div>\n" +
        "    </div>\n" +
        "</body>\n" +
        "</html>\n";
}
