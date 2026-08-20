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

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.file.File;
import org.teavm.jso.file.FileList;

/**
 * The browser APIs {@link FileUpload} needs, isolated behind one class.
 *
 * <p>Two of them have no TeaVM binding and are written as raw script here. The first is the file list
 * on a drop event, which hangs off {@code dataTransfer}. The second is upload progress: it is
 * reported on {@code XMLHttpRequest.upload}, a separate object the binding does not expose, and it is
 * the reason this component uses {@code XMLHttpRequest} rather than {@code fetch} — {@code fetch}
 * cannot report how much of a request body has gone out, and cannot cancel as simply.</p>
 *
 * <p>Framework-internal.</p>
 */
final class UploadBrowser {

    private UploadBrowser() {}

    /** Reports how far an upload has got. */
    @JSFunctor
    interface ProgressCallback extends JSObject {
        /**
         * @param percent how much of the file has gone out, 0 to 100
         */
        void onProgress(int percent);
    }

    /** Reports that an upload stopped, for any reason. */
    @JSFunctor
    interface DoneCallback extends JSObject {
        /**
         * @param status  the HTTP status, {@code 0} when the request never reached the server, or
         *                {@code -1} when it was cancelled
         * @param message what the server said, written for a person to read; empty when there was no
         *                answer
         */
        void onDone(int status, String message);
    }

    /**
     * The files a file input currently holds.
     *
     * @param input the {@code <input type="file">} element
     * @return the list, or null when the element holds none
     */
    @JSBody(params = { "input" }, script = "return input.files || null;")
    static native FileList inputFiles(HTMLElement input);

    /**
     * The files a drop event carried.
     *
     * @param event the {@code drop} event
     * @return the list, or null when the drop carried no files (dragged text, for instance)
     */
    @JSBody(params = { "event" }, script =
        "return (event.dataTransfer && event.dataTransfer.files) || null;")
    static native FileList droppedFiles(Event event);

    /**
     * Tells the browser this element accepts a drop.
     *
     * <p>Without preventing the default on {@code dragover}, the browser navigates away to the
     * dropped file instead of handing it to the page. Nothing else here is optional either: the
     * default must be prevented on {@code dragenter} too, or Firefox never fires {@code drop}.</p>
     *
     * @param element the drop area
     */
    @JSBody(params = { "element" }, script =
        "var stop = function(e) { e.preventDefault(); e.stopPropagation(); };"
        + "element.addEventListener('dragenter', stop);"
        + "element.addEventListener('dragover', stop);")
    static native void acceptDrops(HTMLElement element);

    /** Clears a file input so choosing the same file twice in a row fires {@code change} again. */
    @JSBody(params = { "input" }, script = "input.value = '';")
    static native void clearInput(HTMLElement input);

    /** Opens the operating system file picker. */
    @JSBody(params = { "input" }, script = "input.click();")
    static native void openPicker(HTMLElement input);

    /**
     * Stops the hidden file input's own click from reaching the drop area around it.
     *
     * <p>The input lives inside the drop area so one element carries both behaviours. Without this,
     * {@link #openPicker} would fire a click that bubbles straight back into the drop area's own
     * click handler, which opens the picker again — an endless loop.</p>
     *
     * @param input the {@code <input type="file">} element
     */
    @JSBody(params = { "input" }, script =
        "input.addEventListener('click', function(e) { e.stopPropagation(); });")
    static native void isolateClicks(HTMLElement input);

    /**
     * Where this application is served from, ending in a slash, followed by the upload path.
     *
     * <p>Taken from the shell's {@code <base href>} when there is one — a WAR is mounted under a
     * context path, and a hard-coded {@code "/zeroz4j-upload"} would miss it. Deep client routes make
     * a plain relative URL wrong, so the base is resolved explicitly rather than left to the
     * document.</p>
     *
     * @return an absolute URL
     */
    @JSBody(params = {}, script =
        "var base = document.querySelector('base');"
        + "var root = base ? document.baseURI : (window.location.origin + '/');"
        + "return new URL('zeroz4j-upload', root).href;")
    static native String uploadUrl();

    /**
     * Posts one file, reporting progress as it goes.
     *
     * <p>The body is the file itself, so the browser streams it rather than encoding it, and the
     * server can write it to disk as it arrives. The only thing sent alongside it is the one-time
     * pass; the file name, its type and who is uploading all come from the pass on the server side.
     * The identity cookie rides along because the request is same-origin.</p>
     *
     * @param url        the upload address
     * @param pass       the one-time pass
     * @param file       the file to send
     * @param onProgress called repeatedly while the body goes out
     * @param onDone     called exactly once, however it ends
     * @return a handle to pass to {@link #abort(JSObject)}
     */
    @JSBody(params = { "url", "pass", "file", "onProgress", "onDone" }, script =
        "var xhr = new XMLHttpRequest();"
        + "xhr.open('POST', url, true);"
        + "xhr.setRequestHeader('X-Zeroz4j-Upload-Pass', pass);"
        + "xhr.upload.onprogress = function(e) {"
        + "  if (e.lengthComputable && e.total > 0) {"
        + "    onProgress(Math.round(e.loaded * 100 / e.total));"
        + "  }"
        + "};"
        + "xhr.onload = function() { onDone(xhr.status, xhr.responseText || ''); };"
        + "xhr.onerror = function() { onDone(0, ''); };"
        + "xhr.onabort = function() { onDone(-1, ''); };"
        + "xhr.send(file);"
        + "return xhr;")
    static native JSObject send(String url, String pass, File file, ProgressCallback onProgress,
                               DoneCallback onDone);

    /**
     * Stops an upload that is still going.
     *
     * @param handle the value {@link #send} returned; ignored when null
     */
    @JSBody(params = { "handle" }, script =
        "if (handle) { try { handle.abort(); } catch (e) { } }")
    static native void abort(JSObject handle);
}
