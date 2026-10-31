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

import com.zeroz4j.server.DownloadRequest;
import com.zeroz4j.server.FileDownload;
import com.zeroz4j.server.FileDownloadHandler;
import com.zeroz4j.server.FileUploadHandler;
import com.zeroz4j.server.UploadResult;
import com.zeroz4j.server.UploadedFile;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Path;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test beans for the kinds of upload, a large file, and serving a file back. */
public final class RecordingFiles {

    /** "name:size" for every recording received, newest last. */
    static final CopyOnWriteArrayList<String> RECEIVED = new CopyOnWriteArrayList<>();

    /** The file the download handler serves for the id {@code clip}. */
    static volatile Path clip;

    private RecordingFiles() {
    }

    /** A second upload handler, named, with a limit of its own far above the default. */
    @ApplicationScoped
    public static class Uploads implements FileUploadHandler {

        @Override
        public String getKind() {
            return "recording";
        }

        @Override
        public long getMaxBytes() {
            return 200L * 1024L * 1024L;
        }

        @Override
        public UploadResult onFileUploaded(UploadedFile file) {
            RECEIVED.add(file.getFileName() + ":" + file.getSizeBytes() + ":" + file.getKind());
            return UploadResult.accepted("Kept " + file.getFileName());
        }
    }

    /** Serves {@link #clip}, to anyone who asks for the id {@code clip}. */
    @ApplicationScoped
    public static class Downloads implements FileDownloadHandler {

        @Override
        public String getKind() {
            return "recording";
        }

        @Override
        public FileDownload open(DownloadRequest request) {
            if ("clip".equals(request.getId()) && clip != null) {
                return FileDownload.of(clip);
            }
            return FileDownload.notFound();
        }
    }
}
