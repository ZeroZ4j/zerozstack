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
package com.zeroz4j.ui.binding;

import java.util.function.Predicate;

public interface BindingBuilder<BEAN, FIELDVALUE> {

    /**
     * Adds a generated model rule as a validator, so constraints declared once with
     * {@code @NotBlank}, {@code @Min}, {@code @Max} or {@code @Size} on a {@code @DataModel} field can
     * be reused here instead of being restated by hand.
     *
     * <pre>{@code
     * binder.forField(emailField)
     *       .withRule(Registration_Rules.email())
     *       .bind(Registration::getEmail, Registration::setEmail);
     * }</pre>
     *
     * <p>The same generated rule is enforced independently by the server, so this is user feedback;
     * the server's answer is the one that counts.</p>
     *
     * @param rule a generated {@code <Model>_Rules} field rule
     * @return this builder
     */
    BindingBuilder<BEAN, FIELDVALUE> withRule(com.zeroz4j.api.validation.FieldRule<? super FIELDVALUE> rule);
    BindingBuilder<BEAN, FIELDVALUE> withValidator(Validator<? super FIELDVALUE> validator);
    
    default BindingBuilder<BEAN, FIELDVALUE> withValidator(Predicate<? super FIELDVALUE> predicate, String errorMessage) {
        return withValidator(Validator.from(predicate::test, errorMessage));
    }
    
    BindingBuilder<BEAN, FIELDVALUE> asRequired(String errorMessage);
    
    Binding<BEAN, FIELDVALUE> bind(ValueProvider<BEAN, FIELDVALUE> getter, Setter<BEAN, FIELDVALUE> setter);
}
