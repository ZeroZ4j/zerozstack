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
package com.zeroz4j.ui.component;

import com.zeroz4j.ui.theme.Emphasis;
import com.zeroz4j.ui.theme.TextStyle;
import com.zeroz4j.api.RmiClientExecutor;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.file.File;
import org.teavm.jso.file.FileList;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends files to the server: click to choose or drag them onto the box, several at once, with a
 * progress bar and a cancel button for each one.
 *
 * <pre>{@code
 * import com.zeroz4j.ui.component.FileUpload;
 *
 * FileUpload upload = new FileUpload()
 *         .setTitle("Add your photos")
 *         .setAccept("image/*");
 * upload.addUploadListener((name, accepted, message) -> refreshGallery());
 * layout.add(upload);
 * }</pre>
 *
 * <p>Nothing else is needed on the client. On the server the application implements
 * {@code com.zeroz4j.server.FileUploadHandler}, which is where a file is checked and kept.</p>
 *
 * <h2>How a file gets there</h2>
 * <p>Files do not go over the live connection. The component asks that connection for a one-time
 * pass, then posts the file to a separate address which streams it to disk. That is what makes
 * progress and cancelling possible, and it means a big file is not affected by the live connection's
 * message size limit.</p>
 *
 * <h2>What the checks here are worth</h2>
 * <p>The size and type checks in this class are <b>feedback only</b>. They exist so a person is told
 * "that file is too big" without waiting for it to upload. The server checks the size again — twice —
 * and never believes the type at all. Never treat anything decided here as a rule that holds.</p>
 *
 * <p>This is a new component rather than a change to {@link FileInput}. {@code FileInput} is a
 * single-line form field whose value is a file name, it binds to a signal like every other field, and
 * applications use it that way. Uploading is not a form field: it is several files, each with its own
 * progress and its own outcome, and folding that into a value-bound field would have made both
 * things worse. Use {@code FileInput} when a form needs a file chooser; use this when files have to
 * reach the server.</p>
 */
public class FileUpload extends Div {

    /** Told when one file has finished, whether it was kept or not. */
    @FunctionalInterface
    public interface UploadListener {
        /**
         * @param fileName the name the file had on the person's machine
         * @param accepted whether the server kept it
         * @param message  the sentence the server sent back, ready to show
         */
        void onUploadFinished(String fileName, boolean accepted, String message);
    }

    /** Read once per page and remembered, because it cannot change while the page is open. */
    private static long maxBytes = -1L;

    private final HTMLElement input;
    private final Div dropZone;
    private final Div hint;
    private final Div list;
    private final Span titleLabel;
    private final Span subtitleLabel;
    private final List<UploadListener> listeners = new ArrayList<>();

    private String accept;
    private int dragDepth;

    /**
     * Creates an upload box with the default wording.
     */
    public FileUpload() {
        addClassName("flex flex-col gap-3 w-full");

        input = org.teavm.jso.browser.Window.current().getDocument().createElement("input");
        input.setAttribute("type", "file");
        input.setAttribute("multiple", "multiple");
        input.getStyle().setProperty("display", "none");

        titleLabel = new Span("Drop files here");
        titleLabel.addClassName("font-semibold text-base-content");

        subtitleLabel = new Span("or click to choose from your computer");
        subtitleLabel.addClassName(TextStyle.SECONDARY.getClassNames());

        hint = new Div("");
        hint.addClassName(TextStyle.CAPTION.getClassNames());

        Div inner = new Div();
        inner.addClassName("flex flex-col items-center justify-center gap-1 pointer-events-none");
        inner.add(Icon.of("upload", "w-10 h-10 opacity-40"), titleLabel, subtitleLabel, hint);

        dropZone = new Div();
        dropZone.addClassName("border-2 border-dashed border-base-300 rounded-box "
                + "px-6 py-10 text-center cursor-pointer select-none "
                + "transition-colors duration-150 hover:border-primary hover:bg-base-200/50");
        dropZone.add(inner);
        dropZone.getElement().appendChild(input);
        // The box is a place to drop files onto as well as something to press, so it stays a plain
        // box rather than becoming a button. That means building the keyboard side of a button by
        // hand, and all three parts are needed: it says what it is, it is in the tab order, and it
        // answers Enter and Space in wireUp() exactly as it answers a click. Two of the three give
        // something that can be reached and not pressed, or pressed and never reached.
        //
        // No button is put inside the box on purpose. A control inside a control is not read out
        // properly by screen readers, and this box is already the control.
        dropZone.getElement().setAttribute("role", "button");
        dropZone.getElement().setAttribute("tabindex", "0");
        describeDropZone();

        list = new Div();
        list.addClassName("flex flex-col gap-2");

        add(dropZone, list);

        wireUp();
        updateHint();
        loadLimitInBackground();
    }

    /**
     * Replaces the heading in the box.
     *
     * @param title for example {@code "Add your photos"}
     * @return this component
     */
    public FileUpload setTitle(String title) {
        titleLabel.setText(title);
        describeDropZone();
        return this;
    }

    /**
     * Replaces the line under the heading.
     *
     * @param subtitle for example {@code "or click to choose from your computer"}
     * @return this component
     */
    public FileUpload setSubtitle(String subtitle) {
        subtitleLabel.setText(subtitle);
        describeDropZone();
        return this;
    }

    /**
     * Gives the box its spoken name, out of the same two lines it shows.
     *
     * <p>Somebody who cannot see the box hears only its name, so the name has to be the wording on
     * it. Redone whenever that wording changes, or the name would be the old words.</p>
     */
    private void describeDropZone() {
        String title = titleLabel.getText() == null ? "" : titleLabel.getText().trim();
        String subtitle = subtitleLabel.getText() == null ? "" : subtitleLabel.getText().trim();
        String spoken;
        if (title.isEmpty()) {
            spoken = subtitle.isEmpty() ? "Add files" : subtitle;
        } else {
            spoken = subtitle.isEmpty() ? title : title + ", " + subtitle;
        }
        dropZone.getElement().setAttribute("aria-label", spoken);
    }

    /**
     * Limits which files the picker offers, and warns about a dragged file that does not match.
     *
     * <p>Feedback only, in both directions: the picker filter is a convenience and the drag check is
     * a message. The server decides what it will keep.</p>
     *
     * @param accept the same value an HTML file input takes, for example {@code "image/*"} or
     *               {@code ".pdf,.docx"}; null or empty accepts anything
     * @return this component
     */
    public FileUpload setAccept(String accept) {
        this.accept = accept == null || accept.trim().isEmpty() ? null : accept.trim();
        if (this.accept == null) {
            input.removeAttribute("accept");
        } else {
            input.setAttribute("accept", this.accept);
        }
        updateHint();
        return this;
    }

    /**
     * Whether more than one file can be chosen at a time.
     *
     * @param multiple true by default
     * @return this component
     */
    public FileUpload setMultiple(boolean multiple) {
        if (multiple) {
            input.setAttribute("multiple", "multiple");
        } else {
            input.removeAttribute("multiple");
        }
        return this;
    }

    /**
     * Registers a callback for each finished file.
     *
     * <p>It runs on a thread that may make RMI calls, so refreshing a list from the server inside it
     * works.</p>
     *
     * @param listener the callback
     * @return this component
     */
    public FileUpload addUploadListener(UploadListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
        return this;
    }

    /** Removes every finished row, leaving anything still uploading. */
    public void clearFinished() {
        HTMLElement listElement = list.getElement();
        for (int i = listElement.getChildNodes().getLength() - 1; i >= 0; i--) {
            HTMLElement row = (HTMLElement) listElement.getChildNodes().get(i);
            if ("true".equals(row.getAttribute("data-finished"))) {
                listElement.removeChild(row);
            }
        }
    }

    // ---------------------------------------------------------------- wiring

    private void wireUp() {
        UploadBrowser.acceptDrops(dropZone.getElement());
        UploadBrowser.isolateClicks(input);

        // A raw listener, not addDomEventListener: the click has to reach the file input during this
        // event, and a green thread would run after the browser had already moved on -- which browsers
        // treat as script opening a picker on its own and refuse.
        dropZone.getElement().addEventListener("click", (Event event) ->
                UploadBrowser.openPicker(input));

        // Enter and Space are what a button answers, so the box answers them too. Raw and not
        // threaded for the same reason as the click above: the picker has to open while this key
        // press is still being handled. Space is stopped from doing its usual job, which is
        // scrolling the page down.
        dropZone.getElement().addEventListener("keydown", (Event event) -> {
            String key = Js.eventKey(event);
            if ("Enter".equals(key) || " ".equals(key) || "Spacebar".equals(key)) {
                event.preventDefault();
                UploadBrowser.openPicker(input);
            }
        });

        input.addEventListener("change", (Event event) -> {
            File[] chosen = toArray(UploadBrowser.inputFiles(input));
            UploadBrowser.clearInput(input);
            startAll(chosen);
        });

        dropZone.getElement().addEventListener("dragenter", (Event event) -> {
            dragDepth++;
            setDragActive(true);
        });
        dropZone.getElement().addEventListener("dragleave", (Event event) -> {
            // Moving between child elements fires leave then enter, so a plain boolean flickers.
            dragDepth = Math.max(0, dragDepth - 1);
            if (dragDepth == 0) {
                setDragActive(false);
            }
        });
        dropZone.getElement().addEventListener("drop", (Event event) -> {
            event.preventDefault();
            event.stopPropagation();
            dragDepth = 0;
            setDragActive(false);
            // The file list must be read here, while the event is being dispatched: a dropped file
            // list is not guaranteed to still be readable once the handler has returned. The File
            // objects themselves stay valid, so the slow work happens after this.
            startAll(toArray(UploadBrowser.droppedFiles(event)));
        });
    }

    private void setDragActive(boolean active) {
        if (active) {
            dropZone.addClassName("border-primary bg-primary/10");
            dropZone.removeClassName("border-base-300");
        } else {
            dropZone.removeClassName("border-primary bg-primary/10");
            dropZone.addClassName("border-base-300");
        }
    }

    private static File[] toArray(FileList files) {
        if (files == null) {
            return new File[0];
        }
        File[] copied = new File[files.getLength()];
        for (int i = 0; i < copied.length; i++) {
            copied[i] = files.get(i);
        }
        return copied;
    }

    /**
     * Starts every chosen file.
     *
     * <p>Runs on a green thread because asking for an upload pass is a suspending RMI call, and a
     * suspension cannot happen on a stack that began in the browser.</p>
     */
    private void startAll(File[] files) {
        if (files.length == 0) {
            return;
        }
        new Thread(() -> {
            for (File file : files) {
                start(file);
            }
        }).start();
    }

    private void start(File file) {
        String name = file.getName();
        long size = (long) fileSize(file);
        String type = fileType(file);

        Row row = new Row(name, size);
        list.add(row);

        if (accept != null && !matchesAccept(name, type)) {
            row.fail("That kind of file is not accepted here. Please choose " + describeAccept() + ".");
            return;
        }

        long limit = knownMaxBytes();
        if (limit > 0L && size > limit) {
            // Refused without sending a byte. The server refuses it again if this check is wrong.
            row.fail("That file is too big. The largest we can take is " + describeSize(limit) + ".");
            return;
        }

        String pass;
        try {
            pass = (String) RmiClientExecutor.executeCall("com.zeroz4j.api.FileUploadRpc",
                    "requestUploadPass", new Object[] { name, type, size });
        } catch (RuntimeException ex) {
            row.fail(readable(ex.getMessage()));
            return;
        }
        if (pass == null || pass.isEmpty()) {
            row.fail("We could not start that upload. Please try again.");
            return;
        }

        row.startedWith(UploadBrowser.send(UploadBrowser.uploadUrl(), pass, file,
                row::setPercent,
                (status, message) -> row.finish(status, message)));
    }

    private void finished(String fileName, boolean accepted, String message) {
        if (listeners.isEmpty()) {
            return;
        }
        // A listener is application code and may well call a service, so it gets its own green thread.
        new Thread(() -> {
            for (UploadListener listener : listeners) {
                listener.onUploadFinished(fileName, accepted, message);
            }
        }).start();
    }

    // ---------------------------------------------------------------- one row per file

    /** One file in the list: its name, how far it has got, and what became of it. */
    private final class Row extends Div {

        private final Progress bar = new Progress();
        private final Div status = new Div("Waiting...");
        private final Button cancel;
        private final String fileName;
        private JSObject handle;
        private boolean done;

        Row(String fileName, long size) {
            this.fileName = fileName;
            addClassName("flex items-center gap-3 rounded-box bg-base-200 px-4 py-3");

            Span name = new Span(fileName);
            name.addClassName(TextStyle.SECONDARY.getClassNames(Emphasis.FULL)
                    + " truncate font-medium");
            Span sizeLabel = new Span(describeSize(size));
            sizeLabel.addClassName(TextStyle.CAPTION.getClassNames() + " shrink-0");

            Div heading = new Div();
            heading.addClassName("flex items-baseline justify-between gap-3");
            heading.add(name, sizeLabel);

            bar.addClassName("progress progress-primary w-full h-1.5");
            bar.getElement().setAttribute("value", "0");
            bar.getElement().setAttribute("max", "100");

            status.addClassName(TextStyle.CAPTION.getClassNames());

            Div body = new Div();
            body.addClassName("flex flex-col gap-1 flex-1 min-w-0");
            body.add(heading, bar, status);

            cancel = new Button(Icon.of("x", "w-4 h-4"));
            cancel.setClassName("btn btn-ghost btn-xs btn-circle shrink-0");
            cancel.getElement().setAttribute("title", "Stop sending this file");
            cancel.addClickListener(event -> {
                if (!done) {
                    UploadBrowser.abort(handle);
                }
            });

            add(Icon.of("file", "w-5 h-5 opacity-40 shrink-0"), body, cancel);
        }

        void startedWith(JSObject xhr) {
            this.handle = xhr;
            status.setText("Sending...");
        }

        void setPercent(int percent) {
            bar.getElement().setAttribute("value", Integer.toString(percent));
            status.setText(percent >= 100 ? "Almost done..." : "Sending... " + percent + "%");
        }

        void finish(int status0, String message) {
            if (done) {
                return;
            }
            if (status0 == 200) {
                succeed(message == null || message.isEmpty() ? "Done." : message);
            } else if (status0 == -1) {
                fail("Stopped. Nothing was kept.");
            } else if (status0 == 0) {
                fail("We could not reach the server. Check your connection and try again.");
            } else {
                fail(message == null || message.isEmpty()
                        ? "That file was not accepted. Please try a different one." : message);
            }
        }

        void succeed(String message) {
            settle(true, message);
            bar.getElement().setAttribute("value", "100");
            bar.removeClassName("progress-primary");
            bar.addClassName("progress-success");
            status.setClassName(TextStyle.CAPTION.getClassNames(Emphasis.FULL) + " text-success");
            status.setText(message);
        }

        void fail(String message) {
            settle(false, message);
            bar.removeClassName("progress-primary");
            bar.addClassName("progress-error");
            status.setClassName(TextStyle.CAPTION.getClassNames(Emphasis.FULL) + " text-error");
            status.setText(message);
        }

        private void settle(boolean accepted, String message) {
            done = true;
            getElement().setAttribute("data-finished", "true");
            cancel.setVisible(false);
            finished(fileName, accepted, message);
        }
    }

    // ---------------------------------------------------------------- the size limit

    /** The limit, fetched now if it is not known yet. Must be called from a green thread. */
    private static long knownMaxBytes() {
        if (maxBytes < 0L) {
            fetchLimit();
        }
        return maxBytes;
    }

    private void loadLimitInBackground() {
        if (maxBytes >= 0L) {
            return;
        }
        new Thread(() -> {
            fetchLimit();
            updateHint();
        }).start();
    }

    private static void fetchLimit() {
        try {
            Object answer = RmiClientExecutor.executeCall("com.zeroz4j.api.FileUploadRpc",
                    "maxUploadBytes", new Object[0]);
            if (answer instanceof Number) {
                maxBytes = ((Number) answer).longValue();
            }
        } catch (RuntimeException ex) {
            // Not fatal: the limit is a courtesy. Without it the box simply says nothing about size
            // and the server does the refusing.
            maxBytes = -1L;
        }
    }

    private void updateHint() {
        StringBuilder text = new StringBuilder();
        if (maxBytes > 0L) {
            text.append("Up to ").append(describeSize(maxBytes)).append(" per file.");
        }
        if (accept != null) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(capitalize(describeAccept())).append(" only.");
        }
        hint.setText(text.toString());
    }

    private String describeAccept() {
        if (accept == null) {
            return "any file";
        }
        if ("image/*".equals(accept)) {
            return "pictures";
        }
        StringBuilder readable = new StringBuilder();
        for (String part : accept.split(",")) {
            String candidate = part.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (readable.length() > 0) {
                readable.append(", ");
            }
            readable.append(candidate.startsWith(".")
                    ? candidate.substring(1).toUpperCase() + " files" : candidate);
        }
        return readable.length() == 0 ? "any file" : readable.toString();
    }

    private boolean matchesAccept(String name, String type) {
        String lowerName = name == null ? "" : name.toLowerCase();
        String lowerType = type == null ? "" : type.toLowerCase();
        for (String part : accept.split(",")) {
            String candidate = part.trim().toLowerCase();
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.startsWith(".")) {
                if (lowerName.endsWith(candidate)) {
                    return true;
                }
            } else if (candidate.endsWith("/*")) {
                if (lowerType.startsWith(candidate.substring(0, candidate.length() - 1))) {
                    return true;
                }
            } else if (candidate.equals(lowerType)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- small helpers

    /**
     * A byte count written the way a person would say it.
     *
     * <p>Kept here rather than shared with the server copy, because this module is compiled for the
     * browser and cannot see server classes.</p>
     */
    private static String describeSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + (bytes == 1L ? " byte" : " bytes");
        }
        if (bytes < 1024L * 1024L) {
            return round(bytes / 1024.0) + " KB";
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return round(bytes / (1024.0 * 1024.0)) + " MB";
        }
        return round(bytes / (1024.0 * 1024.0 * 1024.0)) + " GB";
    }

    private static String round(double value) {
        long whole = Math.round(value);
        if (Math.abs(value - whole) < 0.05) {
            return Long.toString(whole);
        }
        long tenths = Math.round(value * 10.0);
        return (tenths / 10) + "." + (tenths % 10);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** Server messages are already sentences; anything else becomes one. */
    private static String readable(String message) {
        if (message == null || message.isEmpty() || message.indexOf(' ') < 0) {
            return "We could not start that upload. Please try again.";
        }
        return message;
    }

    /** {@code Blob.getSize()} is bound as an int, which is not wide enough for a large file. */
    @JSBody(params = { "file" }, script = "return file.size;")
    private static native double fileSize(File file);

    /** Empty rather than null when the browser could not guess a type. */
    @JSBody(params = { "file" }, script = "return file.type || '';")
    private static native String fileType(File file);
}
