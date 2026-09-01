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
package com.zeroz4j.example.payments.api;

import com.zeroz4j.api.RmiService;
import com.zeroz4j.example.payments.model.DailySummary;
import com.zeroz4j.example.payments.model.LedgerEntry;
import com.zeroz4j.example.payments.model.LineItem;
import com.zeroz4j.example.payments.model.Money;
import com.zeroz4j.example.payments.model.Payment;
import com.zeroz4j.example.payments.model.Refund;

import java.util.List;

/**
 * The till.
 *
 * <p>Five methods, chosen so that every shape a wire type can take is sent as an argument and
 * received as a return value, nested and in collections. What each one exercises is written on
 * it.</p>
 */
@RmiService
public interface PaymentsService {

    /**
     * Everything the till has recorded, oldest first.
     *
     * <p><b>Down the wire:</b> a list whose declared element type is an abstract sealed base. Each
     * row arrives as the kind it really is, so the screen can ask {@code instanceof Payment}
     * instead of reading a flag. Each payment brings its lines, its method and the four fields it
     * inherits.</p>
     *
     * @return every entry, oldest first
     */
    List<LedgerEntry> ledger();

    /**
     * What a basket of items comes to.
     *
     * <p><b>Up the wire:</b> records inside a collection, as a call argument.
     * <b>Down:</b> a single record.</p>
     *
     * @param lines what is in the basket
     * @return the total
     */
    Money quote(List<LineItem> lines);

    /**
     * Records a payment.
     *
     * <p><b>Up the wire:</b> a class extending an abstract model, carrying inherited fields, a list
     * of records and a sealed value all in one object. The screen fills in the lines, the method and
     * the note; the till fills in the identifier, the time and the total.</p>
     *
     * <p><b>Down:</b> the same object, finished.</p>
     *
     * @param proposed the payment to record: lines, method and note filled in
     * @return the recorded payment, with its identifier, time and total
     */
    Payment take(Payment proposed);

    /**
     * Gives back everything taken on one payment.
     *
     * @param paymentId which payment to refund
     * @param note      why
     * @return the refund that was recorded
     */
    Refund refund(String paymentId, String note);

    /**
     * The running totals.
     *
     * <p><b>Down the wire:</b> a record holding a map of records.</p>
     *
     * @return the totals so far
     */
    DailySummary summary();
}
