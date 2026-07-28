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
package com.zeroz4j.client;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Int8Array;

/**
 * Low-level TeaVM JavaScript Object (JSO) wrapper around native browser {@code WebSocket}.
 * Configures binary mode ({@code binaryType = 'arraybuffer'}) for raw binary frame transport.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>TeaVM JSO:</b> Uses {@code @JSBody} annotations containing native JavaScript code snippets to instantiate
 *       and interface with browser JavaScript APIs in WebAssembly execution contexts.</li>
 *   <li><b>Binary ArrayBuffer:</b> Converts inbound JavaScript {@code event.data} ArrayBuffer objects into {@code Int8Array}
 *       before calling the Java message handler.</li>
 * </ul>
 */
public class WasmWebSocket {
    private final JSObject ws;

    /**
     * Creates a new native WebSocket connection with lifecycle callback listeners.
     *
     * @param url     the WebSocket target URL
     * @param handler incoming binary message handler
     * @param onOpen  connection opened callback
     * @param onError error callback
     * @param onClose connection closed callback
     *
     * <p><b>Under the hood:</b> Calls native JS constructor {@code new WebSocket(url)}, sets {@code binaryType = 'arraybuffer'},
     * and attaches event handler callbacks for {@code onopen}, {@code onmessage}, {@code onerror}, and {@code onclose}.</p>
     */
    public WasmWebSocket(String url, MessageHandler handler, ConnectionHandler onOpen,
                         ErrorHandler onError, CloseHandler onClose) {
        this.ws = createWebSocket(url, handler, onOpen, onError, onClose);
    }

    @JSBody(params = { "url", "handler", "onOpen", "onError", "onClose" }, script =
        "var ws = new WebSocket(url);" +
        "ws.binaryType = 'arraybuffer';" +
        "ws.onopen = function() { onOpen(); };" +
        "ws.onmessage = function(event) {" +
        "  var arr = new Int8Array(event.data);" +
        "  handler(arr);" +
        "};" +
        "ws.onerror = function(event) { onError('WebSocket error'); };" +
        "ws.onclose = function(event) { onClose(event.code, event.reason || 'Connection closed'); };" +
        "return ws;"
    )
    private static native JSObject createWebSocket(String url, MessageHandler handler,
        ConnectionHandler onOpen, ErrorHandler onError, CloseHandler onClose);

    /**
     * Transmits raw binary bytes across the open WebSocket connection.
     *
     * @param bytes raw byte array payload to send
     *
     * <p><b>Under the hood:</b> Copies {@code bytes} into a JS {@code ArrayBuffer} via {@code Int8Array} and invokes {@code ws.send(buf)}.</p>
     */
    public void send(byte[] bytes) {
        sendBytes(ws, bytes);
    }

    @JSBody(params = { "ws", "bytes" }, script =
        "var buf = new ArrayBuffer(bytes.length);" +
        "var arr = new Int8Array(buf);" +
        "arr.set(bytes);" +
        "ws.send(buf);"
    )
    private static native void sendBytes(JSObject ws, byte[] bytes);

    /** TeaVM JSFunctor for inbound message array buffer callbacks. */
    @JSFunctor
    public interface MessageHandler extends JSObject {
        void onMessage(Int8Array data);
    }

    /** TeaVM JSFunctor for open connection callbacks. */
    @JSFunctor
    public interface ConnectionHandler extends JSObject {
        void onConnect();
    }

    /** TeaVM JSFunctor for error callbacks. */
    @JSFunctor
    public interface ErrorHandler extends JSObject {
        void onError(String message);
    }

    /** TeaVM JSFunctor for close callbacks. */
    @JSFunctor
    public interface CloseHandler extends JSObject {
        void onClose(int code, String reason);
    }
}
