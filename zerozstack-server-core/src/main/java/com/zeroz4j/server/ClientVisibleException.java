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

/**
 * An error whose message is meant for the caller.
 *
 * <p>Throw this from an {@code @RmiService} implementation when the client should read the reason:
 * "That invoice is already approved", "This code has expired". The message travels to the client
 * word for word on the {@code 0x0F RMI_ERROR} frame.</p>
 *
 * <pre>{@code
 * import com.zeroz4j.server.ClientVisibleException;
 *
 * @Override
 * public void approve(String invoiceId) {
 *     Invoice invoice = invoices.byId(invoiceId);
 *     if (invoice.isApproved()) {
 *         throw new ClientVisibleException("That invoice was already approved.");
 *     }
 *     ...
 * }
 * }</pre>
 *
 * <p>Every <em>other</em> exception is replaced with a generic sentence and a short reference code
 * before it leaves the server. That is deliberate: the message of an unplanned failure names classes,
 * fields, queries and container internals, and an anonymous caller can trigger those failures on
 * purpose to learn how the system is built. The real message and stack trace go to the server log
 * under the same reference code, so support can match a user's screenshot to a log line.</p>
 *
 * <p>The framework's own refusals — authentication required, access denied, unknown service or
 * method, failed argument validation — are written to be read by clients and reach them unchanged.</p>
 *
 * <p>Unchecked on purpose: a domain refusal is not something every caller in the chain should have
 * to declare, and the client stub the annotation processor generates has no matching checked type
 * to declare either.</p>
 */
public class ClientVisibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message the sentence the caller should see
     */
    public ClientVisibleException(String message) {
        super(message);
    }

    /**
     * @param message the sentence the caller should see
     * @param cause   the underlying failure; it is logged, never sent
     */
    public ClientVisibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
