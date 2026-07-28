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

public class LinkShowcase extends ComponentShowcase {

    public LinkShowcase() {
        super();
        addTitle("Link");
        addDescription("Link is an anchor tag styled with theme colors and hover animations.");

        // Section 1: Default Link
        Link defaultLink = new Link();
        defaultLink.setText("Default Link");
        defaultLink.getElement().setAttribute("href", "javascript:void(0)");

        Link hoverLink = new Link();
        hoverLink.setText("Hover Underline Only");
        hoverLink.addClassName("link-hover");
        hoverLink.getElement().setAttribute("href", "javascript:void(0)");

        addSection("Basic Link Options", defaultLink, hoverLink);

        // Section 2: Colors
        Link primary = new Link().setThemeColor(ThemeColor.PRIMARY);
        primary.setText("Primary Link");
        primary.getElement().setAttribute("href", "javascript:void(0)");

        Link secondary = new Link().setThemeColor(ThemeColor.SECONDARY);
        secondary.setText("Secondary Link");
        secondary.getElement().setAttribute("href", "javascript:void(0)");

        Link accent = new Link().setThemeColor(ThemeColor.ACCENT);
        accent.setText("Accent Link");
        accent.getElement().setAttribute("href", "javascript:void(0)");

        Link neutral = new Link().setThemeColor(ThemeColor.NEUTRAL);
        neutral.setText("Neutral Link");
        neutral.getElement().setAttribute("href", "javascript:void(0)");

        Link info = new Link().setThemeColor(ThemeColor.INFO);
        info.setText("Info Link");
        info.getElement().setAttribute("href", "javascript:void(0)");

        Link success = new Link().setThemeColor(ThemeColor.SUCCESS);
        success.setText("Success Link");
        success.getElement().setAttribute("href", "javascript:void(0)");

        Link warning = new Link().setThemeColor(ThemeColor.WARNING);
        warning.setText("Warning Link");
        warning.getElement().setAttribute("href", "javascript:void(0)");

        Link error = new Link().setThemeColor(ThemeColor.ERROR);
        error.setText("Error Link");
        error.getElement().setAttribute("href", "javascript:void(0)");

        addSection("Color Variants", primary, secondary, accent, neutral, info, success, warning, error);
    }
}
