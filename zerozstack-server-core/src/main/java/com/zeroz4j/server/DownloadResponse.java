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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the download address answers: a status, headers, and either a short text or a slice of a file.
 *
 * <p>A binding copies {@link #getStatus()} and {@link #getHeaders()} (which include
 * {@code Content-Length}) and then sends the body with {@link #writeBody(OutputStream)}, or hands
 * {@link #getFile()}, {@link #getOffset()} and {@link #getLength()} to a transport that can send a
 * file slice itself.</p>
 *
 * <p>Framework-internal.</p>
 *
 * @since 0.11.0
 */
public final class DownloadResponse {

    private static final int CHUNK = 64 * 1024;

    private final int status;
    private final Map<String, String> headers;
    private final byte[] message;
    private final Path file;
    private final long offset;
    private final long length;

    DownloadResponse(int status, Map<String, String> headers, byte[] message, Path file, long offset,
                     long length) {
        this.status = status;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.message = message;
        this.file = file;
        this.offset = offset;
        this.length = length;
    }

    /** @return the HTTP status */
    public int getStatus() {
        return status;
    }

    /** @return the headers to send, including {@code Content-Length} and {@code Content-Type} */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /** @return the file to send a slice of, or null when the body is a short text or empty */
    public Path getFile() {
        return file;
    }

    /** @return where the slice starts */
    public long getOffset() {
        return offset;
    }

    /** @return how many bytes the slice has; zero when there is no file body */
    public long getLength() {
        return file == null ? 0L : length;
    }

    /**
     * Sends the body: the short text, or the slice of the file, in 64 KB pieces so memory stays flat.
     *
     * @param out where to write
     * @throws IOException when reading or writing failed, for example because the browser went away
     */
    public void writeBody(OutputStream out) throws IOException {
        if (file != null) {
            if (length > 0L) {
                try (SeekableByteChannel channel = Files.newByteChannel(file)) {
                    channel.position(offset);
                    ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(CHUNK, length));
                    long remaining = length;
                    while (remaining > 0L) {
                        buffer.clear();
                        if (remaining < buffer.capacity()) {
                            buffer.limit((int) remaining);
                        }
                        int read = channel.read(buffer);
                        if (read < 0) {
                            throw new IOException("The file ended before its announced length");
                        }
                        out.write(buffer.array(), 0, read);
                        remaining -= read;
                    }
                }
            }
        } else if (message != null) {
            out.write(message);
        }
    }

    static DownloadResponse text(int status, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "text/plain;charset=UTF-8");
        headers.put("Content-Length", Long.toString(bytes.length));
        headers.put("Cache-Control", "no-store");
        headers.put("X-Content-Type-Options", "nosniff");
        return new DownloadResponse(status, headers, bytes, null, 0L, 0L);
    }
}
