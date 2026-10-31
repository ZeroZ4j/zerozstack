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
 * Internal service the audio and video components call to get an address for a file on the server.
 *
 * <p>File bytes never travel over the live connection. The page asks here for an address; the server
 * issues one bound to the identity of this connection, valid for an hour by default
 * ({@code zeroz.download.linkMinutes}) and usable repeatedly so a media element can seek. The address
 * is served over plain HTTP by the framework, which asks the application's
 * {@code com.zeroz4j.server.FileDownloadHandler} on every request.</p>
 *
 * <p>Framework-internal. Applications implement {@code FileDownloadHandler}.</p>
 *
 * @since 0.11.0
 */
@RmiService
public interface FileDownloadRpc {

    /**
     * Issues an address for one file.
     *
     * @param kind the kind that selects the application's handler, empty for the default one
     * @param id   the id the handler looks the file up by
     * @return the address, relative to the page's base address
     */
    String requestDownloadLink(String kind, String id);
}
