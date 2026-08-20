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
package com.zeroz4j.example.server;

import com.zeroz4j.server.FileUploadHandler;
import com.zeroz4j.server.UploadResult;
import com.zeroz4j.server.UploadedFile;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Keeps uploaded files in a folder beside the server, and shows what an application is responsible
 * for checking.
 *
 * <p>This is the whole server side of accepting uploads: one {@code @ApplicationScoped} bean. There
 * is no route to map and nothing to register.</p>
 */
@ApplicationScoped
public class SaveToFolderUploads implements FileUploadHandler {

    private static final Logger LOG = Logger.getLogger(SaveToFolderUploads.class.getName());

    /** Relative to wherever the server was started. */
    private static final Path FOLDER = Paths.get("uploads");

    @Override
    public UploadResult onFileUploaded(UploadedFile file) throws IOException {
        if (file.getSizeBytes() == 0L) {
            return UploadResult.rejected("That file is empty, so there was nothing to save.");
        }

        // The framework never believes the content type, and neither should this. If a file claims to
        // be a picture, the first few bytes have to agree.
        if (file.getContentType().startsWith("image/") && !looksLikeAnImage(file.getTempFile())) {
            return UploadResult.rejected("That is not really a picture, even though its name says so.");
        }

        Files.createDirectories(FOLDER);

        // The stored name is generated here. file.getFileName() came from the browser and is never
        // used to build a path -- it is kept beside the file as plain text instead.
        String storedName = UUID.randomUUID().toString();
        Path target = FOLDER.resolve(storedName);
        Files.move(file.getTempFile(), target, StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(FOLDER.resolve(storedName + ".name"), file.getFileName(),
                StandardCharsets.UTF_8);

        String who = file.getPrincipal() == null ? "an anonymous visitor" : file.getPrincipal().getName();
        LOG.info("[showcase] Kept " + file.getSizeBytes() + " bytes from " + who + " as " + storedName);

        return UploadResult.accepted("Saved to the uploads folder.");
    }

    /** The first bytes of a PNG, JPEG, GIF or WebP. Enough to catch a renamed file. */
    private static boolean looksLikeAnImage(Path path) throws IOException {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = Files.newInputStream(path)) {
            read = in.readNBytes(head, 0, head.length);
        }
        if (read < 4) {
            return false;
        }
        boolean png = head[0] == (byte) 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
        boolean jpeg = head[0] == (byte) 0xFF && head[1] == (byte) 0xD8;
        boolean gif = head[0] == 'G' && head[1] == 'I' && head[2] == 'F';
        boolean webp = read >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F'
                && head[3] == 'F' && head[8] == 'W' && head[9] == 'E' && head[10] == 'B'
                && head[11] == 'P';
        return png || jpeg || gif || webp;
    }
}
