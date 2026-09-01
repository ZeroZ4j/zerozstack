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
 * An amount of money, in the smallest unit of its currency.
 *
 * <p>This is the plainest reason to write a {@code record}. An amount never changes: 2.50 euro is
 * 2.50 euro, and "editing" one means working out a different one. Nothing here needs a setter, and
 * the compiler writes {@code equals}, {@code hashCode} and {@code toString} so two amounts that are
 * the same really are equal — which matters, because this type is used as a value inside other
 * values and as a value in a map.</p>
 *
 * <p>Whole units are deliberately absent. Money kept as a {@code double} loses cents to rounding,
 * so the amount is a count of the smallest unit and the decimal point is only ever put back for
 * display.</p>
 *
 * @param cents    the amount, in the smallest unit of the currency
 * @param currency the ISO currency code, such as {@code EUR}
 */
@DataModel
public record Money(long cents, String currency) {

    /** The currency this example uses everywhere. One shop, one till, one currency. */
    public static final String CURRENCY = "EUR";

    /**
     * @param cents an amount in cents
     * @return that amount in this example's currency
     */
    public static Money of(long cents) {
        return new Money(cents, CURRENCY);
    }

    /** @return nothing at all, in this example's currency */
    public static Money zero() {
        return of(0);
    }

    /**
     * @param other the amount to add
     * @return the two amounts added together
     */
    public Money plus(Money other) {
        return other == null ? this : new Money(cents + other.cents, currency);
    }

    /**
     * @param count how many
     * @return this amount that many times over
     */
    public Money times(int count) {
        return new Money(cents * count, currency);
    }

    /**
     * @param other the amount to compare with
     * @return true when this amount is smaller than the other one
     */
    public boolean isLessThan(Money other) {
        return other != null && cents < other.cents;
    }

    /**
     * The amount written the way a receipt writes it.
     *
     * @return for example {@code 12.50 EUR}
     */
    public String formatted() {
        long whole = cents / 100;
        long fraction = Math.abs(cents % 100);
        String padded = fraction < 10 ? "0" + fraction : String.valueOf(fraction);
        return (cents < 0 && whole == 0 ? "-" : "") + whole + "." + padded + " " + currency;
    }
}
