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

import com.zeroz4j.ui.component.Alert;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.theme.TextStyle;

public class TextStyleShowcase extends ComponentShowcase {

    public TextStyleShowcase() {
        addTitle("Type scale");
        addDescription("Five sizes of text, by name. Ask for one instead of describing it, and "
            + "two screens written months apart still look like the same product.");

        Div scale = new Div();
        scale.addClassName("flex flex-col gap-5 w-full");
        for (TextStyle style : TextStyle.values()) {
            scale.add(sample(style));
        }
        addSection("The five sizes", scale);

        Div page = new Div();
        page.addClassName("flex flex-col gap-2 w-full");
        page.add(
            TextStyle.PAGE_TITLE.paragraph("Deliveries"),
            TextStyle.SECONDARY.paragraph("Nineteen stops left, updated a moment ago"),
            TextStyle.SECTION_TITLE.paragraph("This afternoon"),
            TextStyle.BODY.paragraph("Every stop between now and six o'clock, in the order the "
                + "van will reach them. Drag a row to move it."),
            TextStyle.CAPTION.paragraph("Times are estimates and change as the van moves."));
        addSection("What a page made of them looks like", page);

        Div onTint = new Div();
        onTint.addClassName("flex flex-col gap-3 w-full");
        Alert notice = Alert.caution("Two stops could not be planned.")
            .withHeading("Some addresses were not recognised");
        notice.getElement().appendChild(
            TextStyle.CAPTION.paragraph("The two are at the bottom of the list.").getElement());
        onTint.add(notice, TextStyle.CAPTION.paragraph(
            "The quiet sizes fade whatever colour they are sitting on rather than naming a grey, "
                + "so the same words are right on a page, on a tinted notice and on a dark "
                + "background."));
        addSection("Quiet text is a fade, not a colour", onTint);
    }

    private Div sample(TextStyle style) {
        Div row = new Div();
        row.addClassName("flex flex-col gap-1 w-full");
        row.add(style.paragraph("The quick brown fox jumps over the lazy dog"));
        Div name = TextStyle.CAPTION.paragraph("TextStyle." + style.name()
            + "   —   " + style.getClassNames());
        name.addClassName("font-mono");
        row.add(name);
        return row;
    }
}
