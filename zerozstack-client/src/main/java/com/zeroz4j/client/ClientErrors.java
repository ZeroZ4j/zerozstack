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
package com.zeroz4j.client;

import com.zeroz4j.api.RmiClientExecutor;
import com.zeroz4j.api.RmiSecurityContext;
import com.zeroz4j.api.i18n.FrameworkText;
import com.zeroz4j.api.i18n.Message;
import com.zeroz4j.api.i18n.ReadsMessagesOnce;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Sends uncaught browser errors to the server log and says so on the page when the application
 * does not start.
 *
 * <p>Without it an exception in the compiled client lands in a console nobody has open and the page
 * just stops. The report goes over the existing connection as a call to
 * {@link com.zeroz4j.api.ClientErrorRpc}, on a thread of its own, because a browser error handler
 * runs on a stack that cannot suspend. A report that fails is never reported: the call is wrapped
 * in a catch that does nothing, at most {@value #MAX_PER_PAGE} reports leave one page load, an
 * identical error twice running is sent once, and the first answer of "not taken" from the server
 * stops everything.</p>
 *
 * <p>Both halves are on by default. {@link Zeroz4jClient#reportErrors(boolean)} and
 * {@link Zeroz4jClient#showStartFailure(boolean)} turn them off, and the server has
 * {@code zeroz.clienterrors.enabled}.</p>
 *
 * @since 0.11.0
 */
final class ClientErrors {

    /** Most reports one page load sends. The server has its own, per connection. */
    static final int MAX_PER_PAGE = 20;

    /** How long the connection may take to become usable before the page says it did not. */
    static final int START_DEADLINE_MILLIS = 15_000;

    private static final String RPC = "com.zeroz4j.api.ClientErrorRpc";
    private static final int MAX_MESSAGE = 1000;
    private static final int MAX_STACK = 3000;

    private static boolean reporting = true;
    private static boolean showingStartFailure = true;
    private static boolean installed;
    private static boolean stopped;
    private static int sent;
    private static String previous;

    private ClientErrors() {}

    static void setReporting(boolean enabled) {
        reporting = enabled;
    }

    static void setShowingStartFailure(boolean enabled) {
        showingStartFailure = enabled;
    }

    /** What the page hands over when something is thrown that nobody caught. */
    @JSFunctor
    interface Sink extends JSObject {
        void report(String kind, String message, String stack);
    }

    /** What a timer does. */
    @JSFunctor
    interface Task extends JSObject {
        void run();
    }

    /**
     * Installs the global handlers, once, and the start watchdog. Called by
     * {@link Zeroz4jClient#connect(String, Runnable)} before anything else, so a failure while the
     * application is being built is caught too.
     */
    static void install() {
        if (installed) {
            return;
        }
        installed = true;
        installHandlers(ClientErrors::handle);
        RmiSecurityContext.onAuthenticationFailed(() -> {
            if (showingStartFailure) {
                show(FrameworkText.uiStartRefused());
            }
        });
        RmiSecurityContext.onResolved(ClientErrors::clearFailure);
        setTimeout(START_DEADLINE_MILLIS, ClientErrors::checkStarted);
    }

    @ReadsMessagesOnce("shown once on a page that has not started; the language cannot change "
            + "without a connection, and Reload replaces the page")
    private static boolean show(Message words) {
        return showFailure(words.text(), FrameworkText.uiReload().text());
    }

    @ReadsMessagesOnce("appended once to the message above")
    private static void addReference(String reference) {
        appendReference(FrameworkText.uiReference().text(), reference);
    }

    private static void checkStarted() {
        if (!RmiSecurityContext.isResolved() && showingStartFailure) {
            show(FrameworkText.uiStartNoConnection());
        }
    }

    /**
     * Shows the start failure for an application whose start code threw.
     *
     * @param failure what was thrown
     */
    static void startThrew(Throwable failure) {
        handle("start", String.valueOf(failure), "");
    }

    private static void handle(String kind, String message, String stack) {
        try {
            if ("start".equals(kind) || RmiSecurityContext.isResolved()) {
                if (showingStartFailure && show(FrameworkText.uiStartFailed())) {
                    // The page is empty, so a reference is worth showing. See setReference.
                    pendingReference = true;
                }
            }
            if (!admit(kind, message)) {
                return;
            }
            send(kind, message, stack);
        } catch (Throwable ignored) {
            // Deliberately empty. An error while reporting an error would be reported in turn.
        }
    }

    private static boolean pendingReference;

    /**
     * Decides whether this error leaves the page. Pure, so the rules can be tested.
     *
     * @param kind the kind of error
     * @param message its message
     * @return true when it should be sent
     */
    static synchronized boolean admit(String kind, String message) {
        if (!reporting || stopped || sent >= MAX_PER_PAGE) {
            return false;
        }
        String key = kind + ":" + message;
        if (key.equals(previous)) {
            return false;
        }
        previous = key;
        sent++;
        return true;
    }

    /** Resets the counters. For tests. */
    static synchronized void reset() {
        reporting = true;
        showingStartFailure = true;
        stopped = false;
        sent = 0;
        previous = null;
        pendingReference = false;
    }

    /**
     * Cuts text to a length. The server cuts again; this keeps a huge message off the wire.
     *
     * @param text the text, or null
     * @param max the longest result, not counting an ellipsis
     * @return the text, never null
     */
    static String trim(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private static void send(String kind, String message, String stack) {
        final String shortMessage = trim(message, MAX_MESSAGE);
        final String shortStack = trim(stack, MAX_STACK);
        new Thread(() -> {
            try {
                Object answer = RmiClientExecutor.executeCall(RPC, "report",
                        new Object[] {kind, shortMessage, shortStack, pagePath()});
                if (answer == null) {
                    stopped = true;
                } else if (pendingReference) {
                    pendingReference = false;
                    addReference(String.valueOf(answer));
                }
            } catch (Throwable ignored) {
                // Deliberately empty, for the reason given in handle.
            }
        }).start();
    }

    // ------------------------------------------------------------------ browser

    // Chains whatever handler the page had, and never lets an exception out of its own: one thrown
    // from onerror would re-enter onerror. The names are longer than one letter on purpose; see
    // JsBodyNamingContractTest.
    @JSBody(params = {"sink"}, script =
        "var before = window.onerror;"
        + "window.onerror = function (message, source, line, column, error) {"
        + "  try {"
        + "    var detail = (error && error.stack) ? error.stack : '';"
        + "    var where = source ? (' at ' + String(source).split('?')[0] + ':' + line + ':' + column) : '';"
        + "    sink('error', String(message) + where, detail);"
        + "  } catch (ignored) {}"
        + "  if (before) { return before(message, source, line, column, error); }"
        + "  return false;"
        + "};"
        + "window.addEventListener('unhandledrejection', function (event) {"
        + "  try {"
        + "    var reason = event ? event.reason : null;"
        + "    var detail = (reason && reason.stack) ? reason.stack : '';"
        + "    sink('rejection', reason && reason.message ? String(reason.message) : String(reason), detail);"
        + "  } catch (ignored) {}"
        + "});")
    private static native void installHandlers(Sink sink);

    @JSBody(params = {"millis", "task"}, script = "setTimeout(function () { try { task(); } catch (ignored) {} }, millis);")
    private static native void setTimeout(int millis, Task task);

    @JSBody(script = "return window.location.pathname || '';")
    private static native String pagePath();

    /**
     * Puts the message in the application root when nothing is drawn there. Returns false, and does
     * nothing, when the root is missing or the application has drawn something into it.
     */
    @JSBody(params = {"text", "label"}, script =
        "var root = document.getElementById('app-root');"
        + "if (!root || root.children.length > 0) { return false; }"
        + "root.textContent = '';"
        + "var box = document.createElement('div');"
        + "box.id = 'zeroz4j-start-failed';"
        + "box.setAttribute('role', 'alert');"
        + "box.className = 'alert alert-error';"
        + "box.style.cssText = 'margin:2rem;display:flex;gap:1rem;align-items:center;';"
        + "var words = document.createElement('span');"
        + "words.id = 'zeroz4j-start-failed-text';"
        + "words.textContent = text;"
        + "var button = document.createElement('button');"
        + "button.type = 'button';"
        + "button.className = 'btn btn-sm';"
        + "button.textContent = label;"
        + "button.addEventListener('click', function () { window.location.reload(); });"
        + "box.appendChild(words);"
        + "box.appendChild(button);"
        + "root.appendChild(box);"
        + "return true;")
    private static native boolean showFailure(String text, String label);

    @JSBody(params = {"label", "reference"}, script =
        "var words = document.getElementById('zeroz4j-start-failed-text');"
        + "if (words) { words.textContent = words.textContent + ' ' + label + ': ' + reference; }")
    private static native void appendReference(String label, String reference);

    @JSBody(script =
        "var box = document.getElementById('zeroz4j-start-failed');"
        + "if (box && box.parentNode) { box.parentNode.removeChild(box); }")
    private static native void clearFailure();
}
