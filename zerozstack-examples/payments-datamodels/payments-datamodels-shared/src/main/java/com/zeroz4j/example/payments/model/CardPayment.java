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
 * Paid by card.
 *
 * <p>A record, and {@code final} because everything in a sealed set has to be: the receiving side
 * checks an arriving type against a fixed list, and a type somebody could extend from outside would
 * defeat that. A record is final already, which is one reason records and sealed sets fit together
 * so well.</p>
 *
 * <p>Only the last four digits are here. A till never needs the rest, so it never holds it.</p>
 *
 * @param scheme the card network, such as {@code Visa}
 * @param last4  the last four digits printed on the card
 */
@DataModel
public record CardPayment(String scheme, String last4) implements PaymentMethod {
}
