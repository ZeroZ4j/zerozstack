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

import com.zeroz4j.api.DisconnectedException;
import com.zeroz4j.api.RmiClientExecutor;
import com.zeroz4j.api.SlowCall;
import com.zeroz4j.api.SlowCallBoard;
import com.zeroz4j.api.SlowCallState;
import com.zeroz4j.api.SlowCallStatus;
import com.zeroz4j.api.i18n.FrameworkText;
import com.zeroz4j.api.i18n.ReadsMessagesOnce;
import com.zeroz4j.signals.Signals;
import com.zeroz4j.signals.ValueSignal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * The browser half of a slow call.
 *
 * <p>One board comes down from the server, on the ordinary shared-signal frames, and this turns it
 * into one state per key a screen has asked about. It subscribes the first time a screen touches a
 * slow call, so an application that has none sends and receives nothing.</p>
 *
 * <p>Reconnecting needs no code here. The signal transport re-subscribes after a drop, the server
 * answers with the board, and everything below runs again on that board. That is how work finished
 * during the drop is collected, and how a server restart is noticed: work this browser saw running
 * is simply not on the new board.</p>
 *
 * <p>Runs on the browser's one scheduler, so it takes no locks.</p>
 */
final class ClientSlowCalls implements SlowCall.Tracker {

    private static final String RPC = "com.zeroz4j.api.SlowCallRpc";

    /** One key a screen has asked about. */
    private static final class Tracked {
        private final ValueSignal<SlowCallState<Object>> state;
        /** The same value, readable without subscribing whoever is running to it. */
        private SlowCallState<Object> current;
        /** The run whose value is being fetched, or is in {@link #current}. */
        private String collecting;

        Tracked(SlowCallState<Object> initial) {
            this.state = new ValueSignal<>(initial);
            this.current = initial;
        }

        void set(SlowCallState<Object> next) {
            current = next;
            state.set(next);
        }
    }

    private final Map<String, Tracked> tracked = new HashMap<>();
    private ValueSignal<SlowCallBoard> board;
    private SlowCallBoard latest;

    @Override
    @SuppressWarnings("unchecked")
    public <T> SlowCallState<T> stateOf(SlowCall<T> call) {
        Tracked one = tracked.get(call.key());
        if (one == null) {
            // A handle with a run came from a service call, so the work exists. A handle without one
            // came from find, and only the server knows.
            one = new Tracked(call.id() != null
                    ? SlowCallState.running(-1, null) : SlowCallState.checking());
            tracked.put(call.key(), one);
            subscribe();
            if (latest != null) {
                apply(call.key(), one, latest);
            }
        }
        return (SlowCallState<T>) one.state.get();
    }

    @Override
    public void cancel(SlowCall<?> call) {
        RmiClientExecutor.executeCall(RPC, "cancel", new Object[] {call.key()});
    }

    private void subscribe() {
        if (board == null) {
            board = Signals.shared(SlowCallBoard.SIGNAL_NAME, null);
            board.addListener(this::boardArrived);
        }
    }

    private void boardArrived(SlowCallBoard arrived) {
        if (arrived == null) {
            return;
        }
        latest = arrived;
        // A copy: applying a state runs effects, and an effect may start following another key.
        for (Map.Entry<String, Tracked> one : new ArrayList<>(tracked.entrySet())) {
            apply(one.getKey(), one.getValue(), arrived);
        }
    }

    @ReadsMessagesOnce("A state holds one sentence, like the failure sentence the server rendered.")
    private void apply(String key, Tracked one, SlowCallBoard from) {
        SlowCallBoard.Entry entry = from.find(key);
        if (entry == null) {
            if (one.current.isRunning()) {
                one.collecting = null;
                one.set(SlowCallState.failed(FrameworkText.slowCallLost().text()));
            } else if (one.current.isChecking()) {
                one.set(SlowCallState.idle());
            }
            return;
        }
        SlowCallStatus status = entry.status();
        if (status == SlowCallStatus.RUNNING) {
            one.collecting = null;
            one.set(SlowCallState.running(entry.percent(), entry.progressText()));
        } else if (status == SlowCallStatus.FAILED) {
            one.collecting = null;
            one.set(SlowCallState.failed(entry.message()));
        } else if (status == SlowCallStatus.CANCELED) {
            one.collecting = null;
            one.set(SlowCallState.canceled());
        } else if (!entry.id().equals(one.collecting)) {
            one.collecting = entry.id();
            one.set(SlowCallState.running(100, entry.progressText()));
            collect(key, one, entry.id());
        }
    }

    /** On a thread of its own, because a board arrives on a stack that cannot suspend. */
    private void collect(String key, Tracked one, String id) {
        new Thread(() -> {
            try {
                Object value = RmiClientExecutor.executeCall(RPC, "collect", new Object[] {id});
                onUiThread(() -> {
                    if (id.equals(one.collecting)) {
                        one.set(SlowCallState.finished(value));
                    }
                });
            } catch (DisconnectedException dropped) {
                // Forgotten, so the board that answers the re-subscription fetches it again.
                onUiThread(() -> {
                    if (id.equals(one.collecting)) {
                        one.collecting = null;
                    }
                });
            } catch (RuntimeException failed) {
                onUiThread(() -> {
                    if (id.equals(one.collecting)) {
                        one.set(SlowCallState.failed(failed.getMessage()));
                    }
                });
            }
        }).start();
    }

    private static void onUiThread(Runnable work) {
        WasmRmiClient.PlatformScheduler scheduler = WasmRmiClient.getPlatformScheduler();
        if (scheduler != null) {
            scheduler.runLater(work);
        } else {
            work.run();
        }
    }
}
