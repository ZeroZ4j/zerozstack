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
import java.io.InputStream;

/**
 * The only thing {@link UploadReceiver} needs from an HTTP request, so that it needs no HTTP type.
 *
 * <p>There are two bindings — a JAX-RS resource and a servlet — and they must not be able to drift.
 * That is why this interface asks for headers <em>by name on demand</em> rather than taking them as
 * arguments: <b>which</b> headers matter, and what each one means, is decided once inside the
 * receiver. A binding cannot forget to pass the origin, cannot read a size limit differently, and
 * cannot invent its own name for the pass header, because it never sees any of those decisions.</p>
 *
 * <p>Each binding is a lambda over its own request object and nothing more.</p>
 *
 * <p>Framework-internal.</p>
 */
public interface UploadRequest {

    /**
     * One request header.
     *
     * @param name the header name; matched case-insensitively, as HTTP requires
     * @return the value, or null when the request carried no such header
     */
    String header(String name);

    /**
     * The request body, read once and never buffered whole.
     *
     * @return the stream of bytes the browser is sending
     * @throws IOException when the container cannot supply it
     */
    InputStream body() throws IOException;
}
