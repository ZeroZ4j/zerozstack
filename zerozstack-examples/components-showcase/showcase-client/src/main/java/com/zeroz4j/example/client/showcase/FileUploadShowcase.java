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
package com.zeroz4j.example.client.showcase;

import com.zeroz4j.ui.component.FileUpload;
import com.zeroz4j.ui.layout.Div;

/**
 * The upload box, wired to a server that really keeps the files.
 *
 * <p>The server side of this page is one class, {@code SaveToFolderUploads}, which puts each file in
 * an {@code uploads} folder beside the running server.</p>
 */
public class FileUploadShowcase extends ComponentShowcase {

    public FileUploadShowcase() {
        super();
        addTitle("FileUpload");
        addDescription("FileUpload sends files to the server. Drag them onto the box or click to "
                + "choose. Each file gets its own progress bar and can be stopped while it is going. "
                + "Files do not travel over the live connection, so the connection's message size "
                + "limit does not apply to them.");

        FileUpload anyFile = new FileUpload();
        Div log = new Div("Nothing uploaded yet.");
        log.addClassName("text-sm text-base-content/60");
        anyFile.addUploadListener((name, accepted, message) ->
                log.setText((accepted ? "Kept: " : "Refused: ") + name + " - " + message));

        addSection("Anything, several at a time", anyFile, log);

        FileUpload pictures = new FileUpload()
                .setTitle("Drop your photos here")
                .setSubtitle("or click to choose them")
                .setAccept("image/*");

        addSection("Pictures only", pictures);

        FileUpload single = new FileUpload()
                .setTitle("One document")
                .setSubtitle("or click to choose it")
                .setAccept(".pdf,.txt,.md")
                .setMultiple(false);

        addSection("One file at a time", single);
    }
}
