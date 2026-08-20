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
 * What a {@link FileUploadHandler} says about a file it was given.
 *
 * <p>The message goes straight onto the screen next to the file, so write it for the person sitting
 * there: short sentence, everyday words, and when you refuse something, say what would work.</p>
 */
public final class UploadResult {

    private final boolean accepted;
    private final String message;

    private UploadResult(boolean accepted, String message) {
        this.accepted = accepted;
        this.message = message == null ? "" : message;
    }

    /**
     * The file was kept.
     *
     * @param message what to show, for example {@code "Saved."} or {@code "Added to your photos."}
     * @return the result
     */
    public static UploadResult accepted(String message) {
        return new UploadResult(true, message);
    }

    /**
     * The file was not kept, and the person should be told why.
     *
     * @param message what to show, for example
     *                {@code "That is not a picture. Please choose a JPEG or a PNG."}
     * @return the result
     */
    public static UploadResult rejected(String message) {
        return new UploadResult(false, message);
    }

    /** @return true when the file was kept */
    public boolean isAccepted() {
        return accepted;
    }

    /** @return the sentence to show, never null */
    public String getMessage() {
        return message;
    }
}
