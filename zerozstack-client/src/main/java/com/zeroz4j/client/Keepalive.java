package com.zeroz4j.client;

import com.zeroz4j.api.SyncFrameTypes;
import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.GrowableBuffer;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * <b>Keeps an idle WebSocket alive through the proxies in front of it, and notices when one has
 * silently died.</b>
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
 * <h2>Noticing a connection that has died without saying so</h2>
 * A network can stop carrying anything without the browser ever hearing about it: a laptop that
 * left a Wi-Fi network, a proxy that stopped forwarding, a mobile connection that fell through. The
 * socket still reads as open, and the browser may take minutes to find out otherwise. Every call
 * made in that time waits, and a screen waiting on one shows a loading indicator and nothing else.
 *
 * <p>So a ping also expects its answer. If nothing at all arrives from the server within
 * {@value #DEFAULT_LIVENESS_SECONDS} seconds of a ping, the connection is given up on: the socket
 * is closed, every call waiting on it fails at once with a {@code DisconnectedException}, and the
 * ordinary reconnect starts. Any frame counts as an answer, not only the PONG.</p>
 *
 * <p>A connection that is merely quiet is not asked often. But when a call has been waiting
 * {@value #PROBE_AFTER_SECONDS} seconds with nothing at all arriving from the server, a ping is sent
 * straight away rather than at the end of the idle interval. The server answers a ping ahead of
 * everything else it is doing for that connection, including a slow call, so a live connection
 * answers in milliseconds however long the call takes, and only a dead one stays silent. With the
 * defaults, a call on a connection that died silently fails about fifteen seconds after it was
 * made: five seconds before the ping, ten waiting for its answer, and up to a second for the timer.
 * That is well inside the 30-second call deadline, so the failure says "connection" rather than
 * "timed out".</p>
 *
 * <h2>Configuring it</h2>
 * The keepalive is on by default at {@value #DEFAULT_SECONDS} seconds, which is inside both timeouts
 * above with room to spare. {@link #configure(int)} changes it; zero or less turns it off, for a
 * deployment with no proxy that would rather have an idle socket cost nothing at all.
 *
 * <p>The liveness check is configured separately with {@link #configureLiveness(int)}, and stays
 * on when the keepalive is off: it then pings only while a call is waiting. Set it longer on a
 * network known to pause for more than ten seconds at a time; set it to zero to leave dead
 * connections to the browser, which is how every version before this one behaved.</p>
 */
public final class Keepalive {

    /**
     * Seconds of silence before a ping. Comfortably inside nginx's 60 and Cloudflare's 100.
     *
     * <p>Deliberately not derived from either: a heartbeat tuned to one proxy's timeout is one
     * proxy away from being wrong again.
     */
    public static final int DEFAULT_SECONDS = 25;

    /** Seconds a ping may go unanswered before the connection is treated as dead. */
    public static final int DEFAULT_LIVENESS_SECONDS = 10;

    /** Seconds a call may wait with nothing arriving before a ping is sent to ask whether the connection is alive. */
    public static final int PROBE_AFTER_SECONDS = 5;

    /** How often the timer looks. */
    private static final int TICK_MILLIS = 1_000;

    private static int intervalSeconds = DEFAULT_SECONDS;
    private static int livenessSeconds = DEFAULT_LIVENESS_SECONDS;
    private static double lastActivityMillis;
    private static double lastReceivedMillis;
    /** When the unanswered ping was sent, or 0 when no ping is waiting for its answer. */
    private static double pingSentAtMillis;
    /** Timer looks since that ping; see {@link #tickAt(double)} for why this is counted. */
    private static int ticksSincePing;
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

    /**
     * Sets how long a ping may go unanswered before the connection is given up on, or turns the
     * check off.
     *
     * <p>Call before {@code Zeroz4jClient.connect}; changing it later takes effect at the next tick.
     *
     * @param seconds seconds to wait for anything from the server after a ping; zero or less
     *                disables the check
     */
    public static void configureLiveness(int seconds) {
        livenessSeconds = seconds;
    }

    /** Whether the keepalive is on. */
    public static boolean isEnabled() {
        return intervalSeconds > 0;
    }

    /** Whether an unanswered ping closes the connection. */
    public static boolean isLivenessEnabled() {
        return livenessSeconds > 0;
    }

    /**
     * Records that the socket just carried something, in either direction.
     *
     * <p>Called from the client's own send path. It is what makes this a heartbeat rather than a
     * poll: a busy connection never pings.
     */
    public static void noteActivity() {
        lastActivityMillis = now();
    }

    /**
     * Records that something arrived from the server.
     *
     * <p>Called from the client's receive path for every frame. Anything arriving proves the
     * connection is alive, so it also settles a ping that was waiting for an answer.
     */
    static void noteReceived() {
        double now = now();
        lastActivityMillis = now;
        lastReceivedMillis = now;
        pingSentAtMillis = 0;
        ticksSincePing = 0;
    }

    /**
     * Starts the timer. Idempotent - the framework calls it once, when the transport is installed.
     */
    static void start() {
        if (started || (!isEnabled() && !isLivenessEnabled())) {
            return;
        }
        started = true;
        noteReceived();
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

    private static void tick() {
        try {
            tickAt(now());
        } catch (Throwable problem) {
            System.err.println("[zeroz4j] A keepalive tick failed: " + problem);
            problem.printStackTrace();
        }
    }

    /**
     * One look at the clock.
     *
     * <p>Everything here is deliberately cheap and total: a keepalive that threw would take the
     * interval with it and the connection would start dying again, silently, on the timeout that
     * this exists to prevent.
     *
     * <p>A connection is given up on only when its ping has gone unanswered for the whole liveness
     * period <em>and</em> at least two looks have happened since the ping. A tab the browser froze
     * in the background, or a laptop waking up, runs its timers before it delivers the frames that
     * arrived while it slept; judging on the first look after waking would close a healthy
     * connection whose answer is sitting in the queue right behind the timer.
     *
     * @param nowMillis the clock, passed in so a test can move it
     */
    static void tickAt(double nowMillis) {
        WasmWebSocketChannel channel = WasmRmiClient.networkChannel;
        if (channel == null || !channel.isOpen()) {
            // Nothing to keep alive. The reconnect path has its own timers, and pinging a socket
            // that is down would only produce noise in the console. A ping waiting for an answer
            // belonged to the socket that is gone.
            pingSentAtMillis = 0;
            ticksSincePing = 0;
            return;
        }

        if (pingSentAtMillis > 0) {
            ticksSincePing++;
            if (isLivenessEnabled() && ticksSincePing >= 2
                    && nowMillis - pingSentAtMillis >= livenessSeconds * 1000.0) {
                pingSentAtMillis = 0;
                ticksSincePing = 0;
                channel.dropUnresponsive("nothing arrived from the server for " + livenessSeconds
                        + " seconds after a keepalive ping");
            }
            return;
        }

        boolean idle = isEnabled() && nowMillis - lastActivityMillis >= intervalSeconds * 1000.0;
        if (idle || callWaitingInSilence(nowMillis)) {
            sendPing(channel, nowMillis);
        }
    }

    /**
     * Whether a call has been waiting long enough, with nothing arriving from the server, to be
     * worth asking if the connection is still there.
     */
    private static boolean callWaitingInSilence(double nowMillis) {
        if (!isLivenessEnabled()) {
            return false;
        }
        long oldest = WasmRmiClient.oldestPendingSince();
        if (oldest < 0) {
            return false;
        }
        double silentSince = Math.max(oldest, lastReceivedMillis);
        return nowMillis - silentSince >= PROBE_AFTER_SECONDS * 1000.0;
    }

    private static void sendPing(WasmWebSocketChannel channel, double nowMillis) {
        try {
            GrowableBuffer buffer = new GrowableBuffer();
            buffer.putInt(0); // fire-and-forget: the answer is any frame at all
            BinarySerializer.writeString(buffer, SyncFrameTypes.KEEPALIVE_SERVICE);
            BinarySerializer.writeString(buffer, "ping");
            buffer.putInt(0); // no arguments
            channel.sendRawBytes(buffer.toByteArray());
            lastActivityMillis = nowMillis;
            if (isLivenessEnabled()) {
                pingSentAtMillis = nowMillis;
                ticksSincePing = 0;
            }
        } catch (Exception e) {
            // Deliberately quiet: a failed ping on a socket that is going down anyway is not news,
            // and the reconnect that follows is logged by the channel.
        }
    }

    /** Test support only: puts every clock and setting back to how a fresh page starts. */
    static void resetForTesting(double nowMillis) {
        intervalSeconds = DEFAULT_SECONDS;
        livenessSeconds = DEFAULT_LIVENESS_SECONDS;
        lastActivityMillis = nowMillis;
        lastReceivedMillis = nowMillis;
        pingSentAtMillis = 0;
        ticksSincePing = 0;
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
     * nothing in some engines and everything in others; what it catches is written to the console
     * rather than swallowed, so a tick that fails is at least visible. The request timer in
     * {@link WasmRmiClient} uses it too.
     */
    @JSBody(params = {"millis", "tick"}, script =
            "setInterval(function () {"
            + "  try { tick(); } catch (problem) { console.error('[zeroz4j] A connection timer tick failed', problem); }"
            + "}, millis);")
    static native void every(int millis, Tick tick);

    /**
     * The clock, from {@code System} rather than {@code Date.now()}.
     *
     * <p>TeaVM implements this, and so does the JVM - which matters more than it looks: the
     * framework's own client tests run on the JVM, and {@link #noteReceived()} is called from the
     * receive path on every inbound frame. A browser-only clock there made every existing test that
     * routes a frame fail with {@code UnsatisfiedLinkError}. Nothing on a hot path may be
     * browser-only.
     */
    private static double now() {
        return System.currentTimeMillis();
    }
}
