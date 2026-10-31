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

import com.zeroz4j.api.Disposable;
import com.zeroz4j.ui.Browser;
import java.util.function.Consumer;
import org.teavm.jso.browser.Window;

/**
 * Runs work that calls the server from places that cannot: a timer, a callback from the browser,
 * an {@code Effect}.
 *
 * <p>A remote call suspends, and a suspension cannot start on a stack that began in native
 * JavaScript. A {@code Window.setTimeout} callback, a promise, a framework callback such as
 * {@code RmiSecurityContext.onResolved} and the body of an {@code Effect} are all such stacks, and
 * a call from any of them fails with "Suspension point reached from non-threading context". DOM
 * listeners added with {@code addDomEventListener} are already safe. Everything else goes through
 * here.</p>
 *
 * <pre>{@code
 * // once, from a callback
 * Background.run(() -> orders.set(service.list()));
 *
 * // every 5 seconds until disposed; the next run starts 5 seconds after the last one ends
 * Disposable poll = Background.every(5000, () -> health.set(service.health()));
 * poll.dispose();
 * }</pre>
 */
public final class Background {

    private Background() {
    }

    /**
     * Runs {@code task} on a green thread, where a remote call may suspend.
     *
     * @param task the work; an exception it throws is written to the browser console
     */
    public static void run(Runnable task) {
        new Thread(() -> {
            try {
                task.run();
            } catch (RuntimeException failure) {
                Browser.warn("[zeroz4j] Background task failed: " + failure);
            }
        }).start();
    }

    /**
     * Runs {@code task} once after a delay.
     *
     * @param millis the delay in milliseconds
     * @param task   the work, run on a green thread
     * @return a handle; disposing it before the task starts cancels it
     */
    public static Disposable after(long millis, Runnable task) {
        boolean[] cancelled = new boolean[1];
        int timer = Window.setTimeout(() -> {
            if (!cancelled[0]) {
                run(task);
            }
        }, (int) millis);
        return () -> {
            cancelled[0] = true;
            Window.clearTimeout(timer);
        };
    }

    /**
     * Repeats {@code task}, starting now.
     *
     * <p>The next run starts {@code millis} after the previous one <em>ends</em>, so a slow server
     * is never hit by overlapping calls. A failure does not stop the polling; it is written to the
     * browser console once, and again only after a run has succeeded in between.</p>
     *
     * @param millis the pause between the end of one run and the start of the next
     * @param task   the work, run on a green thread
     * @return a handle; disposing it stops the polling, and a run already under way finishes
     */
    public static Disposable every(long millis, Runnable task) {
        return every(millis, task, null);
    }

    /**
     * Repeats {@code task}, starting now, and tells {@code onError} about each failure.
     *
     * @param millis  the pause between the end of one run and the start of the next
     * @param task    the work, run on a green thread
     * @param onError called after each failed run; null writes the first failure of a streak to the
     *                browser console
     * @return a handle; disposing it stops the polling
     * @see #every(long, Runnable)
     */
    public static Disposable every(long millis, Runnable task, Consumer<RuntimeException> onError) {
        Poller poller = new Poller(millis, task, onError);
        poller.tick();
        return poller::stop;
    }

    /** One polling loop. */
    private static final class Poller {
        private final long millis;
        private final Runnable task;
        private final Consumer<RuntimeException> onError;
        private boolean stopped;
        private boolean failing;
        private int timer = -1;

        Poller(long millis, Runnable task, Consumer<RuntimeException> onError) {
            this.millis = millis;
            this.task = task;
            this.onError = onError;
        }

        void tick() {
            if (stopped) {
                return;
            }
            new Thread(() -> {
                try {
                    task.run();
                    failing = false;
                } catch (RuntimeException failure) {
                    if (onError != null) {
                        onError.accept(failure);
                    } else if (!failing) {
                        Browser.warn("[zeroz4j] Background polling failed: " + failure);
                    }
                    failing = true;
                } finally {
                    if (!stopped) {
                        timer = Window.setTimeout(this::tick, (int) millis);
                    }
                }
            }).start();
        }

        void stop() {
            stopped = true;
            if (timer >= 0) {
                Window.clearTimeout(timer);
                timer = -1;
            }
        }
    }
}
