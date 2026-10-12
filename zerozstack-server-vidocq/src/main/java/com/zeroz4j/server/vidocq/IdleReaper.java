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
package com.zeroz4j.server.vidocq;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Closes connections that have been silent longer than {@code zeroz.ws.idleTimeoutMinutes}.
 *
 * <p>Chappe has no per-connection idle timeout, so the adapter keeps one. A connection's limit is
 * whatever the engine set on its session when it opened; a connection with none is never reaped.
 * One daemon thread looks at every open connection once a second.</p>
 */
final class IdleReaper implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(IdleReaper.class.getName());

    private final Set<ChappeSession> sessions = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "zeroz4j-vidocq-idle-reaper");
        thread.setDaemon(true);
        return thread;
    });

    IdleReaper() {
        timer.scheduleWithFixedDelay(() -> sweep(System.currentTimeMillis()), 1, 1, TimeUnit.SECONDS);
    }

    void add(ChappeSession session) {
        sessions.add(session);
    }

    void remove(ChappeSession session) {
        sessions.remove(session);
    }

    int size() {
        return sessions.size();
    }

    /** Closes every connection idle at {@code nowMillis}; separate from the timer so a test can drive it. */
    void sweep(long nowMillis) {
        for (ChappeSession session : sessions) {
            long limit = session.maxIdleMillis();
            if (limit > 0 && nowMillis - session.lastActivityMillis() > limit) {
                LOG.info("[zeroz4j] Closing an idle connection after " + limit / 60_000L
                        + " minute(s) of silence (zeroz.ws.idleTimeoutMinutes).");
                sessions.remove(session);
                session.closeWith(ChappeSession.CLOSE_GOING_AWAY, "Idle timeout.");
            }
        }
    }

    @Override
    public void close() {
        timer.shutdownNow();
        sessions.clear();
    }
}
