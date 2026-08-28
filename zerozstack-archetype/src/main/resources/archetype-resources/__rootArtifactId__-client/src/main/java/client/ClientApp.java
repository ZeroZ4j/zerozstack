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
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.theme.TextStyle;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;

public class ClientApp {
    public static void main(String[] args) {
        Zeroz4jClient.connect(getWebSocketUrl(), () -> {
            // onResolved fires once the server has answered, whether this connection ended up
            // authenticated or anonymous — it is the "connection is usable" signal, and what you
            // build the UI from. onAuthenticated is about identity and never fires for an anonymous
            // connection, so mounting from it would leave this page blank.
            //
            // If your view makes an RMI call while it is being built, wrap it:
            //     RmiSecurityContext.onResolved(() -> new Thread(ClientApp::buildUi).start());
            // This callback runs on a stack that began in native JavaScript, where a suspending call
            // cannot start.
            RmiSecurityContext.onResolved(() -> {
                // Everything on this page is built out of Java objects, never HTML strings.
                // The class names come from Tailwind and daisyUI, which index.html loads:
                // this framework ships no stylesheet of its own.
                Div card = new Div();
                card.addClassName("card bg-base-200 shadow-xl mx-auto mt-24 w-96");

                // Ask for a text size by name instead of describing one. There are five, in
                // com.zeroz4j.ui.theme.TextStyle, and asking keeps every screen agreeing.
                Div body = new Div(
                    TextStyle.PAGE_TITLE.span("It works"),
                    TextStyle.SECONDARY.paragraph("This page is Java, compiled for the browser, "
                        + "and already talking to the server over one socket."));
                body.addClassName("card-body items-center gap-2 text-center");
                card.add(body);

                HTMLElement appRoot = Window.current().getDocument().getElementById("app-root");
                appRoot.setInnerHTML("");
                appRoot.appendChild(card.getElement());
            });
        });
    }

    private static String getWebSocketUrl() {
        String protocol = Window.current().getLocation().getProtocol().equals("https:") ? "wss" : "ws";
        String host = Window.current().getLocation().getHost();
        return protocol + "://" + host + "/wasm-rmi";
    }
}