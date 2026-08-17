package com.zeroz4j.client;

import com.zeroz4j.api.SyncFrameTypes;
import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.GrowableBuffer;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * <b>Keeps an idle WebSocket alive through the proxies in front of it.</b>
 *
 * <h2>Why the framework has to do this</h2>
 * A WebSocket that carries nothing is closed by whichever proxy in the path has the shortest idle
 * timeout. nginx defaults to <b>60 seconds</b> ({@code proxy_read_timeout}); Cloudflare cuts at
 * <b>100</b> and is not the application's to configure. Measured in a real deployment on
 * 2026-08-17: every socket opened, authenticated, and died at exactly 60 seconds, over and over,
 * with each reconnect re-sending a growing pile of live objects.
 *
 * <p><b>An application cannot fix this itself.</b> Browsers do not expose WebSocket ping frames to
 * page script, so the only thing an application can do is invent a service method whose sole
 * purpose is to make a byte travel - and then declare it on every service interface it owns, where
 * it is indistinguishable from a real operation. One application did exactly that before this class
 * existed. A transport that needs every one of its users to do that is incomplete.
 *
 * <h2>What it sends</h2>
 * A five-byte fire-and-forget frame to {@link SyncFrameTypes#KEEPALIVE_SERVICE}, answered by one
 * empty {@link SyncFrameTypes#PONG}. The answer matters as much as the ping: a proxy times each
 * DIRECTION separately, so a ping the server merely swallowed would keep only one of the two timers
 * alive.
 *
 * <h2>Only when the connection has gone quiet</h2>
 * The timer fires often; a ping is sent only when nothing has crossed the socket for a whole
 * interval. An application in use therefore sends none at all, which is the difference between a
 * heartbeat and a poll. {@link #noteActivity()} is called from both the send and the receive paths,
 * so any real traffic - a call, a signal update, a sync frame - postpones the next ping.
 *
 * <h2>Configuring it</h2>
 * On by default at {@value #DEFAULT_SECONDS} seconds, which is inside both timeouts above with room
 * to spare. {@link #configure(int)} changes it; zero or less turns it off, for a deployment with no
 * proxy that would rather have an idle socket cost nothing at all.
 */
public final class Keepalive {

    /**
     * Seconds of silence before a ping. Comfortably inside nginx's 60 and Cloudflare's 100.
     *
     * <p>Deliberately not derived from either: a heartbeat tuned to one proxy's timeout is one
     * proxy away from being wrong again.
     */
    public static final int DEFAULT_SECONDS = 25;

    /** How often the timer looks. A third of the interval, so a ping is never much late. */
    private static final int TICK_MILLIS = 5_000;

    private static int intervalSeconds = DEFAULT_SECONDS;
    private static double lastActivityMillis;
    private static boolean started;

    private Keepalive() {
    }

    /**
     * Sets the idle interval, or turns the keepalive off.
     *
     * <p>Call before {@code Zeroz4jClient.connect}; changing it later takes effect at the next tick.
     *
     * @param seconds seconds of silence before a ping; zero or less disables it
     */
    public static void configure(int seconds) {
        intervalSeconds = seconds;
    }

    /** Whether the keepalive is on. */
    public static boolean isEnabled() {
        return intervalSeconds > 0;
    }

    /**
     * Records that the socket just carried something, in either direction.
     *
     * <p>Called from the client's own send and receive paths. It is what makes this a heartbeat
     * rather than a poll: a busy connection never pings.
     */
    public static void noteActivity() {
        lastActivityMillis = now();
    }

    /**
     * Starts the timer. Idempotent - the framework calls it once, when the transport is installed.
     */
    static void start() {
        if (started || !isEnabled()) {
            return;
        }
        started = true;
        noteActivity();
        try {
            every(TICK_MILLIS, Keepalive::tick);
        } catch (Throwable outsideABrowser) {
            // There is no setInterval on the JVM, and the framework's own tests install the client
            // there. Catching rather than guarding, because "am I in a browser?" is a question with
            // no honest answer in TeaVM - and a keepalive that refused to install would be a worse
            // failure than one that quietly does not tick in a unit test.
            started = false;
        }
    }

    /**
     * One look at the clock.
     *
     * <p>Everything here is deliberately cheap and total: a keepalive that threw would take the
     * interval with it and the connection would start dying again, silently, on the timeout that
     * this exists to prevent.
     */
    private static void tick() {
        if (!isEnabled()) {
            return;
        }
        WasmWebSocketChannel channel = WasmRmiClient.networkChannel;
        if (channel == null || !channel.isOpen()) {
            // Nothing to keep alive. The reconnect path has its own timers, and pinging a socket
            // that is down would only produce noise in the console.
            return;
        }
        if (now() - lastActivityMillis < intervalSeconds * 1000.0) {
            return;
        }
        try {
            GrowableBuffer buffer = new GrowableBuffer();
            buffer.putInt(0); // fire-and-forget: nothing waits for the answer
            BinarySerializer.writeString(buffer, SyncFrameTypes.KEEPALIVE_SERVICE);
            BinarySerializer.writeString(buffer, "ping");
            buffer.putInt(0); // no arguments
            channel.sendRawBytes(buffer.toByteArray());
            noteActivity();
        } catch (Exception e) {
            // Deliberately quiet: a failed ping on a socket that is going down anyway is not news,
            // and the reconnect that follows is logged by the channel.
        }
    }

    /** What a tick does. A functor so TeaVM can hand it to {@code setInterval}. */
    @JSFunctor
    interface Tick extends JSObject {
        void run();
    }

    /**
     * Ticks for the life of the page.
     *
     * <p>Never cleared: it lives as long as the client does, and the one thing worse than a
     * redundant ping is a keepalive that stopped for a reason nobody noticed. The try/catch is
     * inside the browser because an exception escaping a {@code setInterval} callback cancels
     * nothing in some engines and everything in others.
     */
    @JSBody(params = {"millis", "tick"}, script =
            "setInterval(function () { try { tick(); } catch (e) { } }, millis);")
    private static native void every(int millis, Tick tick);

    /**
     * The clock, from {@code System} rather than {@code Date.now()}.
     *
     * <p>TeaVM implements this, and so does the JVM - which matters more than it looks: the
     * framework's own client tests run on the JVM, and {@link #noteActivity()} is called from the
     * receive path on every inbound frame. A browser-only clock there made every existing test that
     * routes a frame fail with {@code UnsatisfiedLinkError}. Nothing on a hot path may be
     * browser-only.
     */
    private static double now() {
        return System.currentTimeMillis();
    }
}
