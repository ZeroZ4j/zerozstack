package com.smoke.server;

import com.smoke.model.Message;
import com.smoke.signals.SmokeSignals;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Publishes a shared signal every second. Broadcasting a {@code @DataModel} over the wire is what
 * failed at runtime when the annotation processor never ran, so this is the check that matters.
 */
public final class Ticker {

    private static final ScheduledExecutorService SCHEDULER =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "smoke-ticker");
            t.setDaemon(true);
            return t;
        });

    private static int count;

    private Ticker() {
    }

    public static void start() {
        SCHEDULER.scheduleAtFixedRate(
            () -> SmokeSignals.TICK.set(new Message("tick-" + (++count))), 1, 1, TimeUnit.SECONDS);
    }
}
