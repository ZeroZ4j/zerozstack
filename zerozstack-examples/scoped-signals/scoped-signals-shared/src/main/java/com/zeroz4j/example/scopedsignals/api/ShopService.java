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
package com.zeroz4j.example.scopedsignals.api;

import com.zeroz4j.api.RmiService;

/**
 * Actions that change the signals. Every one of them decides <em>whose</em> state it is changing on
 * the server, from the connection's own identity — never from anything the client sends.
 */
@RmiService
public interface ShopService {

    /** Adds an item to the calling browser's basket. */
    void addToBasket(String item);

    /** Empties the calling browser's basket. */
    void clearBasket();

    /** Sends a notice to every tab of the calling user. */
    void noticeMyself(String message);

    /** Bumps the global visitor count everyone shares. */
    void countVisitor();

    /** @return a description of who the server thinks this connection is */
    String whoAmI();
}
