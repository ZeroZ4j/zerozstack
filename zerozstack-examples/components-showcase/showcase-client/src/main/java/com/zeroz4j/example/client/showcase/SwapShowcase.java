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

public class SwapShowcase extends ComponentShowcase {

    public SwapShowcase() {
        addTitle("Swap");
        addDescription("Swap allows toggling between two states, showing different elements.");

        // Text Swap
        Swap textSwap = new Swap();
        
        Div onText = new Div();
        onText.addClassName("swap-on");
        onText.getElement().setTextContent("VOLUME ON Ã°Å¸â€Å ");

        Div offText = new Div();
        offText.addClassName("swap-off");
        offText.getElement().setTextContent("VOLUME OFF Ã°Å¸â€â€¡");

        textSwap.getElement().appendChild(onText.getElement());
        textSwap.getElement().appendChild(offText.getElement());

        // Emoji Swap
        Swap emojiSwap = new Swap();
        emojiSwap.addClassName("swap-flip");
        emojiSwap.addClassName("text-6xl");

        Div onEmoji = new Div();
        onEmoji.addClassName("swap-on");
        onEmoji.getElement().setTextContent("Ã°Å¸ËœË†");

        Div offEmoji = new Div();
        offEmoji.addClassName("swap-off");
        offEmoji.getElement().setTextContent("Ã°Å¸Ëœâ€¡");

        emojiSwap.getElement().appendChild(onEmoji.getElement());
        emojiSwap.getElement().appendChild(offEmoji.getElement());

        addSection("Text Swap", textSwap);
        addSection("Flip Emoji Swap", emojiSwap);

        // Data Binding Demo
        ValueSignal<Boolean> signal = new ValueSignal<>(false);
        Swap component = new Swap();
        
        Div demoOnText = new Div();
        demoOnText.addClassName("swap-on");
        demoOnText.getElement().setTextContent("ON");

        Div demoOffText = new Div();
        demoOffText.addClassName("swap-off");
        demoOffText.getElement().setTextContent("OFF");

        component.getElement().appendChild(demoOnText.getElement());
        component.getElement().appendChild(demoOffText.getElement());

        component.bindValue(signal);
        Span output = new Span();
        output.bindText(new Computed<>(() -> "Current value: " + signal.get()));
        addSection("Data Binding Demo", component, output);
    }
}
