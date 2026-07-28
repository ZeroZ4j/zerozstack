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
package com.zeroz4j.ui.component;

import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.VerticalLayout;
import org.teavm.jso.dom.events.KeyboardEvent;

/**
 * Username/password sign-in form.
 *
 * <p>Collects credentials and hands them to a {@link LoginListener}; how they are
 * verified is the application's concern (the zeroz4j examples pass them as WebSocket
 * handshake parameters validated by the server's dev-mode {@code DevAuth}). The form
 * disables itself on submit; call {@link #showError(String)} to report a failed attempt
 * and re-enable it.</p>
 *
 * <pre>{@code
 * Login login = new Login((username, password) -> connectWith(username, password));
 * login.setHint("Demo users: demo / demo · admin / admin");
 * }</pre>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Submit Paths:</b> The button click and the Enter key in either field submit.</li>
 *   <li><b>No Client-Side Verification:</b> This component never validates credentials —
 *       treat the server as the only authority.</li>
 * </ul>
 */
public class Login extends Card {

    /** Receives the submitted credentials. */
    @FunctionalInterface
    public interface LoginListener {
        /**
         * Invoked on submit.
         *
         * @param username the entered username, trimmed
         * @param password the entered password (may be empty, never null)
         */
        void login(String username, String password);
    }

    private final TextField usernameField;
    private final TextField passwordField;
    private final Button submitButton;
    private final Div errorDiv;
    private final Div hintDiv;

    /**
     * Creates a sign-in form titled "Sign in".
     *
     * @param listener receives submitted credentials
     */
    public Login(LoginListener listener) {
        this("Sign in", listener);
    }

    /**
     * Creates a sign-in form.
     *
     * @param title    the card title
     * @param listener receives submitted credentials
     */
    public Login(String title, LoginListener listener) {
        super();
        addClassName("w-96");
        addClassName("bg-base-200");
        addClassName("shadow-xl");
        addClassName("mx-auto");
        addClassName("mt-24");

        VerticalLayout layout = new VerticalLayout();
        layout.addClassName("gap-3");
        layout.addClassName("p-6");

        layout.add(new CardTitle(title));

        usernameField = new TextField("Username");
        usernameField.addClassName("w-full");

        passwordField = new TextField("Password");
        passwordField.getElement().setAttribute("type", "password");
        passwordField.addClassName("w-full");

        submitButton = new Button(title);
        submitButton.addClassName("btn-primary");
        submitButton.addClassName("w-full");
        submitButton.addClickListener(e -> submit(listener));

        org.teavm.jso.dom.events.EventListener<KeyboardEvent> enterSubmits = evt -> {
            if ("Enter".equals(evt.getKey())) {
                submit(listener);
            }
        };
        usernameField.addDomEventListener("keydown", enterSubmits);
        passwordField.addDomEventListener("keydown", enterSubmits);

        errorDiv = new Div();
        errorDiv.addClassName("text-error");
        errorDiv.addClassName("text-sm");

        hintDiv = new Div();
        hintDiv.addClassName("text-sm");
        hintDiv.addClassName("opacity-60");

        layout.add(usernameField, passwordField, submitButton, errorDiv, hintDiv);
        add(layout);
    }

    private void submit(LoginListener listener) {
        String username = usernameField.getValue();
        String password = passwordField.getValue();
        if (username == null || username.trim().isEmpty()) {
            return;
        }
        errorDiv.setText("");
        submitButton.setEnabled(false);
        listener.login(username.trim(), password == null ? "" : password);
    }

    /**
     * Sets the helper line under the form (e.g. demo credentials).
     *
     * @param hint the hint text; empty hides it
     */
    public void setHint(String hint) {
        hintDiv.setText(hint == null ? "" : hint);
    }

    /**
     * Shows a failed-attempt message and re-enables the form.
     *
     * @param message the error text
     */
    public void showError(String message) {
        errorDiv.setText(message);
        submitButton.setEnabled(true);
    }
}
