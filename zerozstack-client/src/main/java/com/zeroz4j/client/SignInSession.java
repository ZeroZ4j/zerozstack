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

import com.zeroz4j.api.SyncFrameTypes;
import org.teavm.jso.JSBody;

/**
 * The browser's half of cookie sign-in.
 *
 * <p>Two things happen with no application code. When the server says the session cookie is old
 * enough, this posts for a new one; script cannot read an {@code HttpOnly} cookie, so the server has
 * to say. And when a connection that came in on a session cookie reconnects anonymous, because
 * somebody signed out in another tab or the account was revoked, this reloads the page, which
 * brings up the sign-in form with the page's address kept.</p>
 *
 * <p>An application only calls {@link #signOut()}.</p>
 *
 * @since 0.11.0
 */
public final class SignInSession {

    private static boolean signedIn;
    private static boolean reloads = true;

    private SignInSession() {
    }

    /**
     * Signs this browser out and goes to the application's root.
     *
     * <p>A real form post, so the cookie is cleared by the response that the next page loads
     * from.</p>
     */
    public static void signOut() {
        postForm(AppBase.url("zeroz4j-session/sign-out"));
    }

    /**
     * Turns off the reload when a session ends while the page is open, for an application that
     * shows its own screen. {@code RmiSecurityContext.onAuthenticationFailed} then says when.
     *
     * @param enabled false to stay on the page
     * @since 0.11.0
     */
    public static void reloadWhenSignedOut(boolean enabled) {
        reloads = enabled;
    }

    /** Called by the client runtime with the last byte of an AUTH frame, before anything else. */
    static void connected(boolean authenticated, byte flags) {
        boolean wasSignedIn = signedIn;
        signedIn = (flags & SyncFrameTypes.AUTH_SESSION_COOKIE) != 0;
        if (wasSignedIn && !authenticated && reloads) {
            reload();
            return;
        }
        if ((flags & SyncFrameTypes.AUTH_RENEW_DUE) != 0) {
            renew(AppBase.url("zeroz4j-session/renew"));
        }
    }

    @JSBody(params = {"url"}, script =
            "var form = document.createElement('form');"
            + "form.method = 'post';"
            + "form.action = url;"
            + "document.body.appendChild(form);"
            + "form.submit();")
    private static native void postForm(String url);

    /** A failed request is left alone: the next connection is told again. */
    @JSBody(params = {"url"}, script =
            "try {"
            + "  fetch(url, { method: 'POST', credentials: 'same-origin', cache: 'no-store' })"
            + "    .catch(function (ignored) {});"
            + "} catch (ignored) {}")
    private static native void renew(String url);

    @JSBody(script = "window.location.reload();")
    private static native void reload();
}
