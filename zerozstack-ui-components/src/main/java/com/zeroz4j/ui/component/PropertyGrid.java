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

import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import com.zeroz4j.ui.component.Component;

/**
 * Two-column key/value inspector grid. Values are monospace and copy on click ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â
 * the standard way ids, paths, and hashes are shown everywhere in the Console.
 */
public final class PropertyGrid extends Div {

    public PropertyGrid() {
        addClassName("grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm items-baseline");
    }

    public PropertyGrid row(String key, String value) {
        Span keySpan = new Span(key);
        keySpan.addClassName("text-base-content/60 whitespace-nowrap");
        Span valueSpan = new Span(value == null ? "ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â" : value);
        valueSpan.addClassName("font-mono text-xs break-all cursor-pointer hover:text-primary");
        valueSpan.getElement().setAttribute("title", "click to copy");
        valueSpan.getElement().addEventListener("click", threaded(e -> {
            Js.copyToClipboard(value == null ? "" : value);
            valueSpan.addClassName("text-success");
        }));
        add(keySpan, valueSpan);
        return this;
    }

    /** A row whose value is an arbitrary component (badges, dots, meters). */
    public PropertyGrid row(String key, Component value) {
        Span keySpan = new Span(key);
        keySpan.addClassName("text-base-content/60 whitespace-nowrap");
        Div holder = new Div();
        holder.add(value);
        add(keySpan, holder);
        return this;
    }
}

