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

import java.util.ArrayList;
import java.util.List;

/**
 * Money taken: what was bought, and how it was paid for.
 *
 * <p>This is the type that presses hardest on the wire, and it is worth saying why. One
 * {@code Payment} carries, all at once:</p>
 *
 * <ul>
 *   <li>four fields it inherits from {@link LedgerEntry}, one of which is itself a record;</li>
 *   <li>a list of {@link LineItem} records, each holding a {@link Money} record of its own;</li>
 *   <li>a {@link PaymentMethod}, which is a sealed value that may itself hold a record.</li>
 * </ul>
 *
 * <p>It travels in both directions. The screen builds one and sends it up to be recorded; the
 * server fills in the identifier, the time and the total, and sends the finished thing back. So
 * every one of those shapes is written and read on both tiers, which is the only way to find out
 * whether they really work.</p>
 *
 * <p>It is {@code final} because {@link LedgerEntry} is sealed, and it has a no-argument
 * constructor and setters because it is a class rather than a record.</p>
 */
@DataModel
public final class Payment extends LedgerEntry {

    private List<LineItem> lines = new ArrayList<>();
    private PaymentMethod method;

    /** @return what was bought */
    public List<LineItem> getLines() {
        return lines;
    }

    /**
     * @param lines what was bought
     */
    public void setLines(List<LineItem> lines) {
        this.lines = lines;
    }

    /** @return how the customer paid */
    public PaymentMethod getMethod() {
        return method;
    }

    /**
     * @param method how the customer paid
     */
    public void setMethod(PaymentMethod method) {
        this.method = method;
    }
}
