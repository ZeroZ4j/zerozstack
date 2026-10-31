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
package com.zeroz4j.server;

/**
 * The application's half of cookie sign-in: checks credentials, and says who a returning session
 * still is.
 *
 * <p>Write one {@code @ApplicationScoped} bean implementing this. That turns sign-in on: the
 * framework then answers at {@code /zeroz4j-session/...}, and reads the session cookie at every
 * WebSocket handshake. With no such bean those addresses answer 404 and no cookie is read. More
 * than one bean is refused, because picking one decides who can sign in.</p>
 *
 * <p>It is a CDI bean rather than a {@link java.util.ServiceLoader} entry like
 * {@link AuthenticationProvider} because both methods read the application's own user records, and
 * those are reached through injection.</p>
 *
 * <pre>{@code
 * @ApplicationScoped
 * public class Accounts implements SignInHandler {
 *     @Inject UserStore users;
 *
 *     @Override
 *     public AuthenticatedPrincipal signIn(SignInAttempt attempt) {
 *         User user = users.byEmail(attempt.username());
 *         if (user == null || !user.passwordMatches(attempt.password())) {
 *             return null;
 *         }
 *         return new AuthenticatedPrincipal(user.email(), user.roles(), user.tenant());
 *     }
 *
 *     @Override
 *     public AuthenticatedPrincipal resume(SignInSession session) {
 *         User user = users.byEmail(session.name());
 *         if (user == null || user.disabled()
 *                 || session.startedAt().isBefore(user.passwordChangedAt())) {
 *             return null;
 *         }
 *         return new AuthenticatedPrincipal(user.email(), user.roles(), user.tenant());
 *     }
 * }
 * }</pre>
 *
 * <p><b>Both methods run outside any RMI call,</b> so {@link RmiRequestContext} is empty in them.</p>
 *
 * @since 0.11.0
 */
public interface SignInHandler {

    /**
     * Checks credentials.
     *
     * <p>Take the same time for an unknown name as for a wrong password, by hashing the password
     * against a dummy value: the time an answer takes otherwise says which names exist. The
     * framework limits attempts per name and treats every refusal the same.</p>
     *
     * @param attempt what the form carried
     * @return who signed in, or null to refuse. Attributes on this principal are not kept: a
     *         connection is built from what {@link #resume(SignInSession)} returns
     */
    AuthenticatedPrincipal signIn(SignInAttempt attempt);

    /**
     * Says who a valid session is now. Called at every handshake that presents a session, and at
     * every renewal.
     *
     * <p>The cookie carries who signed in and when, never roles, so roles here are always current.
     * Returning null ends this session for that handshake, which is how a disabled account or a
     * changed password takes effect. Compare {@link SignInSession#startedAt()} with the time of the
     * password change: that survives a restart, where
     * {@link SignInSessions#revokeUser(String, String, String)} on its own does not.</p>
     *
     * @param session the verified session
     * @return the current principal, with any attributes to keep on the connection, or null to
     *         refuse
     */
    AuthenticatedPrincipal resume(SignInSession session);

    /**
     * Told when somebody signs out, after the session has been ended.
     *
     * <p>The framework's record of ended sessions is kept in memory. Keep {@code session.id()} in
     * your own store and refuse it in {@link #resume(SignInSession)} if a copied cookie must stay
     * dead across a restart.</p>
     *
     * @param session the session that ended
     */
    default void signedOut(SignInSession session) {
    }
}
