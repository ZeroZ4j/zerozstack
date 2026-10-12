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

import io.vidocq.vauban.core.container.VaubanContainer;
import io.vidocq.vauban.core.context.RequestContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;

/**
 * Puts the standard {@link RequestContextController} over Vauban's own request context.
 *
 * <p>Vauban 0.3.0 has a request scope but offers no {@code RequestContextController} bean, and the
 * engine looks one up for every frame it handles. A bean in this module's archive supplies it.</p>
 *
 * <p>Framework-internal; a bean, so it must be public.</p>
 */
@ApplicationScoped
public class VidocqRequestContextController implements RequestContextController {

    @Override
    public boolean activate() {
        RequestContext context = VaubanContainer.current().requestContext();
        if (context.isActive()) {
            return false;
        }
        context.activate();
        return true;
    }

    @Override
    public void deactivate() {
        VaubanContainer.current().requestContext().deactivate();
    }
}
