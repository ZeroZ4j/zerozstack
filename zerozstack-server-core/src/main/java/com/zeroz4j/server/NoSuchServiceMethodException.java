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
 * A call naming a method the service does not have.
 *
 * <p>It is a {@link NoSuchMethodException}, and it always will be — everything that used to catch
 * or test for one still does, and {@link #getMessage()} is the same English sentence it was before
 * language support existed. What it adds is the refusal's <em>name</em>, so the caller can be
 * answered in their own language while the server log stays English.</p>
 *
 * @since 0.9.0
 */
public class NoSuchServiceMethodException extends NoSuchMethodException
        implements CarriesClientMessage {

    private static final long serialVersionUID = 1L;

    private final transient Message clientMessage;

    /**
     * @param message what to tell the caller, in whatever language they read
     */
    public NoSuchServiceMethodException(Message message) {
        super(ServerMessages.inEnglish(message));
        this.clientMessage = message;
    }

    @Override
    public Message clientMessage() {
        return clientMessage;
    }
}
