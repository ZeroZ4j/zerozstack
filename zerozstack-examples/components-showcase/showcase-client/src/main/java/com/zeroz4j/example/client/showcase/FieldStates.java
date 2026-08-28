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
package com.zeroz4j.example.client.showcase;

import com.zeroz4j.ui.component.AbstractField;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;

/**
 * The states every field really has, written once instead of seven times. A gallery that shows
 * only the happy field is the gallery that let a whole set of missing error messages through.
 */
final class FieldStates {

    /** A caption long enough to find out what a page does when a caption does not fit. */
    static final String LONG_CAPTION =
            "The full legal name of the organisation exactly as it appears on the commercial "
            + "register extract, including any suffix such as GmbH, AG or e. K.";

    private FieldStates() {
    }

    /** Stacks the states down the page with a line above each saying which state it is. */
    static Div stack(Component... rows) {
        Div host = new Div();
        host.addClassName("flex flex-col gap-6 w-full");
        host.add(rows);
        return host;
    }

    /** One state: a short line saying what is being shown, then the field itself. */
    static Div labelled(String what, Component field) {
        Div row = new Div();
        row.addClassName("flex flex-col gap-1 w-full min-w-0");
        Span caption = new Span(what);
        caption.addClassName("text-xs uppercase tracking-wide text-base-content/50");
        row.add(caption, field);
        return row;
    }

    /** Marks a field as wrong, with the sentence saying why. */
    static <F extends AbstractField<?, ?>> F wrong(F field, String message) {
        field.setErrorMessage(message);
        return field;
    }

    /**
     * Read-only rather than disabled. The difference matters: a disabled control is taken out of
     * the keyboard's order and never read out, so a value nobody may change but everybody must be
     * able to read has to be read-only, not disabled.
     */
    static <F extends AbstractField<?, ?>> F readOnly(F field) {
        field.getElement().setAttribute("readonly", "true");
        field.getElement().setAttribute("aria-readonly", "true");
        return field;
    }
}
