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
 * Internal service behind the client error report.
 *
 * <p>The browser sends an uncaught error here, and the server writes it to its log under a short
 * reference. Everything in the arguments is text the browser typed, so the server cuts it to length
 * and removes line breaks before logging it. Framework-internal. Applications set
 * {@code zeroz.clienterrors.enabled} and {@code zeroz.clienterrors.maxPerConnection}.</p>
 *
 * @since 0.11.0
 */
@RmiService
public interface ClientErrorRpc {

    /**
     * Writes one browser error to the server log.
     *
     * @param kind {@code error} for an uncaught exception, {@code rejection} for a promise nobody
     *             handled, {@code start} for an application that did not start
     * @param message what the browser says went wrong
     * @param stack the stack trace the browser gives, or an empty string
     * @param page the path of the page, without its query
     * @return the reference written next to the error in the log, or null when the server does not
     *         take reports (turned off, or this connection has sent its share), which tells the
     *         browser to stop sending
     *
     * <p><b>Under the hood:</b> arrives on {@code 0x01 RPC_CALL} and answers on
     * {@code 0x01 RPC_RESPONSE}.</p>
     */
    String report(String kind, String message, String stack, String page);
}
