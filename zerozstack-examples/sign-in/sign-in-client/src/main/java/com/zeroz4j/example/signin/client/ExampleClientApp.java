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
package com.zeroz4j.example.signin.client;

import com.zeroz4j.api.RmiSecurityContext;
import com.zeroz4j.client.AppBase;
import com.zeroz4j.client.Zeroz4jClient;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.Login;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * Shows the sign-in form or the application, depending on what the server decided at the handshake.
 * The browser attached the session cookie by itself; nothing here handles it.
 */
public class ExampleClientApp {

    private static boolean shown;

    public static void main(String[] args) {
        Zeroz4jClient.connect(AppBase.webSocketUrl(),
                () -> RmiSecurityContext.onResolved(() -> new Thread(ExampleClientApp::show).start()));
    }

    /** Once: this runs again after every reconnect, and the page should stay as it is. */
    private static void show() {
        if (shown) {
            return;
        }
        shown = true;
        HTMLElement root = Window.current().getDocument().getElementById("app-root");
        Component.replaceContents(root,
                RmiSecurityContext.isAuthenticated() ? new HomePage() : new Login());
    }
}
