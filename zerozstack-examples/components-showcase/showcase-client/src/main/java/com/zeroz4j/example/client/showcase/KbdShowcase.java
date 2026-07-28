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

import com.zeroz4j.ui.component.*;
import com.zeroz4j.ui.layout.*;
import com.zeroz4j.ui.theme.*;
import com.zeroz4j.signals.*;

public class KbdShowcase extends ComponentShowcase {

    public KbdShowcase() {
        super();
        addTitle("Kbd");
        addDescription("Kbd is used to display keyboard keys or key combinations.");

        // Section 1: Standard Keys
        Kbd kbdCmd = new Kbd("Ã¢Å’Ëœ");
        Kbd kbdShift = new Kbd("Ã¢â€¡Â§");
        Kbd kbdOption = new Kbd("Ã¢Å’Â¥");
        Kbd kbdControl = new Kbd("Ã¢Å’Æ’");
        Kbd kbdA = new Kbd("A");
        Kbd kbdEsc = new Kbd("esc");

        addSection("Standard Keys", kbdCmd, kbdShift, kbdOption, kbdControl, kbdA, kbdEsc);

        // Section 2: Keyboard Combinations
        Div shortcut = new Div();
        shortcut.addClassName("flex");
        shortcut.addClassName("items-center");
        shortcut.addClassName("gap-1");

        Kbd k1 = new Kbd("ctrl");
        Span plus = new Span("+");
        Kbd k2 = new Kbd("shift");
        Span plus2 = new Span("+");
        Kbd k3 = new Kbd("del");

        shortcut.add(k1, plus, k2, plus2, k3);

        addSection("Shortcut Combination", shortcut);

        // Section 3: Sizes (kbd-xs, kbd-sm, kbd-md, kbd-lg)
        Kbd xs = new Kbd("A"); xs.addClassName("kbd-xs");
        Kbd sm = new Kbd("A"); sm.addClassName("kbd-sm");
        Kbd md = new Kbd("A"); md.addClassName("kbd-md");
        Kbd lg = new Kbd("A"); lg.addClassName("kbd-lg");

        addSection("Size Modifier Classes", xs, sm, md, lg);
    }
}
