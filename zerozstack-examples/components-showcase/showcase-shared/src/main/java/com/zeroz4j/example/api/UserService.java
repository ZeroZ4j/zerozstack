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
package com.zeroz4j.example.api;

import com.zeroz4j.example.model.UserInfo;
import com.zeroz4j.api.RmiService;
import com.zeroz4j.api.RolesAllowed;
import com.zeroz4j.api.Secured;

/**
 * Example remote service interface with security annotations.
 * All methods require authentication. The systemDiagnostics
 * method is restricted to the 'admin' role.
 */
@RmiService
@Secured
public interface UserService {
    /**
     * Retrieves user profile details.
     */
    UserInfo getUserInfo(String username);

    /**
     * Updates the user's score on the backend.
     */
    void updateScore(String username, int newScore);

    /**
     * Triggers backend system diagnostics - admin only.
     */
    @RolesAllowed("admin")
    String systemDiagnostics(String category, int scanDepth);
}

