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
 * The answer the upload address sends back: an HTTP status and one sentence.
 *
 * <p>The sentence is what the component puts on the screen, so it is written for a non-technical
 * reader in every case, including the failures.</p>
 *
 * <p>Framework-internal.</p>
 */
public final class UploadOutcome {

    private final int status;
    private final String message;

    private UploadOutcome(int status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * @param message what to show
     * @return a 200 outcome
     */
    public static UploadOutcome ok(String message) {
        return new UploadOutcome(200, message);
    }

    /**
     * @param status  the HTTP status
     * @param message what to show
     * @return a failing outcome
     */
    public static UploadOutcome refused(int status, String message) {
        return new UploadOutcome(status, message);
    }

    /** @return the HTTP status */
    public int getStatus() {
        return status;
    }

    /** @return the sentence to show */
    public String getMessage() {
        return message;
    }

    /** @return true when the file was accepted */
    public boolean isOk() {
        return status == 200;
    }
}
