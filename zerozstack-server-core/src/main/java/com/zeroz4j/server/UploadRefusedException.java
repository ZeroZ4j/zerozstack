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
 * Why an upload was refused, together with the HTTP status to answer and the sentence to show.
 *
 * <p>The message is written for the person looking at the screen, not for a log file, because the
 * component puts it straight in front of them.</p>
 *
 * <p>Framework-internal.</p>
 */
public class UploadRefusedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;

    /**
     * @param status  the HTTP status the upload address answers with
     * @param message a plain sentence a non-technical reader will understand
     */
    public UploadRefusedException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** @return the HTTP status to answer with */
    public int getStatus() {
        return status;
    }
}
