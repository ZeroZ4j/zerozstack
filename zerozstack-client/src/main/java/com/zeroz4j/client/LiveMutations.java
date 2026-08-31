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

import com.zeroz4j.api.BinaryRegistry;
import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.LiveMutationRefusals;
import com.zeroz4j.api.LiveMutationTracker;
import com.zeroz4j.api.SyncFrameTypes;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client half of two-way LiveSync: forwards field changes on {@code @ClientWritable}
 * live instances to the server.
 *
 * <p>Deserialization instantiates the APT-generated {@code <Model>_Live} subclasses,
 * whose setters report through {@link LiveMutationTracker}. Changes travel as whole-object
 * {@code zeroz4j.livesync#mutate} frames. The server authorizes, validates, applies, and
 * re-broadcasts — or answers with a corrective sync that reverts this client's instance in
 * place.</p>
 *
 * <h2>One message per pause, not one per keystroke (0.8.0+)</h2>
 *
 * <p>An edit is not sent the instant a setter returns. It waits for a short pause in the typing —
 * {@value #DEFAULT_QUIET_MILLIS} ms by default — and everything changed during that burst goes in
 * one message. Several edits to one object were always collapsed into one frame by the pending
 * set; what was missing was the waiting, so every character produced its own frame.</p>
 *
 * <p>A plain pause-and-send has a hole: somebody typing steadily never pauses, so nothing is ever
 * sent, and a closed tab or a dropped connection takes the whole paragraph with it. So there is
 * also a ceiling — {@value #DEFAULT_MAX_WAIT_MILLIS} ms by default — measured from the first
 * change of the burst. Whichever comes first wins, which means a person typing without stopping
 * still has their work sent about once a second.</p>
 *
 * <p>Both numbers are set with {@link #configure(int, int)}. A quiet period of zero turns the
 * waiting off and restores the old behavior of sending on every setter call.</p>
 *
 * <h2>There is no flush when the page is left, on purpose</h2>
 *
 * <p>Somebody who closes the tab or follows a link in the middle of a burst loses whatever was
 * still waiting - up to {@value #DEFAULT_MAX_WAIT_MILLIS} ms of typing, which is what the ceiling
 * is for. A handler on {@code beforeunload}, {@code pagehide} and {@code visibilitychange} was
 * built and measured on Chrome: the handler runs, but whether the browser gets the bytes out of
 * the WebSocket before it takes the page apart is its decision, and it went both ways on the same
 * machine on the same day, for both a closed tab and a followed link. The one thing browsers do
 * guarantee at unload, {@code navigator.sendBeacon}, speaks HTTP and cannot write to a WebSocket.
 * Something that works half the time here is worse than nothing, because it invites an application
 * to rely on it, so it was taken out again.</p>
 *
 * <h2>Nothing may overtake a waiting edit</h2>
 *
 * <p>While an edit is waiting, anything else this client sends would arrive first, and the server
 * would act on a value the person has already replaced. So every outgoing call flushes first:
 * {@link #flushBeforeOutboundCall()} is called by {@link WasmRmiClient#executeCall} and by the
 * shared-signal write path, which between them cover RMI service calls, {@code LiveMutex} locks
 * and signal writes. The waiting edits are written to the socket before the call is, so the server
 * reads them in that order.</p>
 */
public final class LiveMutations {

    /**
     * How long the typing has to stop before the edits are sent, in milliseconds.
     *
     * <p>150 ms sits above the gap between two keys of somebody typing quickly (roughly 80-120 ms)
     * so a burst is not chopped up, and well below the quarter second at which a person starts to
     * feel a delay. A pause to think is longer than this, so a finished sentence goes out at once.
     */
    public static final int DEFAULT_QUIET_MILLIS = 150;

    /**
     * The longest an edit may wait, in milliseconds, counted from the first change of the burst.
     *
     * <p>One second is what somebody typing without ever stopping stands to lose if the tab is
     * closed or the connection drops - around ten characters. It also caps a continuous typist at
     * one message a second instead of ten, which is the whole point of the change. Shorter buys
     * less exposure at the price of more messages; longer is only worth it for a field where
     * losing a few seconds of typing does not matter.
     */
    public static final int DEFAULT_MAX_WAIT_MILLIS = 1_000;

    /**
     * The objects edited since the last send, in the order they were first edited, one entry per
     * object however many of its setters were called.
     *
     * <p>Keyed by <b>identity</b>, deliberately. It used to be a plain set, which asks each model
     * whether it is equal to another - and that was harmless only because an edit left immediately,
     * so the collection never held two things at once. Now that edits wait, two consequences would
     * both be wrong: a model whose {@code equals} looks at its own fields changes as it is typed
     * into, so it would no longer find itself and every character would queue a second entry; and
     * two genuinely different objects that happen to be equal would collapse into one, sending one
     * person's edit and throwing the other away. Which object was edited is a question about
     * identity, and nothing else.</p>
     */
    private static final Map<Identity, Object> pendingMutations = new LinkedHashMap<>();

    /** One edited object, compared by {@code ==} rather than by {@code equals}. */
    private static final class Identity {
        private final Object target;
        private final int hash;

        Identity(Object target) {
            this.target = target;
            this.hash = System.identityHashCode(target);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Identity && ((Identity) other).target == target;
        }
    }

    /** True while a timer is booked to look at the pending set. */
    private static boolean timerArmed = false;

    /** When the newest change in the current burst arrived. */
    private static long lastChangeMillis;

    /** When the oldest still-unsent change arrived. The ceiling is measured from here. */
    private static long firstChangeMillis;

    private static int quietMillis = DEFAULT_QUIET_MILLIS;
    private static int maxWaitMillis = DEFAULT_MAX_WAIT_MILLIS;

    /** Books a callback for later. Returns false where the platform has no timers. */
    interface Delayer {
        boolean after(int millis, Runnable task);
    }

    /** The clock. Its own interface so a test can move time without waiting for it. */
    interface Clock {
        long nowMillis();
    }

    private static Delayer delayer = LiveMutations::browserDelay;
    private static Clock clock = LiveMutations::systemMillis;

    /**
     * Cleared once the platform has been found to have no timers, so the next thousand keystrokes
     * do not each throw and catch. The framework's own tests run this client on a plain JVM, where
     * there is no {@code setTimeout}; there the edits are sent immediately, exactly as before.
     */
    private static boolean timersAvailable = true;

    private LiveMutations() {}

    /**
     * Sets how long an edit waits before it is sent.
     *
     * <p>Call before {@code Zeroz4jClient.connect}. Both numbers are in milliseconds.</p>
     *
     * @param quietMillis   milliseconds of no further change before the edits are sent; zero or
     *                      less sends every setter call immediately, with no waiting at all
     * @param maxWaitMillis the longest an edit may wait even while the changes keep coming,
     *                      counted from the first unsent change; raised to {@code quietMillis} if
     *                      it is smaller, since a ceiling below the pause would leave no room for
     *                      a pause to be noticed
     */
    public static synchronized void configure(int quietMillis, int maxWaitMillis) {
        LiveMutations.quietMillis = quietMillis;
        LiveMutations.maxWaitMillis = Math.max(quietMillis, maxWaitMillis);
    }

    /** @return the milliseconds of quiet an edit waits for before it is sent */
    public static synchronized int quietMillis() {
        return quietMillis;
    }

    /** @return the longest an edit may wait, in milliseconds, while changes keep arriving */
    public static synchronized int maxWaitMillis() {
        return maxWaitMillis;
    }

    /** Test support only: drops retained mutations and puts the timings back to their defaults. */
    static synchronized void resetForTesting() {
        pendingMutations.clear();
        timerArmed = false;
        lastChangeMillis = 0;
        firstChangeMillis = 0;
        quietMillis = DEFAULT_QUIET_MILLIS;
        maxWaitMillis = DEFAULT_MAX_WAIT_MILLIS;
        delayer = LiveMutations::browserDelay;
        clock = LiveMutations::systemMillis;
        timersAvailable = true;
    }

    /** Test support only: drives the waiting from a fake clock and a fake timer. */
    static synchronized void useForTesting(Clock testClock, Delayer testDelayer) {
        clock = testClock;
        delayer = testDelayer;
        timersAvailable = true;
    }

    /**
     * Enables live instantiation and installs the mutation listener.
     * Called from {@link WasmRmiClient#initialize}.
     */
    static void install() {
        BinaryRegistry.setPreferLiveInstances(true);
        LiveMutationTracker.install(LiveMutations::onChanged);
    }

    private static void onChanged(Object liveObject) {
        boolean sendNow;
        synchronized (LiveMutations.class) {
            pendingMutations.put(new Identity(liveObject), liveObject);
            long now = clock.nowMillis();
            lastChangeMillis = now;
            if (timerArmed) {
                // A timer is already booked. It re-books itself for as long as the typing keeps
                // the quiet period out of reach, and stops doing so at the ceiling.
                return;
            }
            firstChangeMillis = now;
            // Either the waiting is switched off, or there is no timer on this platform. Both mean
            // the pre-0.8.0 behavior: send it now.
            sendNow = quietMillis <= 0 || !arm(quietMillis);
        }
        if (sendNow) {
            flush();
        }
    }

    /**
     * Decides whether the burst is over, and either sends it or books another look.
     *
     * <p>Runs from a browser timer, so on a stack that began in native JavaScript. Nothing it calls
     * may suspend a green thread; putting bytes on the socket does not.</p>
     */
    private static void tick() {
        boolean sendNow;
        synchronized (LiveMutations.class) {
            timerArmed = false;
            if (pendingMutations.isEmpty()) {
                return;
            }
            long now = clock.nowMillis();
            long sinceLastChange = now - lastChangeMillis;
            long sinceFirstChange = now - firstChangeMillis;
            sendNow = sinceLastChange >= quietMillis || sinceFirstChange >= maxWaitMillis;
            if (!sendNow) {
                long untilQuiet = quietMillis - sinceLastChange;
                long untilCeiling = maxWaitMillis - sinceFirstChange;
                int again = (int) Math.max(1L, Math.min(untilQuiet, untilCeiling));
                // A timer that cannot be booked must not leave the edit sitting there for ever.
                sendNow = !arm(again);
            }
        }
        if (sendNow) {
            flush();
        }
    }

    /** Books one look at the pending set. Called while holding the class lock. */
    private static boolean arm(int delayMillis) {
        if (!timersAvailable) {
            return false;
        }
        try {
            if (!delayer.after(delayMillis, LiveMutations::tick)) {
                timersAvailable = false;
                return false;
            }
            timerArmed = true;
            return true;
        } catch (Throwable outsideABrowser) {
            timersAvailable = false;
            return false;
        }
    }

    private static void flush() {
        Object[] toSend;
        synchronized (LiveMutations.class) {
            if (WasmRmiClient.networkChannel != null && !WasmRmiClient.networkChannel.isOpen()) {
                // The connection is down. Keep the pending set instead of sending into a dead
                // socket, where the edits would be silently lost while the user's screen shows
                // them applied — the worst failure this class can produce. flushPending() sends
                // them the moment the connection is restored.
                return;
            }
            toSend = pendingMutations.values().toArray();
            pendingMutations.clear();
        }
        for (Object liveObject : toSend) {
            sendMutation(liveObject);
        }
    }

    /**
     * Sends every mutation retained while the connection was down. Called on reconnect, before
     * the re-sync request: the edits reach the server first, so the object state the re-sync
     * answers with already includes them (and any concurrent change is settled by the server's
     * usual last-write-wins broadcast).
     */
    static void flushPending() {
        flush();
    }

    /**
     * Sends any waiting edit before this client sends anything else.
     *
     * <p>The reason there is a method for this: a person types into a field and immediately presses
     * a button. The typing is waiting out its quiet period; the button's call would go out first
     * and the server would decide on the old value. Everything the person can see says the edit was
     * ignored. Flushing here puts the edits on the socket ahead of the call, so the server reads
     * them in the order they happened.</p>
     *
     * <p>Cheap when there is nothing waiting, which is the common case: it takes the lock, finds an
     * empty set and returns.</p>
     */
    static void flushBeforeOutboundCall() {
        synchronized (LiveMutations.class) {
            if (pendingMutations.isEmpty()) {
                return;
            }
        }
        flush();
    }

    /**
     * Puts one object's current state on the wire, or — if that cannot be done — puts the screen
     * back to the truth and says so.
     *
     * <p>A failure here means the person has typed something the server will never have. Dropping it
     * with a console line is what let the whole up direction of LiveSync stay broken for a version,
     * so it is handled the way a server refusal is: the object is asked for again, which overwrites
     * the optimistic change in place, and the reason goes to
     * {@link LiveMutationRefusals} for the application to show.</p>
     *
     * @param liveObject the edited live instance
     */
    private static void sendMutation(Object liveObject) {
        if (WasmRmiClient.networkChannel == null) {
            return;
        }
        try {
            GrowableBuffer buffer = new GrowableBuffer();
            buffer.putInt(0); // fire-and-forget
            BinarySerializer.writeString(buffer, SyncFrameTypes.LIVESYNC_SERVICE);
            BinarySerializer.writeString(buffer, "mutate");
            buffer.putInt(1);
            BinarySerializer.writeValue(buffer, liveObject, WasmRmiClient.MAPPER);
            WasmRmiClient.networkChannel.sendRawBytes(buffer.toByteArray());
        } catch (Exception e) {
            String model = BinaryRegistry.wireNameOf(liveObject.getClass().getName());
            boolean reverted = WasmRmiClient.requestResyncOf(liveObject);
            LiveMutationRefusals.report(model, "This change could not be sent to the server ("
                    + e.getMessage() + ")."
                    + (reverted
                        ? " The value shown is being put back to what the server has."
                        : " The screen still shows it, and the server does not have it."));
        }
    }

    /** What a timer callback does. A functor so TeaVM can hand it to {@code setTimeout}. */
    @JSFunctor
    interface Callback extends JSObject {
        void run();
    }

    /** The default {@link Delayer}: a browser timer. Throws where there is no browser. */
    private static boolean browserDelay(int millis, Runnable task) {
        setTimeout(millis, task::run);
        return true;
    }

    /**
     * The clock, from {@code System} rather than {@code Date.now()}: TeaVM implements this and so
     * does the JVM, and the framework's own client tests run on the JVM.
     */
    private static long systemMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Books one callback. The try/catch is inside the browser because an exception escaping a
     * {@code setTimeout} callback cancels nothing in some engines and everything in others.
     */
    @JSBody(params = {"millis", "callback"}, script =
            "setTimeout(function () { try { callback(); } catch (e) { } }, millis);")
    private static native void setTimeout(int millis, Callback callback);
}
