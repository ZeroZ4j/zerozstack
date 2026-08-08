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
package com.zeroz4j.example.oidclogin.server;

import com.zeroz4j.example.oidclogin.api.IdentityService;
import com.zeroz4j.server.RmiRequestContext;
import jakarta.enterprise.context.ApplicationScoped;

/** Reports the identity the OIDC provider established, as the server sees it. */
@ApplicationScoped
public class IdentityServiceImpl implements IdentityService {

    @Override
    public String publicGreeting() {
        return "This call needs no identity at all.";
    }

    @Override
    public String whoAmI() {
        return "name=" + RmiRequestContext.getPrincipal().getName()
                + "\nroles=" + RmiRequestContext.getRoles()
                + "\ntenant=" + (RmiRequestContext.getTenantId() == null
                        ? "none (single-tenant)" : RmiRequestContext.getTenantId())
                + "\nclientId=" + RmiRequestContext.getClientId();
    }

    @Override
    public String plannerOnly() {
        return "Reached because Keycloak granted the realm role 'planner' to "
                + RmiRequestContext.getPrincipal().getName() + ".";
    }
}
