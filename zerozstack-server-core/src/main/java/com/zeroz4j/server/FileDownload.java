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

import java.nio.file.Path;

/**
 * The answer a {@link FileDownloadHandler} gives: a file to send, or a reason not to.
 *
 * <p>Whether the browser shows the file or saves it follows from the type. Audio, video, pictures
 * other than SVG, PDF and plain text are sent as {@code inline}; everything else, HTML included, is
 * sent as {@code attachment}, so a file a user uploaded can never run as a page of this application.
 * {@link #asAttachment()} makes any file a download.</p>
 *
 * @since 0.11.0
 */
public final class FileDownload {

    /** What happened to the request. */
    enum Outcome { FILE, NOT_FOUND, FORBIDDEN }

    private final Outcome outcome;
    private final Path file;
    private final String fileName;
    private final String contentType;
    private final boolean attachment;

    private FileDownload(Outcome outcome, Path file, String fileName, String contentType,
                         boolean attachment) {
        this.outcome = outcome;
        this.file = file;
        this.fileName = fileName;
        this.contentType = contentType;
        this.attachment = attachment;
    }

    /**
     * Sends this file.
     *
     * @param file an existing, readable file; the framework streams it and never holds it in memory
     * @return the answer
     */
    public static FileDownload of(Path file) {
        if (file == null) {
            return notFound();
        }
        return new FileDownload(Outcome.FILE, file, null, null, false);
    }

    /** @return the answer for a file that does not exist */
    public static FileDownload notFound() {
        return new FileDownload(Outcome.NOT_FOUND, null, null, null, false);
    }

    /** @return the answer for a caller who may not have the file */
    public static FileDownload forbidden() {
        return new FileDownload(Outcome.FORBIDDEN, null, null, null, false);
    }

    /**
     * The name the browser offers when saving. Defaults to the name of the file on disk.
     *
     * @param name the name; slashes, quotes and control characters are removed
     * @return a copy with this name
     */
    public FileDownload withFileName(String name) {
        return new FileDownload(outcome, file, name, contentType, attachment);
    }

    /**
     * The content type. Defaults to a guess from the file extension, then
     * {@code application/octet-stream}.
     *
     * @param type for example {@code audio/mpeg}
     * @return a copy with this type
     */
    public FileDownload withContentType(String type) {
        return new FileDownload(outcome, file, fileName, type, attachment);
    }

    /**
     * Makes the browser save the file instead of showing or playing it.
     *
     * @return a copy sent as an attachment
     */
    public FileDownload asAttachment() {
        return new FileDownload(outcome, file, fileName, contentType, true);
    }

    Outcome outcome() {
        return outcome;
    }

    Path file() {
        return file;
    }

    String fileName() {
        return fileName;
    }

    String contentType() {
        return contentType;
    }

    boolean attachment() {
        return attachment;
    }
}
