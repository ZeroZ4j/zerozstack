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
package com.smoke.client;

import com.smoke.model.Message;
import com.smoke.service.EchoService;
import com.smoke.service.EchoService_Stub;
import com.smoke.signals.SmokeSignals;
import com.zeroz4j.client.Zeroz4jClient;
import com.zeroz4j.api.RmiSecurityContext;
import com.zeroz4j.signals.Effect;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;

public class ClientApp {
    public static void main(String[] args) {
        Zeroz4jClient.connect(getWebSocketUrl(), () -> {
            RmiSecurityContext.onAuthenticated(() -> {
                HTMLElement appRoot = Window.current().getDocument().getElementById("app-root");
                appRoot.setInnerHTML("<h1>Zeroz4j App is running!</h1>"
                    + "<p id='tick'>tick: waiting</p><p id='echo'>echo: waiting</p>");

                // Defect 1: a shared @DataModel signal arriving proves a serializer was generated.
                Effect.create(() -> {
                    Message tick = SmokeSignals.TICK.get();
                    HTMLElement el = Window.current().getDocument().getElementById("tick");
                    if (el != null && tick != null) {
                        el.setInnerHTML("tick: " + tick.text);
                    }
                });

                // Defect 3: an RMI call returning proves the server implementation was discovered.
                new Thread(() -> {
                    EchoService echo = new EchoService_Stub();
                    Message reply = echo.echo("hello");
                    HTMLElement el = Window.current().getDocument().getElementById("echo");
                    if (el != null) {
                        el.setInnerHTML("echo: " + (reply == null ? "null" : reply.text));
                    }
                }).start();
            });
        });
    }

    private static String getWebSocketUrl() {
        String protocol = Window.current().getLocation().getProtocol().equals("https:") ? "wss" : "ws";
        String host = Window.current().getLocation().getHost();
        return protocol + "://" + host + "/wasm-rmi";
    }
}