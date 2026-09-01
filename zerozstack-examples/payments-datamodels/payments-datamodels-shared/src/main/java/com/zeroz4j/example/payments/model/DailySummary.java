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

import java.util.Map;

/**
 * What the till has done so far: totals, and a breakdown by how people paid.
 *
 * <p>A record because it is worked out fresh every time it is asked for and is never edited. It is
 * also the awkward shape on purpose — a record holding a map whose values are records, sent as a
 * return value. That combination is where a serializer usually falls over, so the screen shows it
 * and any failure is visible rather than theoretical.</p>
 *
 * @param taken      everything the till has taken
 * @param refunded   everything it has given back
 * @param byMethod   how much came in through each kind of payment, keyed by the kind's name
 * @param entryCount how many entries the ledger holds
 */
@DataModel
public record DailySummary(Money taken, Money refunded, Map<String, Money> byMethod,
                           int entryCount) {

    /** @return what the till is actually up, once refunds are taken off */
    public Money net() {
        return new Money(taken.cents() - refunded.cents(), taken.currency());
    }
}
