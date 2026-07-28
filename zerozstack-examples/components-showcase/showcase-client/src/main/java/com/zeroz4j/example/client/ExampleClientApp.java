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
package com.zeroz4j.example.client;

import com.zeroz4j.client.Zeroz4jClient;
import com.zeroz4j.ui.component.Login;
import com.zeroz4j.api.RmiSecurityContext;
import org.teavm.jso.JSBody;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;

public class ExampleClientApp {

    private static boolean started = false;

    public static void main(String[] args) {
        HTMLElement appRoot = Window.current().getDocument().getElementById("app-root");

        Login[] loginHolder = new Login[1];
        loginHolder[0] = new Login((username, password) -> {
            // Credentials ride the WebSocket handshake; DevAuth validates them server-side.
            String wsUrl = getWebSocketUrl()
                    + "?user=" + encode(username) + "&password=" + encode(password);
            Zeroz4jClient.connect(wsUrl, () -> {
                RmiSecurityContext.onAuthenticated(() -> {
                    if (started) {
                        return;
                    }
                    started = true;
                    appRoot.setInnerHTML("");
                    appRoot.appendChild(new MainLayout().getElement());
                });
                // Invalid credentials leave the session anonymous: no AUTH frame arrives.
                Window.setTimeout(() -> {
                    if (!RmiSecurityContext.isAuthenticated()) {
                        loginHolder[0].showError("Sign-in failed - try demo/demo or admin/admin");
                    }
                }, 3000);
            });
        });
        loginHolder[0].setHint("Demo users: demo / demo · admin / admin (admin unlocks the Admin view)");
        appRoot.appendChild(loginHolder[0].getElement());
    }

    @JSBody(params = {"value"}, script = "return encodeURIComponent(value);")
    private static native String encode(String value);

    @JSBody(script =
        "var l = window.location;" +
        "var path = l.pathname;" +
        "var idx = path.lastIndexOf('/');" +
        "if (idx !== -1) { path = path.substring(0, idx + 1); } else { path = '/'; }" +
        "return (l.protocol === 'https:' ? 'wss://' : 'ws://') + l.host + path + 'wasm-rmi';")
    private static native String getWebSocketUrl();
}

