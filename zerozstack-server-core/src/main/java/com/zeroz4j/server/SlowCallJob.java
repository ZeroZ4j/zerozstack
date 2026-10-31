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

/**
 * What the work of a slow call can say to the browser, and hear from it.
 *
 * @see SlowCallWork
 * @since 0.11.0
 */
public interface SlowCallJob {

    /**
     * Reports how far along the work is.
     *
     * @param percent 0 to 100
     *
     * <p><b>Under the hood:</b> the browser hears it on {@code 0x17 SIGNAL_UPD}, at most four times
     * a second. Calling this more often is safe, and the last value is always sent.</p>
     */
    void progress(int percent);

    /**
     * Reports how far along the work is and what it is doing.
     *
     * @param percent 0 to 100, or -1 when there is no number to give
     * @param text    a short line for the screen, or null
     *
     * <p><b>Under the hood:</b> the same as {@link #progress(int)}.</p>
     */
    void progress(int percent, String text);

    /**
     * Whether somebody asked this work to stop. Blocking calls also get an interrupt. Check this in
     * a loop that does not block.
     *
     * @return true once a cancel has arrived
     */
    boolean isCanceled();
}
