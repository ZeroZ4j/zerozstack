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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a {@link LiveSync} {@link DataModel} class into two-way synchronization: setter
 * calls on the client's live instance propagate to the server automatically — no service
 * method, no explicit save.
 *
 * <p>The server remains authoritative. Every client mutation is checked against this
 * annotation, the declared write roles, and the model's validation annotations before it
 * is applied and re-broadcast; rejected mutations answer the writer with a corrective
 * sync that snaps its instance back to server truth. Concurrent unlocked writes are
 * last-write-wins — serialize editors with {@code LiveMutex} where that matters.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Setter Contract:</b> Mutation tracking rides APT-generated setter overrides, so
 *       tracked fields need setters, and clients must mutate through them. Collection
 *       contents changed in place (e.g. {@code getTags().add(...)}) are not observed —
 *       reassign via the setter or call {@code LiveMutationTracker.touch(obj)}.</li>
 *   <li><b>Security Default:</b> Without this annotation, client mutations of a live
 *       object are ignored — deny by default.</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ClientWritable {

    /** Roles allowed to mutate from a client; empty allows any session (subject to validation). */
    String[] value() default {};
}
