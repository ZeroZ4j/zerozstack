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
package com.zeroz4j.signals;

/**
 * SPI connecting shared signals to a tier's runtime. The signal core knows nothing about
 * the network; the installed transport defines what "shared" means in this runtime:
 * the server transport broadcasts on set and serves retained values to subscribers, the
 * Wasm client transport subscribes and applies remote updates to local mirrors. With no
 * transport installed, shared signals behave as local signals.
 *
 * <p>Framework-internal — applications never implement or call this.</p>
 */
public interface SignalTransport {

    /**
     * Invoked when a shared signal is declared (or replayed after transport installation).
     *
     * @param signal the shared signal
     */
    void onSharedSignalCreated(SharedValueSignal<?> signal);

    /**
     * Decides whether a local {@code set()} on the given shared signal is permitted in
     * this runtime. The client transport returns false while shared signals are
     * server-authoritative.
     *
     * @param signal the shared signal being set
     * @return true if the set may proceed
     */
    boolean canSet(SharedValueSignal<?> signal);

    /**
     * Invoked after a permitted {@code set()} actually changed the value. The server
     * transport broadcasts the new value to connected sessions here.
     *
     * @param signal   the shared signal
     * @param newValue the value just assigned
     */
    void afterSet(SharedValueSignal<?> signal, Object newValue);
}
