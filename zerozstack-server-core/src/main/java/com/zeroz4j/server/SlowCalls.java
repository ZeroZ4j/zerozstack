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

import com.zeroz4j.api.SlowCall;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.logging.Logger;

/**
 * Starts work that may outlast the 30 second limit on a remote call.
 *
 * <p>Inject it into a service and return what {@link #start(String, SlowCallWork)} returns:</p>
 *
 * <pre>{@code
 * @ApplicationScoped
 * public class ReviewServiceImpl implements ReviewService {
 *     @Inject SlowCalls slowCalls;
 *
 *     @Override
 *     public SlowCall<Review> writeReview(long callId) {
 *         return slowCalls.start("review-" + callId, job -> {
 *             job.progress(10, "Reading the transcript");
 *             return model.review(callId);
 *         });
 *     }
 * }
 * }</pre>
 *
 * <p>{@code @Secured}, {@code @RolesAllowed} and validation apply to the service method as always,
 * so they are checked before the work starts.</p>
 *
 * <h2>Whose work it is</h2>
 *
 * <p>The connection decides. A signed-in caller owns the work as that user in that tenant, so any
 * browser of theirs can follow it. An anonymous caller owns it as that browser, through the signed
 * browser id in its cookie. Neither is an argument, and the WebSocket session is not part of it, so
 * a reconnect or a page reload finds the same work.</p>
 *
 * <h2>What the work can read</h2>
 *
 * <p>The work runs with the caller's {@link RmiRequestContext}: principal, roles, tenant, browser
 * id and language. {@link RmiRequestContext#getSessionId()} is null, because the connection that
 * started the work may be gone long before it ends. The CDI request scope is active. On a WAR the
 * thread comes from the {@link SessionThreadFactoryProvider}, so it carries what the container's
 * factory carries.</p>
 *
 * <h2>Limits</h2>
 *
 * <p>Results live in memory. A server restart loses them and any running work, and a browser that
 * was waiting is told so in a sentence. See {@link ServerSettings#SLOW_CALL_KEEP_MINUTES},
 * {@link ServerSettings#SLOW_CALL_MAX_KEPT_PER_CLIENT},
 * {@link ServerSettings#SLOW_CALL_MAX_RUNNING_PER_CLIENT} and
 * {@link ServerSettings#SLOW_CALL_MAX_RUNNING}.</p>
 *
 * @since 0.11.0
 */
@ApplicationScoped
public class SlowCalls {

    private static final Logger LOG = Logger.getLogger(SlowCalls.class.getName());

    @Inject
    ServerRuntime runtime;

    /** Every bean of this server, for the request scope the work runs in. */
    @Inject
    @Any
    Instance<Object> beans;

    /**
     * Starts the work and returns at once.
     *
     * <p>Call this inside a service method. Starting a key this caller already has running hands
     * back the running work and ignores {@code work}, so a second click or a second tab cannot
     * start it twice. Starting a key that has ended replaces it, and the old result is dropped.</p>
     *
     * @param <T>  what the work returns
     * @param key  what the work is called, at most 200 characters. It is unique per owner, and it
     *             is what a screen calls {@code SlowCall.find} with after a reload. Use one key for
     *             one kind of value
     * @param work the work
     * @return the handle to return from the service method
     * @throws ClientVisibleException   when the caller or the server is running as many slow calls
     *                                  as it may; nothing started
     * @throws IllegalStateException    outside a service call, or for a connection with neither a
     *                                  sign-in nor a browser id: there is nobody to keep it for
     * @throws IllegalArgumentException when the key is empty or too long, or the work is null
     *
     * <p><b>Under the hood:</b> the handle goes back on the service call's own
     * {@code 0x01 RPC_RESPONSE}, and the owner's subscribed connections hear about the work on
     * {@code 0x17 SIGNAL_UPD}.</p>
     */
    public <T> SlowCall<T> start(String key, SlowCallWork<T> work) {
        return runtime.slowCalls().start(key, work, this::inRequestScope);
    }

    /**
     * Runs a piece of work in a CDI request scope of its own, as every call frame is: a thread of
     * ours has none, and a {@code @RequestScoped} bean must resolve inside the work.
     */
    private void inRequestScope(SlowCallRegistry.Body body) throws Exception {
        RequestContextController requestContext =
                beans.select(RequestContextController.class).get();
        boolean activated = requestContext.activate();
        try {
            body.run();
        } finally {
            if (activated) {
                try {
                    requestContext.deactivate();
                } catch (RuntimeException containerGone) {
                    // The same case the engine meets: the container stopped under a running job.
                    LOG.fine("[zeroz4j] The request scope of a slow call was already gone when "
                            + "it ended; the container is shutting down. " + containerGone);
                }
            }
        }
    }
}
