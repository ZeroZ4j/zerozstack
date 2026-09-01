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
package com.zeroz4j.server.test;

import com.zeroz4j.api.i18n.Message;
import com.zeroz4j.server.CarriesClientMessage;

/**
 * Asserting on <em>which</em> refusal happened rather than on the sentence it produced.
 *
 * <p>A refusal's words are a translation now, so they depend on who was asking. Its name does not.
 * A test that used to read</p>
 *
 * <pre>{@code
 * assertEquals("Access denied: requires role [approver] but user has []", thrown.getMessage());
 * }</pre>
 *
 * <p>becomes</p>
 *
 * <pre>{@code
 * Refusals.assertRefusedWith(FrameworkKeys.ACCESS_DENIED, thrown);
 * }</pre>
 *
 * <p>which is what it should have been asserting on all along: the sentence is wording and wording
 * is allowed to be improved, while the name is the contract.</p>
 *
 * <p>Nothing forces this. English is still the fallback and is unchanged, so a test asserting on
 * the English sentence of a deployment that added no language still passes.</p>
 *
 * @since 0.9.0
 */
public final class Refusals {

    private Refusals() {
    }

    /**
     * Fails unless the failure is the named refusal.
     *
     * @param expectedKey the refusal's name, from {@code FrameworkKeys} or an application catalog
     * @param thrown      what was caught
     */
    public static void assertRefusedWith(String expectedKey, Throwable thrown) {
        if (expectedKey == null) {
            throw new AssertionError("assertRefusedWith needs a key to look for.");
        }
        if (thrown == null) {
            throw new AssertionError("Expected the refusal " + expectedKey
                    + ", but nothing was thrown.");
        }
        Message carried = messageOf(thrown);
        if (carried == null) {
            throw new AssertionError("Expected the refusal " + expectedKey + ", but "
                    + thrown.getClass().getName() + " carries no message to name - it was thrown"
                    + " with a plain sentence: " + thrown.getMessage());
        }
        if (!expectedKey.equals(carried.key())) {
            throw new AssertionError("Expected the refusal " + expectedKey + " but got "
                    + carried.key() + " (" + thrown.getMessage() + ").");
        }
    }

    /**
     * The refusal a failure is carrying, if it is carrying one.
     *
     * @param thrown what was caught
     * @return the message, or null when it was thrown with a plain sentence
     */
    public static Message messageOf(Throwable thrown) {
        for (Throwable at = thrown; at != null; at = at.getCause() == at ? null : at.getCause()) {
            if (at instanceof CarriesClientMessage) {
                Message carried = ((CarriesClientMessage) at).clientMessage();
                if (carried != null) {
                    return carried;
                }
            }
        }
        return null;
    }
}
