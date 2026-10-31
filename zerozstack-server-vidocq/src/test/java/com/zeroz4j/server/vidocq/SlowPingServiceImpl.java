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
package com.zeroz4j.server.vidocq;

import com.zeroz4j.api.SlowCall;
import com.zeroz4j.server.RmiRequestContext;
import com.zeroz4j.server.SlowCalls;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Test bean for {@link SlowPingService}: answers with the caller's name, from the work's thread. */
@ApplicationScoped
public class SlowPingServiceImpl implements SlowPingService {

    @Inject
    SlowCalls slowCalls;

    @Override
    public SlowCall<String> slowPing() {
        return slowCalls.start("slow-ping",
                job -> "pong " + RmiRequestContext.getPrincipal().getName());
    }
}
