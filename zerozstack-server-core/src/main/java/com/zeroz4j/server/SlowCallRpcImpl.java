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
package com.zeroz4j.server;

import com.zeroz4j.api.SlowCallRpc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Where a browser fetches a slow call's result or asks it to stop.
 *
 * <p>The caller comes from the connection and nothing else. A run that belongs to somebody else
 * gets the same answer as a run that never existed, so asking tells a caller nothing.</p>
 *
 * @since 0.11.0
 */
@ApplicationScoped
public class SlowCallRpcImpl implements SlowCallRpc {

    /** This server, which owns the running work. */
    @Inject
    ServerRuntime runtime;

    /**
     * Fetches the value of finished work.
     *
     * @param id the run
     * @return what the work returned
     * @throws ClientVisibleException with the sentence for lost work, when the caller has no
     *                                finished run under that id
     *
     * <p><b>Under the hood:</b> arrives on {@code 0x01 RPC_CALL}; the value goes back on
     * {@code 0x01 RPC_RESPONSE}, the refusal on {@code 0x0F RPC_ERROR}.</p>
     */
    @Override
    public Object collect(String id) {
        return runtime.slowCalls().collect(id, SlowCallRegistry.Owner.ofCaller());
    }

    /**
     * Asks running work to stop. Does nothing when the caller has no running work under that key.
     *
     * @param key the key the work was started under
     *
     * <p><b>Under the hood:</b> arrives on {@code 0x01 RPC_CALL}; the owner's subscribed
     * connections hear the result on {@code 0x17 SIGNAL_UPD}.</p>
     */
    @Override
    public void cancel(String key) {
        runtime.slowCalls().cancel(key, SlowCallRegistry.Owner.ofCaller());
    }
}
