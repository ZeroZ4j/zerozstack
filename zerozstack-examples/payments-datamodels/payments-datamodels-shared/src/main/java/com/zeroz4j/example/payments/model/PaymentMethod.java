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
 * How a customer paid: exactly one of four kinds, each carrying different details.
 *
 * <p>This is what a sealed interface is for. A card payment has a scheme and the last four digits.
 * Cash has the note the customer handed over, and therefore change. A bank transfer has a
 * reference and nothing else. A gift card has its number and what is left on it afterwards. There
 * is no field they share, so there is nothing to put in a common class — and there is no
 * open-ended set either, because a till that took a fifth kind of payment would need new code
 * anyway.</p>
 *
 * <p>The alternative, which this replaces, is a class with a {@code kind} string and every field
 * any kind might need, most of them null. Nothing checks that a "CARD" row really has its digits
 * filled in, and the day somebody adds a fifth kind, every place that switches on the string keeps
 * compiling and starts being wrong.</p>
 *
 * <p>Here the compiler holds the list. A method declared to take a {@code PaymentMethod} accepts
 * these four and nothing else, and what comes off the wire on the other side is the real kind — a
 * {@link CardPayment}, not a {@code PaymentMethod} that has to be interrogated. The receiver knows
 * the permitted list too, so a message naming any other type is turned away before that type is
 * built.</p>
 */
@DataModel
public sealed interface PaymentMethod
        permits CardPayment, CashPayment, BankTransfer, GiftCard {
}
