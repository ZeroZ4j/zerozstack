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
 * Paid with a gift card.
 *
 * <p>Carries the card number and what is left on it once this sale is taken off — the one thing a
 * customer always asks. Like {@link CashPayment} it holds a {@link Money}, so it too is a record
 * nested inside a record nested inside a sealed set.</p>
 *
 * @param cardNumber    the number printed on the card
 * @param remainingAfter what is left on the card once this sale is paid for
 */
@DataModel
public record GiftCard(String cardNumber, Money remainingAfter) implements PaymentMethod {
}
