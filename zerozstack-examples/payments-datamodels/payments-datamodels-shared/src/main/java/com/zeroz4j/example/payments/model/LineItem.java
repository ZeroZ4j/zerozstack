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
package com.zeroz4j.example.payments.model;

import com.zeroz4j.api.DataModel;

/**
 * One line on a receipt: what was bought, how many, and what each one costs.
 *
 * <p>A record again, and for the same reason: once the line is on the receipt it does not change.
 * Correcting a mistake means taking the line off and putting a different one on, which is what the
 * screen does.</p>
 *
 * <p>It also carries a {@link Money}, so this is a record inside a record. That nesting is the
 * thing worth watching: it goes over the wire in both directions, and the inner value has to come
 * back as a real {@code Money} rather than as two loose numbers.</p>
 *
 * @param description what was sold
 * @param quantity    how many of it
 * @param unitPrice   the price of one
 */
@DataModel
public record LineItem(String description, int quantity, Money unitPrice) {

    /**
     * @return what this line comes to
     */
    public Money total() {
        return unitPrice == null ? Money.zero() : unitPrice.times(quantity);
    }
}
