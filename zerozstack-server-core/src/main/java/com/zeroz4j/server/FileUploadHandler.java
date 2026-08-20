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
 * What the application does with an uploaded file.
 *
 * <p>Implement it as an {@code @ApplicationScoped} CDI bean; the framework finds it the same way it
 * finds a {@link LiveMutationListener}. There is nothing to register and no route to map.</p>
 *
 * <pre>{@code
 * import com.zeroz4j.server.FileUploadHandler;
 * import com.zeroz4j.server.UploadResult;
 * import com.zeroz4j.server.UploadedFile;
 * import jakarta.enterprise.context.ApplicationScoped;
 * import java.nio.file.Files;
 * import java.nio.file.Path;
 * import java.nio.file.StandardCopyOption;
 * import java.util.UUID;
 *
 * @ApplicationScoped
 * public class SaveToDisk implements FileUploadHandler {
 *
 *     private static final Path FOLDER = Path.of("uploads");
 *
 *     @Override
 *     public UploadResult onFileUploaded(UploadedFile file) throws Exception {
 *         if (file.getSizeBytes() == 0) {
 *             return UploadResult.rejected("That file is empty.");
 *         }
 *         Files.createDirectories(FOLDER);
 *         // The stored name is generated here. file.getFileName() is untrusted and never a path.
 *         Path target = FOLDER.resolve(UUID.randomUUID().toString());
 *         Files.move(file.getTempFile(), target, StandardCopyOption.REPLACE_EXISTING);
 *         return UploadResult.accepted("Saved.");
 *     }
 * }
 * }</pre>
 *
 * <h2>What the framework has already done</h2>
 * <ul>
 *   <li>Checked that the upload came from a live, logged-in connection, using a pass that expires in
 *       seconds and works once.</li>
 *   <li>Refused anything over {@code zeroz.upload.maxBytes} — from the declared length before reading
 *       the body, and again from the bytes actually counted.</li>
 *   <li>Written the bytes to a temporary file under a name it generated, so nothing the browser sent
 *       influenced where anything landed.</li>
 *   <li>Made sure the file is complete: a cancelled or dropped upload never reaches this method.</li>
 * </ul>
 *
 * <h2>What the application must still check</h2>
 * <ul>
 *   <li><b>What the bytes actually are.</b> {@link UploadedFile#getContentType()} is the browser's
 *       guess from the file extension. If it matters, read the file's first bytes.</li>
 *   <li><b>Whether this caller may upload at all</b>, and how much. The framework enforces one login
 *       and one size; quotas, per-role limits and per-tenant rules are application rules.</li>
 *   <li><b>Where the file goes.</b> Generate the stored name.
 *       {@link UploadedFile#getFileName()} is untrusted and must never become a path segment.</li>
 * </ul>
 *
 * <p><b>The temporary file is deleted when this method returns</b>, whether it returned a result or
 * threw. Move it or copy it; do not keep the {@link java.nio.file.Path} and read it later.</p>
 *
 * <p>If more than one implementation is present the framework uses one and logs a warning naming
 * them all — a second handler is almost always an accident.</p>
 */
@FunctionalInterface
public interface FileUploadHandler {

    /**
     * Called once, with a complete file on disk.
     *
     * @param file the uploaded file, its reported name and type, and who sent it
     * @return whether it was kept, and the sentence to show the person who uploaded it
     * @throws Exception when something went wrong; the framework logs it, deletes the temporary file
     *                   and shows a general apology rather than the exception, so return a
     *                   {@link UploadResult#rejected} instead when the person could fix it
     */
    UploadResult onFileUploaded(UploadedFile file) throws Exception;
}
