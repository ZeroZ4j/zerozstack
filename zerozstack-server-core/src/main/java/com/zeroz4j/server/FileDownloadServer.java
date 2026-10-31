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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serves an application file at {@code zeroz4j-download}: checks the address, asks the application's
 * {@link FileDownloadHandler}, and answers with the right type, disposition and byte range.
 *
 * <p>Every binding calls {@link #respond(DownloadHttpRequest)} and sends what comes back, so a WAR
 * and a standalone server answer a request identically. This class names no servlet or JAX-RS
 * type.</p>
 *
 * <p>Framework-internal; applications write a {@link FileDownloadHandler}.</p>
 *
 * @since 0.11.0
 */
public final class FileDownloadServer {

    private static final Logger LOG = Logger.getLogger(FileDownloadServer.class.getName());

    /**
     * The path the files are served from, relative to wherever the application is mounted. Reserved
     * by the framework, like {@code wasm-rmi} and {@code zeroz4j-upload}.
     */
    public static final String DOWNLOAD_PATH = "zeroz4j-download";

    /** The query parameter that carries the address's token. */
    public static final String PASS_PARAMETER = "pass";

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
                    .withZone(ZoneOffset.UTC);

    private static final String REFUSED = "We could not open that file. Reload the page and try again.";

    private FileDownloadServer() {}

    /**
     * Answers one request.
     *
     * @param request the incoming request
     * @return the answer; never null
     */
    public static DownloadResponse respond(DownloadHttpRequest request) {
        String method = request.method() == null ? "" : request.method().toUpperCase(Locale.ROOT);
        boolean head = "HEAD".equals(method);
        if (!head && !"GET".equals(method)) {
            DownloadResponse notAllowed = DownloadResponse.text(405, "Method not allowed.");
            Map<String, String> headers = new LinkedHashMap<>(notAllowed.getHeaders());
            headers.put("Allow", "GET, HEAD");
            return new DownloadResponse(405, headers,
                    "Method not allowed.".getBytes(StandardCharsets.UTF_8), null, 0L, 0L);
        }

        DownloadLinks.Link link;
        try {
            String clientId = ClientIdentity.verify(
                    ClientIdentity.fromCookieHeader(request.header("Cookie")));
            link = DownloadLinks.find(request.queryParameter(PASS_PARAMETER), clientId);
        } catch (RuntimeException ex) {
            link = null;
        }
        if (link == null) {
            return DownloadResponse.text(401, REFUSED);
        }

        FileDownloadHandler handler = DownloadLinks.handlerFor(link.kind, true);
        if (handler == null) {
            return DownloadResponse.text(404, "We could not find that file.");
        }

        FileDownload answer;
        try {
            // The identity on this thread is the one the address was issued to, not this request's,
            // so a handler that reads RmiRequestContext sees the same person as one that reads the
            // request.
            RmiRequestContext.setContext(link.principal, link.roles, link.sessionId, link.tenantId,
                    link.clientId);
            try {
                answer = handler.open(link.toRequest());
            } finally {
                RmiRequestContext.clear();
            }
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "[zeroz4j] FileDownloadHandler " + handler.getClass().getName()
                    + " threw while opening a file", ex);
            return DownloadResponse.text(500, "Something went wrong on our side. Please try again.");
        }
        if (answer == null || answer.outcome() == FileDownload.Outcome.NOT_FOUND) {
            return DownloadResponse.text(404, "We could not find that file.");
        }
        if (answer.outcome() == FileDownload.Outcome.FORBIDDEN) {
            return DownloadResponse.text(403, "You do not have access to that file.");
        }

        Path file = answer.file();
        long size;
        long modified;
        try {
            if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
                return DownloadResponse.text(404, "We could not find that file.");
            }
            size = Files.size(file);
            modified = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException | RuntimeException ex) {
            return DownloadResponse.text(404, "We could not find that file.");
        }

        String etag = "\"" + Long.toHexString(size) + "-" + Long.toHexString(modified) + "\"";
        String lastModified = HTTP_DATE.format(Instant.ofEpochMilli(modified - Math.floorMod(modified, 1000L)));
        String type = contentType(answer, file);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", type);
        headers.put("Accept-Ranges", "bytes");
        headers.put("ETag", etag);
        headers.put("Last-Modified", lastModified);
        headers.put("Content-Disposition", disposition(answer, file, type));
        headers.put("X-Content-Type-Options", "nosniff");
        headers.put("Cache-Control", "private, no-cache");

        if (matchesNoneMatch(request.header("If-None-Match"), etag)) {
            return new DownloadResponse(304, headers, null, null, 0L, 0L);
        }

        long start = 0L;
        long end = size - 1L;
        int status = 200;
        String range = request.header("Range");
        if (range != null && size > 0L && ifRangeAllows(request.header("If-Range"), etag, lastModified)) {
            long[] parsed = parseRange(range, size);
            if (parsed == UNSATISFIABLE) {
                Map<String, String> refused = new LinkedHashMap<>();
                refused.put("Content-Range", "bytes */" + size);
                refused.put("Content-Length", "0");
                refused.put("Accept-Ranges", "bytes");
                return new DownloadResponse(416, refused, null, null, 0L, 0L);
            }
            if (parsed != null) {
                start = parsed[0];
                end = parsed[1];
                status = 206;
                headers.put("Content-Range", "bytes " + start + "-" + end + "/" + size);
            }
        }

        long length = size == 0L ? 0L : end - start + 1L;
        headers.put("Content-Length", Long.toString(length));
        return new DownloadResponse(status, headers, null, head ? null : file, start, length);
    }

    private static final long[] UNSATISFIABLE = new long[0];

    /**
     * Reads one {@code bytes=} range.
     *
     * @param header the Range header
     * @param size   the file size
     * @return start and end inclusive; {@link #UNSATISFIABLE} when no byte is in range; null when the
     *         header is not one this server answers with a partial body (several ranges, another unit,
     *         bad numbers), in which case the whole file is sent, which HTTP allows
     */
    static long[] parseRange(String header, long size) {
        String value = header.trim();
        if (!value.regionMatches(true, 0, "bytes=", 0, 6)) {
            return null;
        }
        String spec = value.substring(6).trim();
        if (spec.indexOf(',') >= 0) {
            return null;
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String first = spec.substring(0, dash).trim();
        String last = spec.substring(dash + 1).trim();
        try {
            if (first.isEmpty()) {
                if (last.isEmpty()) {
                    return null;
                }
                long suffix = Long.parseLong(last);
                if (suffix <= 0L) {
                    return UNSATISFIABLE;
                }
                return new long[] { Math.max(0L, size - suffix), size - 1L };
            }
            long start = Long.parseLong(first);
            long end = last.isEmpty() ? size - 1L : Long.parseLong(last);
            if (start < 0L || (!last.isEmpty() && end < start)) {
                return null;
            }
            if (start >= size) {
                return UNSATISFIABLE;
            }
            return new long[] { start, Math.min(end, size - 1L) };
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean ifRangeAllows(String ifRange, String etag, String lastModified) {
        if (ifRange == null || ifRange.trim().isEmpty()) {
            return true;
        }
        String value = ifRange.trim();
        return value.equals(etag) || value.equals(lastModified);
    }

    private static boolean matchesNoneMatch(String header, String etag) {
        if (header == null || header.trim().isEmpty()) {
            return false;
        }
        for (String candidate : header.split(",")) {
            String token = candidate.trim();
            if (token.equals("*")) {
                return true;
            }
            if (token.startsWith("W/")) {
                token = token.substring(2);
            }
            if (token.equals(etag)) {
                return true;
            }
        }
        return false;
    }

    private static String contentType(FileDownload answer, Path file) {
        String declared = answer.contentType();
        if (declared != null) {
            String cleaned = clean(declared).trim();
            if (!cleaned.isEmpty()) {
                return cleaned;
            }
        }
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        String guess = guessType(name);
        if (guess != null) {
            return guess;
        }
        try {
            String probed = Files.probeContentType(file);
            if (probed != null) {
                return probed;
            }
        } catch (IOException | RuntimeException ex) {
            // Fall through to the generic type.
        }
        return "application/octet-stream";
    }

    /**
     * A type from a file name's extension, for the formats applications serve most.
     *
     * @param name a file name
     * @return the type, or null when the extension is not known
     */
    static String guessType(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        switch (name.substring(dot + 1).toLowerCase(Locale.ROOT)) {
            case "mp3": return "audio/mpeg";
            case "m4a": return "audio/mp4";
            case "aac": return "audio/aac";
            case "wav": return "audio/wav";
            case "oga":
            case "ogg": return "audio/ogg";
            case "opus": return "audio/opus";
            case "flac": return "audio/flac";
            case "weba": return "audio/webm";
            case "mp4":
            case "m4v": return "video/mp4";
            case "webm": return "video/webm";
            case "ogv": return "video/ogg";
            case "mov": return "video/quicktime";
            case "mkv": return "video/x-matroska";
            case "png": return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "svg": return "image/svg+xml";
            case "pdf": return "application/pdf";
            case "txt": return "text/plain; charset=UTF-8";
            case "csv": return "text/csv; charset=UTF-8";
            case "json": return "application/json";
            case "html":
            case "htm": return "text/html; charset=UTF-8";
            case "zip": return "application/zip";
            default: return null;
        }
    }

    private static boolean showsInline(String type) {
        String lower = type.toLowerCase(Locale.ROOT);
        if (lower.startsWith("image/svg")) {
            return false;
        }
        return lower.startsWith("audio/") || lower.startsWith("video/") || lower.startsWith("image/")
                || lower.startsWith("application/pdf") || lower.startsWith("text/plain");
    }

    private static String disposition(FileDownload answer, Path file, String type) {
        String name = answer.fileName();
        if (name == null || name.trim().isEmpty()) {
            name = file.getFileName() == null ? "download" : file.getFileName().toString();
        }
        name = clean(name).replace('/', '_').replace('\\', '_').replace('"', '_').replace(';', '_').trim();
        if (name.isEmpty()) {
            name = "download";
        }
        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            ascii.append(c >= 0x20 && c < 0x7f ? c : '_');
        }
        String kind = answer.attachment() || !showsInline(type) ? "attachment" : "inline";
        return kind + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + percentEncode(name);
    }

    /** Removes control characters, including the line breaks a header must never carry. */
    private static String clean(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x20 && c != 0x7f) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String percentEncode(String text) {
        StringBuilder out = new StringBuilder();
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            int v = b & 0xff;
            boolean plain = (v >= 'a' && v <= 'z') || (v >= 'A' && v <= 'Z') || (v >= '0' && v <= '9')
                    || v == '-' || v == '.' || v == '_' || v == '~';
            if (plain) {
                out.append((char) v);
            } else {
                out.append('%').append(Character.toUpperCase(Character.forDigit(v >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(v & 0xf, 16)));
            }
        }
        return out.toString();
    }
}
