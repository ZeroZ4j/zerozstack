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
package com.zeroz4j.example.server;

import com.zeroz4j.example.model.Registration;
import com.zeroz4j.example.model.Registration_Rules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RegistrationValidationTest {

    @Test
    public void testServerSideModelValidationRejectsInvalidRegistration() {
        // "A" fails @Size(min=2), "a" fails @Size(min=5), 99 fails @Max(50), "" fails @NotBlank, 450 chars fails @Size(max=400)
        Registration invalid = new Registration(1L, "A", "a", 99, "", false, "x".repeat(450));

        List<String> violations = Registration_Rules.validate(invalid);

        assertFalse(violations.isEmpty(), "Server-side validation rules must detect violations for invalid Registration");
        assertTrue(violations.stream().anyMatch(v -> v.contains("fullName")), "fullName size violation expected");
        assertTrue(violations.stream().anyMatch(v -> v.contains("email")), "email size violation expected");
        assertTrue(violations.stream().anyMatch(v -> v.contains("experienceYears")), "experienceYears max violation expected");
        assertTrue(violations.stream().anyMatch(v -> v.contains("tShirtSize")), "tShirtSize not blank violation expected");
        assertTrue(violations.stream().anyMatch(v -> v.contains("bio")), "bio size violation expected");
    }

    @Test
    public void testServerSideModelValidationAcceptsValidRegistration() {
        Registration valid = new Registration(2L, "Jane Doe", "jane.doe@example.com", 8, "M", true, "Senior Java Developer");

        List<String> violations = Registration_Rules.validate(valid);

        assertTrue(violations.isEmpty(), "Valid registration must have zero violations");
    }
}
