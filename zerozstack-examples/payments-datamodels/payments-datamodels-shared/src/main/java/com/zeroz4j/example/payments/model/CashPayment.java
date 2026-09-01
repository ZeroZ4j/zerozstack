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
 * Paid in cash.
 *
 * <p>This kind carries something none of the others do: what the customer handed over, which is
 * what change is worked out from. A single class covering all four kinds would have had this field
 * sitting empty on three of them.</p>
 *
 * <p>It is also a {@link Money} inside a record inside a sealed set, so this one value is three
 * levels of nesting deep by the time it reaches the wire.</p>
 *
 * @param handedOver the note or coins the customer gave
 */
@DataModel
public record CashPayment(Money handedOver) implements PaymentMethod {

    /**
     * @param due what the customer owed
     * @return the change to give back
     */
    public Money changeFrom(Money due) {
        if (handedOver == null || due == null) {
            return Money.zero();
        }
        return new Money(handedOver.cents() - due.cents(), handedOver.currency());
    }
}
