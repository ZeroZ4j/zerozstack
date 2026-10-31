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

import com.zeroz4j.ui.theme.Emphasis;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.VerticalLayout;
import com.zeroz4j.ui.theme.TextStyle;
import org.teavm.jso.JSBody;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.html.HTMLInputElement;

/**
 * Sign-in form: a real HTML {@code <form>}, with fields a password manager recognizes.
 *
 * <p><b>With no listener it posts to the framework's cookie sign-in.</b> The browser submits the
 * form itself, so it offers to save the password and fills it in next time. The server answers
 * with the session cookie and sends the person to the page they asked for; a refusal comes back to
 * this form with the reason. See {@code docs/guides/cookie-sign-in.md}.</p>
 *
 * <pre>{@code
 * root.add(new Login());
 * }</pre>
 *
 * <p><b>With a listener it hands the credentials over instead</b> and stays on the page, which is
 * what the examples do with their development logins. The form disables itself on submit; call
 * {@link #showError(String)} to report a failed attempt and re-enable it. Browsers are less
 * willing to offer to save a password from a form that never really submits.</p>
 *
 * <pre>{@code
 * Login login = new Login((username, password) -> connectWith(username, password));
 * login.setHint("Demo users: demo / demo · admin / admin");
 * }</pre>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Submit Paths:</b> The button and the Enter key in either field both submit the form.
 *       That is the browser's own behavior, so there are no key listeners here.</li>
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

    /** The address the form posts to, relative to the shell's {@code <base href>}. */
    private static final String SIGN_IN_ADDRESS = "zeroz4j-session/sign-in";

    /** Where the name is kept while a refused attempt comes back, in this tab only. */
    private static final String KEPT_NAME = "zeroz4j.signin.username";

    private final FormElement form = new FormElement();
    private final TextField usernameField;
    private final TextField passwordField;
    private final Button submitButton;
    private final Div errorDiv;
    private final Div hintDiv;
    private boolean posts;

    /** The {@code <form>} element. Added here because nothing else in the library needs one. */
    private static final class FormElement extends Component implements HasComponents {
        FormElement() {
            super("form");
        }

        @Override
        public Component getComponent() {
            return this;
        }
    }

    /**
     * Creates a sign-in form titled "Sign in" that posts to the framework's cookie sign-in, with
     * an "Email" field and a "Password" field.
     *
     * @since 0.11.0
     */
    public Login() {
        this("Sign in");
    }

    /**
     * Creates a sign-in form that posts to the framework's cookie sign-in.
     *
     * @param title the card title and the button's words
     * @since 0.11.0
     */
    public Login(String title) {
        this(title, "Email", null);
        form.getElement().setAttribute("method", "post");
        form.getElement().setAttribute("action", SIGN_IN_ADDRESS);
        form.getElement().setAttribute("accept-charset", "UTF-8");
        usernameField.getElement().setAttribute("required", "required");
        passwordField.getElement().setAttribute("required", "required");
        setHiddenField("return", requestedPage());
        posts = true;
    }

    /**
     * Creates a sign-in form titled "Sign in" that hands credentials to a listener.
     *
     * @param listener receives submitted credentials
     */
    public Login(LoginListener listener) {
        this("Sign in", listener);
    }

    /**
     * Creates a sign-in form that hands credentials to a listener.
     *
     * @param title    the card title
     * @param listener receives submitted credentials
     */
    public Login(String title, LoginListener listener) {
        this(title, "Username", listener);
    }

    private Login(String title, String usernameLabel, LoginListener listener) {
        super();
        addClassName("w-96");
        addClassName("max-w-full");
        addClassName("shadow-xl");
        addClassName("mx-auto");
        addClassName("mt-24");

        VerticalLayout layout = new VerticalLayout();
        layout.addClassName("gap-3");
        layout.addClassName("p-6");

        layout.add(new CardTitle(title));

        // The single-argument constructor sets a placeholder, and a placeholder stops naming the
        // box the moment somebody types in it. The library's own sign-in screen should not be the
        // example of that.
        usernameField = new TextField().withLabel(usernameLabel);
        usernameField.addClassName("w-full");
        usernameField.getElement().setAttribute("name", "username");
        usernameField.getElement().setAttribute("autocomplete", "username");

        passwordField = new TextField().withLabel("Password");
        passwordField.getElement().setAttribute("type", "password");
        passwordField.getElement().setAttribute("name", "password");
        passwordField.getElement().setAttribute("autocomplete", "current-password");
        passwordField.addClassName("w-full");

        // A button inside a form submits it, which is also what Enter in a field does.
        submitButton = new Button(title);
        submitButton.getElement().setAttribute("type", "submit");
        submitButton.addClassName("btn-primary");
        submitButton.addClassName("w-full");

        errorDiv = new Div();
        errorDiv.addClassName("text-error");
        // FULL, not the size's own fade: fading the one line that says what went wrong is
        // exactly backwards.
        errorDiv.addClassName(TextStyle.SECONDARY.getClassNames(Emphasis.FULL));
        // A sign-in that failed has to be heard, not only seen. An alert is read out the moment
        // the message appears, wherever the person's attention happens to be at the time.
        errorDiv.getElement().setAttribute("role", "alert");

        hintDiv = new Div();
        TextStyle.SECONDARY.applyTo(hintDiv);

        layout.add(usernameField, passwordField, submitButton, errorDiv, hintDiv);
        form.add(layout);
        add(form);

        // Not addDomEventListener: that runs the handler on a new thread, by which time it is too
        // late to stop the browser submitting.
        form.getElement().addEventListener("submit", (Event evt) -> {
            if (listener != null) {
                evt.preventDefault();
                new Thread(() -> submit(listener)).start();
            } else {
                keepName(inputValue(usernameField));
                submitButton.setEnabled(false);
            }
        });
    }

    /** On attach, so a caption changed with {@link #withUsernameLabel(String)} is in the sentence. */
    @Override
    protected void onAttach() {
        super.onAttach();
        if (posts) {
            posts = false;
            showReturnedProblem();
        }
    }

    /** Reads the element rather than the field's model: a password manager may not fire input. */
    private static String inputValue(TextField field) {
        HTMLInputElement input = field.getElement().cast();
        return input.getValue();
    }

    private void submit(LoginListener listener) {
        String username = inputValue(usernameField);
        String password = inputValue(passwordField);
        if (username == null || username.trim().isEmpty()) {
            return;
        }
        errorDiv.setText("");
        submitButton.setEnabled(false);
        listener.login(username.trim(), password == null ? "" : password);
    }

    /**
     * Shows what the server said when this form was posted and came back, and puts the name back.
     * The name has to be put back: an empty box is one a password manager fills from whatever it
     * has saved, so somebody who mistyped a password would watch their own name be replaced.
     */
    private void showReturnedProblem() {
        String problem = returnedProblem();
        String kept = takeKeptName();
        if (problem == null) {
            return;
        }
        if (kept != null) {
            usernameField.setValue(kept);
        }
        if ("locked".equals(problem)) {
            showError("Too many attempts. Try again later.");
        } else if ("error".equals(problem)) {
            showError("Sign-in failed. Try again later.");
        } else {
            showError("Wrong " + usernameField.getLabel().toLowerCase() + " or password.");
        }
    }

    /**
     * Changes the caption of the name field, for an application that signs in with something other
     * than an email address.
     *
     * @param label the caption, such as "Username"
     * @return this form
     * @since 0.11.0
     */
    public Login withUsernameLabel(String label) {
        usernameField.setLabel(label);
        return this;
    }

    /**
     * Adds a field the form carries but does not show, or changes one. The server reads it with
     * {@code SignInAttempt.field}.
     *
     * @param name  the field name
     * @param value the value
     * @since 0.11.0
     */
    public void setHiddenField(String name, String value) {
        HTMLElement existing = hiddenField(form.getElement(), name);
        HTMLElement input = existing != null ? existing
                : HTMLDocument.current().createElement("input");
        input.setAttribute("type", "hidden");
        input.setAttribute("name", name);
        input.setAttribute("value", value == null ? "" : value);
        if (existing == null) {
            form.getElement().appendChild(input);
        }
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

    @JSBody(params = {"form", "name"}, script =
            "var inputs = form.querySelectorAll('input[type=hidden]');"
            + "for (var idx = 0; idx < inputs.length; idx++) {"
            + "  if (inputs[idx].name === name) { return inputs[idx]; }"
            + "}"
            + "return null;")
    private static native HTMLElement hiddenField(HTMLElement form, String name);

    /**
     * The page being shown, as a route under the application's root with its query. The server
     * decides whether it is safe to go back to; this only reports it.
     */
    @JSBody(script =
            "try {"
            + "  var params = new URLSearchParams(window.location.search);"
            + "  params.delete('signin');"
            + "  var base = new URL(document.baseURI).pathname;"
            + "  var path = window.location.pathname;"
            + "  if (path.indexOf(base) === 0) { path = path.substring(base.length); }"
            + "  else if (path + '/' === base) { path = ''; }"
            + "  var query = params.toString();"
            + "  return path + (query ? '?' + query : '');"
            + "} catch (ignored) { return ''; }")
    private static native String requestedPage();

    /** Reads why the server sent the form back, and takes it out of the address bar. */
    @JSBody(script =
            "try {"
            + "  var url = new URL(window.location.href);"
            + "  var problem = url.searchParams.get('signin');"
            + "  if (problem !== null) {"
            + "    url.searchParams.delete('signin');"
            + "    window.history.replaceState(window.history.state, '', url.toString());"
            + "  }"
            + "  return problem;"
            + "} catch (ignored) { return null; }")
    private static native String returnedProblem();

    /** {@code sessionStorage} and never the address: a name does not belong in a log or history. */
    @JSBody(params = {"name"}, script =
            "try { window.sessionStorage.setItem('" + KEPT_NAME + "', name); } catch (ignored) {}")
    private static native void keepName(String name);

    @JSBody(script =
            "try {"
            + "  var kept = window.sessionStorage.getItem('" + KEPT_NAME + "');"
            + "  window.sessionStorage.removeItem('" + KEPT_NAME + "');"
            + "  return kept;"
            + "} catch (ignored) { return null; }")
    private static native String takeKeptName();
}
