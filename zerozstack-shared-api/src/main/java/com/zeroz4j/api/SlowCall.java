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
package com.zeroz4j.api;

/**
 * Work that may outlast the 30 second limit on a remote call.
 *
 * <p>Declare the service method with this return type. The call itself returns at once with this
 * handle, the work runs on the server, and the value comes back by itself:</p>
 *
 * <pre>{@code
 * // shared
 * @RmiService
 * public interface ReviewService {
 *     SlowCall<Review> writeReview(long callId);
 * }
 *
 * // server
 * @ApplicationScoped
 * public class ReviewServiceImpl implements ReviewService {
 *     @Inject SlowCalls slowCalls;
 *
 *     @Override
 *     public SlowCall<Review> writeReview(long callId) {
 *         return slowCalls.start("review-" + callId, job -> model.review(callId));
 *     }
 * }
 *
 * // client
 * SlowCall<Review> call = new ReviewService_Stub().writeReview(id);
 * Effect.create(() -> show(call.state()));
 * }</pre>
 *
 * <p><b>The key names the work</b>, and it is unique per browser or per signed-in user. Starting a
 * key that is still running hands back the running one. After a page reload nothing in the browser
 * remembers the handle, so ask for it again by key with {@link #find(String)}.</p>
 *
 * <p>The guide is {@code docs/guides/slow-calls.md}.</p>
 *
 * @param <T> what the work returns: anything that may cross the wire
 * @since 0.11.0
 */
public final class SlowCall<T> {

    /**
     * The browser half of a slow call. {@code WasmRmiClient} installs it when the client starts.
     *
     * <p>Framework-internal. Applications call {@link SlowCall#state()} and
     * {@link SlowCall#cancel()}.</p>
     */
    public interface Tracker {

        /**
         * @param <T>  what the work returns
         * @param call the handle
         * @return where it stands now, read so that an {@code Effect} follows it
         */
        <T> SlowCallState<T> stateOf(SlowCall<T> call);

        /**
         * @param call the handle
         */
        void cancel(SlowCall<?> call);
    }

    private static volatile Tracker tracker;

    private String key;
    private String id;

    /** For the wire. */
    public SlowCall() {
    }

    /**
     * For the framework. A server gets a handle from {@code SlowCalls.start}, a browser from its
     * service stub or {@link #find(String)}.
     *
     * @param key what the application calls this work
     * @param id  this run of it, or null when only the key is known
     */
    public SlowCall(String key, String id) {
        this.key = key;
        this.id = id;
    }

    /**
     * The slow call this browser or user has under a key, if any.
     *
     * <p>This is how a screen finds its work again after a page reload. It starts nothing. The
     * state is {@link SlowCallStatus#CHECKING} for a moment, and then {@link SlowCallStatus#IDLE}
     * when there is no such work. Browser only.</p>
     *
     * @param <T> what the work returns
     * @param key the key the server started it under
     * @return a handle
     */
    public static <T> SlowCall<T> find(String key) {
        return new SlowCall<>(key, null);
    }

    /** @return what the application calls this work */
    public String key() {
        return key;
    }

    /** @return this run of the work, or null for a handle from {@link #find(String)} */
    public String id() {
        return id;
    }

    /**
     * Where the work stands now. Read it inside an {@code Effect} and the effect runs again on
     * every change, with no timer and no polling. Browser only.
     *
     * <p>The {@code Effect} is what you dispose. There is nothing to dispose on this handle.</p>
     *
     * @return the state; never null
     * @throws IllegalStateException on a server, or before the client has started
     */
    public SlowCallState<T> state() {
        return requireTracker().stateOf(this);
    }

    /**
     * Asks the server to stop the work. The work sees an interrupt, and
     * {@code SlowCallJob.isCanceled()} turns true. The state turns to
     * {@link SlowCallStatus#CANCELED} when the server answers. Does nothing to work that has
     * ended. Browser only.
     *
     * <p>An ordinary remote call on {@code 0x01 RPC_CALL}, so it suspends, and it throws
     * {@link DisconnectedException} while the connection is down.</p>
     *
     * @throws IllegalStateException on a server, or before the client has started
     */
    public void cancel() {
        requireTracker().cancel(this);
    }

    /**
     * Installs the browser half. Called by {@code WasmRmiClient.initialize}.
     *
     * @param browserHalf the tracker, or null to remove it
     */
    public static void installTracker(Tracker browserHalf) {
        tracker = browserHalf;
    }

    private static Tracker requireTracker() {
        Tracker installed = tracker;
        if (installed == null) {
            throw new IllegalStateException("state() and cancel() work in a browser, after "
                    + "Zeroz4jClient.connect. On a server the work is the lambda given to "
                    + "SlowCalls.start. In a test use TestConnection.awaitSlowCall.");
        }
        return installed;
    }

    void assign(String wireKey, String wireId) {
        this.key = wireKey;
        this.id = wireId;
    }

    @Override
    public String toString() {
        return "SlowCall[" + key + "]";
    }
}
