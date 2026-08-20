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
 * Internal service the file upload component calls before it sends a file.
 *
 * <p>Files do not travel over the live connection. They are posted to a separate HTTP address, which
 * streams them straight to disk instead of assembling a whole message in memory — so an upload is
 * unaffected by {@code zeroz.ws.maxBinaryMessageBytes}, and a large file never has to fit in one
 * frame.</p>
 *
 * <p>What the live connection is used for is <em>permission</em>. The page asks here for a pass; the
 * server issues one bound to the identity of this connection, expiring in seconds and usable exactly
 * once. The upload address accepts nothing without a valid pass, so uploading inherits the same login
 * as every other call and a stranger cannot post files to the server.</p>
 *
 * <p>Framework-internal. Applications implement
 * {@code com.zeroz4j.server.FileUploadHandler} and use
 * {@code com.zeroz4j.ui.component.FileUpload}; neither requires calling this.</p>
 */
@RmiService
public interface FileUploadRpc {

    /**
     * The largest file this deployment accepts, in bytes.
     *
     * <p>The component reads it once so it can tell a user a file is too big without sending it.
     * That is feedback only — the upload address checks the size again, twice, and does not trust
     * the browser.</p>
     *
     * @return the configured maximum, from {@code zeroz.upload.maxBytes}
     */
    long maxUploadBytes();

    /**
     * Issues a one-time pass for uploading one file.
     *
     * <p>The pass records the file's declared name, type and size, and the identity of the connection
     * that asked for it. The upload address reads the name and type from the pass, never from the
     * HTTP request, so the request cannot claim a different file than the one the pass was issued
     * for.</p>
     *
     * @param fileName    the name the browser reported. <b>Untrusted.</b> Recorded and passed to the
     *                    application as information; never used to build a path.
     * @param contentType the type the browser reported. <b>Untrusted.</b> Proof of nothing.
     * @param sizeBytes   the size the browser reported, checked against the configured maximum here
     *                    so an oversized file is refused before a single byte is sent
     * @return the pass token to send with the file
     * @throws IllegalArgumentException when the declared size exceeds the maximum; the message is
     *                                  written for the person looking at the screen
     */
    String requestUploadPass(String fileName, String contentType, long sizeBytes);
}
