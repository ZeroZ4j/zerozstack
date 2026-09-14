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
package com.zeroz4j.api;

/**
 * Thrown by an RMI call the server did not answer in time.
 *
 * <p>The browser client gives every call a deadline, 30 seconds unless the application set another
 * with {@code WasmRmiClient.setRequestTimeout}. A call still unanswered at its deadline is failed
 * with this exception by a timer of its own, so it fires whether or not anything else is arriving
 * on the connection.</p>
 *
 * <p>It is a different type from {@link DisconnectedException} on purpose. Both mean "this call
 * did not come back", and both are usually a connection problem rather than a fault in the server,
 * so something like the router's failure message treats them alike. But a timed-out call may still
 * be running on the server and may still complete there: the socket stayed open, only the answer
 * never arrived. Code that must not repeat a call that might have happened can tell the two
 * apart.</p>
 *
 * <p>Nothing is retried for you, exactly as with {@code DisconnectedException}.</p>
 *
 * <p>Before this type existed a timed-out call failed with a plain {@code RuntimeException}, which
 * this still is, so code catching that is unaffected.</p>
 */
public class RequestTimeoutException extends RuntimeException {

    /**
     * Creates the exception with a message naming the call and the deadline it missed.
     *
     * @param message human-readable description
     */
    public RequestTimeoutException(String message) {
        super(message);
    }
}
