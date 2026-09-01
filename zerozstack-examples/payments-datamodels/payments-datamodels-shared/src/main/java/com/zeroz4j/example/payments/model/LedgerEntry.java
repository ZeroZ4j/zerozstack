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

import java.time.Instant;

/**
 * Something that happened at the till: either money taken, or money given back.
 *
 * <p>Two things are going on in this one class, and both are worth separating in your head.</p>
 *
 * <p><b>It is a base class, and that is why it exists.</b> A {@link Payment} and a {@link Refund}
 * each need an identifier, the moment it happened, the amount, and whatever the person at the till
 * wrote on it. That is four fields either way, so they live here once instead of twice. This is the
 * ordinary Java refactor of moving what two classes share up a level — and until 0.8.0 doing it
 * here quietly broke the application, because the fields on the base class stopped being sent and
 * nothing said so. They travel now.</p>
 *
 * <p><b>It is also sealed, and that is a separate decision.</b> A ledger entry is one of exactly
 * two things and there will never be a third, so the list of what may extend this is written down
 * and the compiler holds it. That is what lets {@code List<LedgerEntry>} come back from the server
 * with each row arriving as the real kind it is — a {@code Payment} or a {@code Refund} — instead
 * of as a base object with a flag on it that the screen has to interpret.</p>
 *
 * <p>Three rules apply to a sealed base and each is checked when you compile: the base is
 * {@code abstract}, everything it permits is {@code final}, and everything it permits is itself a
 * {@code @DataModel}. An abstract model is never a value in its own right — nothing can build one —
 * so it gets no code of its own generated. It exists to hand its fields down.</p>
 */
@DataModel
public abstract sealed class LedgerEntry permits Payment, Refund {

    private String id;
    private Instant recordedAt;
    private Money amount;
    private String note;

    /** @return the identifier the till gave this entry */
    public String getId() {
        return id;
    }

    /**
     * @param id the identifier the till gave this entry
     */
    public void setId(String id) {
        this.id = id;
    }

    /** @return when this happened */
    public Instant getRecordedAt() {
        return recordedAt;
    }

    /**
     * @param recordedAt when this happened
     */
    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    /** @return how much money moved */
    public Money getAmount() {
        return amount;
    }

    /**
     * @param amount how much money moved
     */
    public void setAmount(Money amount) {
        this.amount = amount;
    }

    /** @return whatever the person at the till wrote on it, possibly empty */
    public String getNote() {
        return note;
    }

    /**
     * @param note whatever the person at the till wants written on it
     */
    public void setNote(String note) {
        this.note = note;
    }
}
