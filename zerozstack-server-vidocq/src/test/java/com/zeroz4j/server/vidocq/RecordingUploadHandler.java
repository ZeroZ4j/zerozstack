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
package com.zeroz4j.server.vidocq;

import com.zeroz4j.server.FileUploadHandler;
import com.zeroz4j.server.UploadResult;
import com.zeroz4j.server.UploadedFile;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Files;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test bean: keeps what each accepted upload contained and what it was called. */
@ApplicationScoped
public class RecordingUploadHandler implements FileUploadHandler {

    /** "name:content" for every file received, newest last. */
    static final CopyOnWriteArrayList<String> RECEIVED = new CopyOnWriteArrayList<>();

    @Override
    public UploadResult onFileUploaded(UploadedFile file) throws Exception {
        RECEIVED.add(file.getFileName() + ":" + Files.readString(file.getTempFile()));
        return UploadResult.accepted("Kept " + file.getFileName());
    }
}
