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
 * A failure that knows what it wants to say without yet knowing what language to say it in.
 *
 * <p>An exception carrying a {@link Message} instead of a sentence can be thrown deep inside a
 * service method that knows nothing about who is calling, and still be answered in the caller's
 * language: the one place that already decides what a caller is told is also the one place with the
 * caller's language in hand, and it renders the message there. The server log keeps English from
 * the same value, so an operator reading a log at three in the morning does not have to know what
 * language the caller had.</p>
 *
 * <p>{@link ClientVisibleException} implements this. An application with an exception hierarchy of
 * its own can implement it too, and its refusals will be translated the same way.</p>
 *
 * @since 0.9.0
 */
public interface CarriesClientMessage {

    /**
     * @return what to tell the caller, still unrendered; null means fall back to
     *         {@code getMessage()}
     */
    Message clientMessage();
}
