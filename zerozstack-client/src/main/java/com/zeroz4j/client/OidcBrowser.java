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

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * The browser APIs {@link OidcClient} needs, isolated behind one class.
 *
 * <p>Everything here is a thin call into the platform — randomness, hashing, storage, navigation and
 * an HTTP POST. Keeping them together means the login flow itself reads as ordinary Java, and means
 * the parts that cannot run outside a browser are in one place rather than smeared through the
 * protocol logic.</p>
 *
 * <p>Framework-internal; applications use {@link OidcClient}.</p>
 */
final class OidcBrowser {

    private OidcBrowser() {}

    /** Receives a single string result, or null when the operation failed. */
    @JSFunctor
    interface StringCallback extends JSObject {
        void accept(String value);
    }

    /** Receives a token response, or an error message when the exchange failed. */
    @JSFunctor
    interface TokenCallback extends JSObject {
        void accept(String accessToken, String refreshToken, int expiresInSeconds, String error);
    }

    /**
     * 256 bits of cryptographic randomness, base64url encoded.
     *
     * <p>Used for the PKCE code verifier and the state parameter. {@code crypto.getRandomValues} is
     * the browser's CSPRNG; {@code Math.random} is not one and must never appear here.</p>
     */
    @JSBody(params = {}, script =
        "var bytes = new Uint8Array(32);"
        + "crypto.getRandomValues(bytes);"
        + "var str = '';"
        + "for (var i = 0; i < bytes.length; i++) { str += String.fromCharCode(bytes[i]); }"
        + "return btoa(str).replace(/\\+/g, '-').replace(/\\//g, '_').replace(/=+$/, '');")
    static native String randomToken();

    /**
     * The PKCE code challenge: base64url(SHA-256(verifier)).
     *
     * <p>Asynchronous because {@code crypto.subtle.digest} is. The alternative PKCE method,
     * {@code plain}, sends the verifier itself and offers no protection against an intercepted
     * authorization code — so the async plumbing is the price of the flow being worth anything.</p>
     */
    @JSBody(params = { "verifier", "callback" }, script =
        "try {"
        + "  var data = new TextEncoder().encode(verifier);"
        + "  crypto.subtle.digest('SHA-256', data).then(function(buf) {"
        + "    var bytes = new Uint8Array(buf);"
        + "    var str = '';"
        + "    for (var i = 0; i < bytes.length; i++) { str += String.fromCharCode(bytes[i]); }"
        + "    callback(btoa(str).replace(/\\+/g, '-').replace(/\\//g, '_').replace(/=+$/, ''));"
        + "  }).catch(function(e) { callback(null); });"
        + "} catch (e) { callback(null); }")
    static native void codeChallenge(String verifier, StringCallback callback);

    /**
     * Exchanges an authorization code for tokens.
     *
     * <p>A public client sends no secret — there is nowhere in a browser to keep one. The PKCE
     * verifier is what proves this is the same client that started the flow.</p>
     */
    @JSBody(params = { "url", "body", "callback" }, script =
        "fetch(url, {"
        + "  method: 'POST',"
        + "  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },"
        + "  body: body"
        + "}).then(function(response) {"
        + "  return response.text().then(function(text) {"
        + "    if (!response.ok) { callback(null, null, 0, 'HTTP ' + response.status + ': ' + text); return; }"
        + "    var json;"
        + "    try { json = JSON.parse(text); } catch (e) { callback(null, null, 0, 'Malformed token response'); return; }"
        + "    if (!json.access_token) { callback(null, null, 0, 'Token response carried no access_token'); return; }"
        + "    callback(json.access_token, json.refresh_token || null, json.expires_in || 0, null);"
        + "  });"
        + "}).catch(function(e) { callback(null, null, 0, String(e)); });")
    static native void postForm(String url, String body, TokenCallback callback);

    /** @return a query parameter of the current URL, or null */
    @JSBody(params = { "name" }, script =
        "return new URLSearchParams(window.location.search).get(name);")
    static native String queryParameter(String name);

    /** @return the current URL without its query string or fragment */
    @JSBody(params = {}, script = "return window.location.origin + window.location.pathname;")
    static native String currentUrlWithoutQuery();

    /** Navigates away, replacing the current history entry so Back does not re-trigger the login. */
    @JSBody(params = { "url" }, script = "window.location.replace(url);")
    static native void navigate(String url);

    /**
     * Rewrites the address bar without reloading.
     *
     * <p>Used to strip {@code code} and {@code state} once consumed: leaving a used authorization
     * code in the URL puts it into history, bookmarks and any analytics that records paths.</p>
     */
    @JSBody(params = { "url" }, script =
        "window.history.replaceState({}, document.title, url);")
    static native void replaceUrl(String url);

    /** @return a value previously stored for this tab, or null */
    @JSBody(params = { "key" }, script = "try { return sessionStorage.getItem(key); } catch (e) { return null; }")
    static native String storageGet(String key);

    /** Stores a value for this tab only, cleared when the tab closes. */
    @JSBody(params = { "key", "value" }, script = "try { sessionStorage.setItem(key, value); } catch (e) { }")
    static native void storageSet(String key, String value);

    /** Removes a stored value. */
    @JSBody(params = { "key" }, script = "try { sessionStorage.removeItem(key); } catch (e) { }")
    static native void storageRemove(String key);

    /** Runs a callback after a delay, for the silent token refresh. */
    @JSBody(params = { "millis", "callback" }, script =
        "setTimeout(function() { callback(null); }, millis);")
    static native void setTimeout(int millis, StringCallback callback);

    /** Writes a diagnostic line to the browser console. */
    @JSBody(params = { "message" }, script = "console.warn(message);")
    static native void warn(String message);
}
