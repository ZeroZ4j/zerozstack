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

import com.zeroz4j.ui.theme.Emphasis;
import com.zeroz4j.ui.theme.TextStyle;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import com.zeroz4j.ui.component.Component;

/**
 * Two-column key/value inspector grid. Values are monospace and each one has a small copy
 * button beside it — the standard way ids, paths, and hashes are shown everywhere in the Console.
 */
public final class PropertyGrid extends Div {

    public PropertyGrid() {
        addClassName("grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 items-baseline "
            + TextStyle.SECONDARY.getClassNames(Emphasis.FULL));
    }

    /**
     * Adds one row: a name on the left, a value on the right, and a small button that copies it.
     *
     * <p>The value used to be the button - clicking the text copied it, and a tip said so. That
     * only ever worked with a mouse: the tip never appeared for anybody else, and the text could
     * not be reached with Tab or pressed with Enter. So the value is now ordinary text, which can
     * be read and selected as text should be, and the copying is a real button beside it named
     * "Copy" and then the name of the row, so it is clear which value it copies.</p>
     */
    public PropertyGrid row(String key, String value) {
        Span keySpan = new Span(key);
        keySpan.addClassName(Emphasis.FAINT.getClassNames() + " whitespace-nowrap");
        Span valueSpan = new Span(value == null ? "—" : value);
        valueSpan.addClassName(TextStyle.CAPTION.getClassNames(Emphasis.FULL)
                + " font-mono break-all");

        Button copyButton = new Button(Icon.of("copy", "w-3 h-3"), "Copy " + key);
        copyButton.setClassName("btn btn-ghost btn-xs btn-circle shrink-0 opacity-40 "
            + "hover:opacity-100");
        copyButton.getElement().setAttribute("type", "button");
        copyButton.getElement().addEventListener("click", threaded(e -> {
            Js.copyToClipboard(value == null ? "" : value);
            valueSpan.addClassName("text-success");
        }));

        Div valueCell = new Div();
        valueCell.addClassName("flex items-baseline gap-1 min-w-0");
        valueCell.add(valueSpan, copyButton);
        add(keySpan, valueCell);
        return this;
    }

    /** A row whose value is an arbitrary component (badges, dots, meters). */
    public PropertyGrid row(String key, Component value) {
        Span keySpan = new Span(key);
        keySpan.addClassName(Emphasis.FAINT.getClassNames() + " whitespace-nowrap");
        Div holder = new Div();
        holder.add(value);
        add(keySpan, holder);
        return this;
    }
}

