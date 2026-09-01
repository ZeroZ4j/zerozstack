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

import com.zeroz4j.api.i18n.Message;

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
 *
 * <h2>Saying it in the caller's language (0.9.0+)</h2>
 *
 * <p>Give it a {@link Message} instead of a sentence and the caller reads the refusal in their own
 * language, while the server log keeps English:</p>
 *
 * <pre>{@code
 * throw new ClientVisibleException(AppText_Text.invoiceAlreadyApproved(invoiceNumber));
 * }</pre>
 *
 * <p><b>Both forms are correct and both are here to stay.</b> They are not two ways to do one
 * thing: one is the translated case and one is the untranslated case. An application that sells in
 * one language should keep writing the sentence, and nothing about it has changed.</p>
 */
public class ClientVisibleException extends RuntimeException implements CarriesClientMessage {

    private static final long serialVersionUID = 1L;

    private final transient Message clientMessage;

    /**
     * @param message the sentence the caller should see
     */
    public ClientVisibleException(String message) {
        super(message);
        this.clientMessage = null;
    }

    /**
     * @param message the sentence the caller should see
     * @param cause   the underlying failure; it is logged, never sent
     */
    public ClientVisibleException(String message, Throwable cause) {
        super(message, cause);
        this.clientMessage = null;
    }

    /**
     * A refusal the caller reads in their own language.
     *
     * <p>The message is carried, not turned into words here: the language is decided at the edge of
     * the server, where the connection is. {@link #getMessage()} is the English version, which is
     * what goes to the log.</p>
     *
     * @param message what to tell the caller
     * @since 0.9.0
     */
    public ClientVisibleException(Message message) {
        super(ServerMessages.inEnglish(message));
        this.clientMessage = message;
    }

    /**
     * A refusal the caller reads in their own language, with the failure behind it.
     *
     * @param message what to tell the caller
     * @param cause   the underlying failure; it is logged, never sent
     * @since 0.9.0
     */
    public ClientVisibleException(Message message, Throwable cause) {
        super(ServerMessages.inEnglish(message), cause);
        this.clientMessage = message;
    }

    /**
     * @return what to tell the caller, still unrendered, or null when this was thrown with a
     *         sentence rather than a message
     * @since 0.9.0
     */
    @Override
    public Message clientMessage() {
        return clientMessage;
    }
}
