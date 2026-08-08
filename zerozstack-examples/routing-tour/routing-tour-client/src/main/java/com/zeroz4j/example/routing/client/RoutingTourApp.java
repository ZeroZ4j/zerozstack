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
package com.zeroz4j.example.routing.client;

import com.zeroz4j.api.RmiSecurityContext;
import com.zeroz4j.client.Zeroz4jClient;
import com.zeroz4j.client.router.Router;
import org.teavm.jso.JSBody;

/**
 * Starts the routing tour.
 *
 * <p>The whole navigation story is the four {@code Router} calls below — the route table itself is
 * generated from the {@code @Route} annotations at compile time, so nothing here enumerates views.</p>
 */
public class RoutingTourApp {

    public static void main(String[] args) {
        Zeroz4jClient.connect(webSocketUrl(), () -> {
            // Only a real sign-in reaches this. A connection the server declined fires
            // onAuthenticationFailed instead, so the tour never starts half-authenticated.
            RmiSecurityContext.onAuthenticated(() -> {
                Router.notFoundRoute("/not-found");
                Router.forbiddenRoute("/forbidden");
                Router.onError((path, reason) ->
                        warn("[tour] Could not open " + path + ": " + reason.getMessage()));
                Router.start("app-root");
            });

            RmiSecurityContext.onAuthenticationFailed(() ->
                    showFatal("Sign-in was refused. Add ?user=demo&password=demo to the URL."));
        });
    }

    @JSBody(script =
        "var l = window.location;"
        + "var user = new URLSearchParams(l.search).get('user') || 'demo';"
        + "var password = new URLSearchParams(l.search).get('password') || 'demo';"
        + "return (l.protocol === 'https:' ? 'wss://' : 'ws://') + l.host + '/wasm-rmi'"
        + "     + '?user=' + encodeURIComponent(user) + '&password=' + encodeURIComponent(password);")
    private static native String webSocketUrl();

    @JSBody(params = { "message" }, script = "console.warn(message);")
    private static native void warn(String message);

    @JSBody(params = { "message" }, script =
        "document.getElementById('app-root').textContent = message;")
    private static native void showFatal(String message);
}
