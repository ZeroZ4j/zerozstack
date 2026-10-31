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

import com.zeroz4j.api.FileDownloadRpc;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Issues download addresses to the page. The address is checked again, by the application's
 * {@link FileDownloadHandler}, on every request.
 *
 * @since 0.11.0
 */
@ApplicationScoped
public class FileDownloadRpcImpl implements FileDownloadRpc {

    /** This server, so its own settings apply rather than the whole process's. */
    @jakarta.inject.Inject
    ServerRuntime runtime;

    @Override
    public String requestDownloadLink(String kind, String id) {
        try {
            ServerConfig config = runtime != null ? runtime.config() : ServerConfig.fromSystemProperties();
            return DownloadLinks.issue(config, kind, id);
        } catch (UploadRefusedException refused) {
            throw new IllegalArgumentException(refused.getMessage(), refused);
        }
    }
}
