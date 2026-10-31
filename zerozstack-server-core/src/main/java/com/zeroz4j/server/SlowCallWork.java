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
 * The work of one slow call. It runs on a thread of its own, so it may block for as long as it
 * needs: a remote model, a report, an import.
 *
 * <p>Returns the value the browser receives. Throw {@link ClientVisibleException} to fail with a
 * sentence the person should read. Anything else reaches the browser as "The server could not
 * complete this request. Reference: &lt;code&gt;", with the real message in the log under that
 * code.</p>
 *
 * @param <T> what the work returns: anything that may cross the wire
 * @see SlowCalls#start(String, SlowCallWork)
 * @since 0.11.0
 */
@FunctionalInterface
public interface SlowCallWork<T> {

    /**
     * @param job reports progress and says whether somebody asked to stop
     * @return the value
     * @throws Exception when the work fails
     */
    T run(SlowCallJob job) throws Exception;
}
