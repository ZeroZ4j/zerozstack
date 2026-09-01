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

import java.util.ArrayList;
import java.util.List;

/**
 * Where an edit that did not happen is reported, so a screen never keeps showing one that did not.
 *
 * <p>An edit to a {@code @ClientWritable} object is applied on the screen straight away and sent to
 * the server afterwards. Almost always it lands. When it does not, the person is looking at a value
 * the server does not have, and something has to say so. Two things can go wrong, and both arrive
 * here:</p>
 *
 * <ul>
 *   <li><b>The server refused it</b> — the model is not writable by clients, the connection does not
 *       hold the role the model asks for, or the new state fails the model's validation. The server
 *       sends the current state back first, so the screen is already correct by the time the reason
 *       arrives.</li>
 *   <li><b>The browser could not send it</b> — the change could not be put on the wire at all. The
 *       client asks the server to re-send that object, which puts the screen back to the truth, and
 *       reports the failure here.</li>
 * </ul>
 *
 * <pre>{@code
 * LiveMutationRefusals.onRefused((model, reason) -> toast.show("Not saved: " + reason));
 * }</pre>
 *
 * <p>With nothing listening, every refusal is still written to the console as a full sentence
 * saying the change did not happen. It is never silent. Before 0.8.0 a failure to send was one
 * console line nobody read, and the edit was dropped — which is how the entire up direction of
 * LiveSync stayed broken for a whole version.</p>
 */
public final class LiveMutationRefusals {

    /** Told that one live edit did not reach the server. */
    @FunctionalInterface
    public interface Listener {
        /**
         * @param modelClassName the model whose edit did not happen; may be empty if unknown
         * @param reason         a sentence saying why, suitable to show to a person
         */
        void refused(String modelClassName, String reason);
    }

    private static final List<Listener> listeners = new ArrayList<>();

    private LiveMutationRefusals() {}

    /**
     * Registers a listener for edits that did not reach the server.
     *
     * @param listener the listener
     * @return a handle that removes it again
     */
    public static Disposable onRefused(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener must not be null");
        }
        synchronized (LiveMutationRefusals.class) {
            listeners.add(listener);
        }
        return () -> {
            synchronized (LiveMutationRefusals.class) {
                listeners.remove(listener);
            }
        };
    }

    /** Test support only: forgets every registered listener. */
    public static synchronized void resetForTesting() {
        listeners.clear();
    }

    /**
     * Reports a live edit that did not reach the server. Called by the client runtime, not by
     * applications.
     *
     * @param modelClassName the model whose edit did not happen; may be null or empty if unknown
     * @param reason         a sentence saying why
     */
    public static void report(String modelClassName, String reason) {
        String model = modelClassName == null ? "" : modelClassName;
        List<Listener> copy;
        synchronized (LiveMutationRefusals.class) {
            copy = new ArrayList<>(listeners);
        }
        if (copy.isEmpty()) {
            // Loud by default. A change the person can see on the screen but the server never got is
            // the worst state this framework can be in, so it is never merely counted.
            System.err.println("[zeroz4j] A change was NOT saved"
                    + (model.isEmpty() ? "" : " (" + model + ")") + ": " + reason
                    + " Register a LiveMutationRefusals.onRefused(...) listener to tell the person.");
            return;
        }
        for (Listener listener : copy) {
            try {
                listener.refused(model, reason);
            } catch (Exception e) {
                System.err.println("[zeroz4j] A LiveMutationRefusals listener threw: "
                        + e.getMessage());
            }
        }
    }
}
