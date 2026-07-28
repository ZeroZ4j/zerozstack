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
package ${package}.client;

import com.zeroz4j.client.Zeroz4jClient;
import com.zeroz4j.api.RmiSecurityContext;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;

public class ClientApp {
    public static void main(String[] args) {
        Zeroz4jClient.connect(getWebSocketUrl(), () -> {
            RmiSecurityContext.onAuthenticated(() -> {
                HTMLElement appRoot = Window.current().getDocument().getElementById("app-root");
                appRoot.setInnerHTML("<h1>Zeroz4j App is running!</h1>");
            });
        });
    }

    private static String getWebSocketUrl() {
        String protocol = Window.current().getLocation().getProtocol().equals("https:") ? "wss" : "ws";
        String host = Window.current().getLocation().getHost();
        return protocol + "://" + host + "/wasm-rmi";
    }
}