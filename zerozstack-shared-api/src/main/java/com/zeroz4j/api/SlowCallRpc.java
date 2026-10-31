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

/**
 * Internal service behind {@link SlowCall}.
 *
 * <p>Both methods answer only for the caller's own slow calls. Who the caller is comes from the
 * connection, so a run that belongs to somebody else looks exactly like one that does not exist.
 * Framework-internal. Applications use {@link SlowCall}.</p>
 *
 * @since 0.11.0
 */
@RmiService
public interface SlowCallRpc {

    /**
     * Fetches the value of finished work. A result stays on the server until it expires, so
     * several tabs can fetch the same one.
     *
     * @param id the run, from the board
     * @return what the work returned
     * @throws RuntimeException with a sentence, when the caller has no finished run under that id
     *
     * <p><b>Under the hood:</b> arrives on {@code 0x01 RPC_CALL} and answers on
     * {@code 0x01 RPC_RESPONSE} or {@code 0x0F RPC_ERROR}.</p>
     */
    Object collect(String id);

    /**
     * Asks running work to stop. Does nothing when the caller has no running work under that key.
     *
     * @param key the key the work was started under
     *
     * <p><b>Under the hood:</b> arrives on {@code 0x01 RPC_CALL}.</p>
     */
    void cancel(String key);
}
