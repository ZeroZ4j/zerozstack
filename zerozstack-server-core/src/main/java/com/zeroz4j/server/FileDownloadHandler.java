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
 * What the application does when a page asks for one of its files.
 *
 * <p>Implement it as an {@code @ApplicationScoped} CDI bean, the same way as a
 * {@link FileUploadHandler}. The page asks for a file by kind and id; the server issues an address
 * for it; and every request to that address, including each range request a media element makes
 * while seeking, calls {@link #open(DownloadRequest)} again with the identity of the person the
 * address was issued to.</p>
 *
 * <pre>{@code
 * @ApplicationScoped
 * public class RecordingDownloads implements FileDownloadHandler {
 *
 *     @Inject Recordings recordings;
 *
 *     public String getKind() {
 *         return "recording";
 *     }
 *
 *     public FileDownload open(DownloadRequest request) {
 *         Recording recording = recordings.find(request.getId());
 *         if (recording == null) {
 *             return FileDownload.notFound();
 *         }
 *         if (!recording.isVisibleTo(request.getPrincipal())) {
 *             return FileDownload.forbidden();
 *         }
 *         return FileDownload.of(recording.getPath()).withContentType("audio/mpeg");
 *     }
 * }
 * }</pre>
 *
 * <p>The framework handles the content type header, the content disposition, range requests so media
 * can seek, and conditional requests. It never reads the file into memory. The id is text from the
 * page: look it up, never use it as a path.</p>
 *
 * @since 0.11.0
 */
@FunctionalInterface
public interface FileDownloadHandler {

    /**
     * The name a page uses to reach this handler. Letters, digits, dot, dash and underscore, at most
     * 64 characters. The default, empty, is the handler for requests that name no kind.
     *
     * @return the kind, never null
     */
    default String getKind() {
        return "";
    }

    /**
     * Decides whether this caller may have this file, and which file it is.
     *
     * @param request what was asked for and who is asking
     * @return the file, {@link FileDownload#notFound()} or {@link FileDownload#forbidden()}; null counts
     *         as not found
     * @throws Exception when something went wrong; the framework logs it and answers with a general
     *                   apology
     */
    FileDownload open(DownloadRequest request) throws Exception;
}
