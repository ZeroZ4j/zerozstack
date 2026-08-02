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
