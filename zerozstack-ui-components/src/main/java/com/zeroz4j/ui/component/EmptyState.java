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
package com.zeroz4j.ui.component;

import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.layout.Div;

/** Friendly zero-data placeholder: big glyph, title, hint, optional primary action. */
public final class EmptyState extends Div {

    public EmptyState(String icon, String title, String hint) {
        addClassName("flex flex-col items-center justify-center gap-2 py-12 px-6 text-center "
            + "text-base-content/60");
        Icon glyph = Icon.of(icon, "w-12 h-12 opacity-30");
        Div titleDiv = new Div(title);
        titleDiv.addClassName("font-semibold text-base-content/80");
        Div hintDiv = new Div(hint);
        hintDiv.addClassName("text-sm max-w-md");
        add(glyph, titleDiv, hintDiv);
    }

    public EmptyState withAction(String label, Runnable action) {
        Button button = new Button(label, e -> action.run());
        button.addClassName("btn-primary btn-sm mt-2");
        add(button);
        return this;
    }
}

