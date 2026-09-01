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
 * Money given back, against a payment the till already took.
 *
 * <p>It has one field of its own and inherits four more. That ratio is the argument for the
 * base class: without it the identifier, the time, the amount and the note would be written out
 * here a second time, and the two copies would drift.</p>
 */
@DataModel
public final class Refund extends LedgerEntry {

    private String againstPaymentId;

    /** @return the identifier of the payment this gives money back against */
    public String getAgainstPaymentId() {
        return againstPaymentId;
    }

    /**
     * @param againstPaymentId the identifier of the payment this gives money back against
     */
    public void setAgainstPaymentId(String againstPaymentId) {
        this.againstPaymentId = againstPaymentId;
    }
}
