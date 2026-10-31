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
 * Where one slow call stands, as a screen sees it.
 *
 * @see SlowCall
 * @since 0.11.0
 */
public enum SlowCallStatus {

    /** The browser has not heard from the server yet, so it does not know. */
    CHECKING,

    /** The server has no slow call under this key for this browser or user. */
    IDLE,

    /** The work is running, or it finished and the value is still on its way to the browser. */
    RUNNING,

    /** The work finished and the value is here. */
    FINISHED,

    /** The work failed, or the server no longer has it. {@link SlowCallState#message()} says so. */
    FAILED,

    /** Somebody asked the work to stop. */
    CANCELED
}
