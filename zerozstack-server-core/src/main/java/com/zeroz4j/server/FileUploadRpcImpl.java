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

import com.zeroz4j.api.FileUploadRpc;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Answers the two questions the upload component asks over the live connection.
 *
 * <p>Deliberately not {@code @Secured}: an application with no login must still be able to accept
 * files. The pass records whatever identity the connection has — including none — and the
 * application's {@link FileUploadHandler} decides what to do with it.</p>
 *
 * <p>Framework-internal.</p>
 */
@ApplicationScoped
public class FileUploadRpcImpl implements FileUploadRpc {

    @Override
    public long maxUploadBytes() {
        return UploadLimits.maxBytes();
    }

    @Override
    public String requestUploadPass(String fileName, String contentType, long sizeBytes) {
        try {
            return UploadPasses.issue(fileName, contentType, sizeBytes).getToken();
        } catch (UploadRefusedException refused) {
            // The RMI layer sends the message back to the browser, and the component shows it, so it
            // has to read like a sentence rather than a diagnostic.
            throw new IllegalArgumentException(refused.getMessage(), refused);
        }
    }
}
