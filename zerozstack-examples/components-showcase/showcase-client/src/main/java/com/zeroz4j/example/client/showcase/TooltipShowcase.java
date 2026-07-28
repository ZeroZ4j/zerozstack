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
import com.zeroz4j.ui.component.mixin.HasPositionVariant.Position;

public class TooltipShowcase extends ComponentShowcase {

    public TooltipShowcase() {
        addTitle("Tooltip");
        addDescription("Tooltip component shows a popup message when user hovers over an element.");

        // Basic Tooltip wrapping a button
        Tooltip basicTooltip = new Tooltip("This is a tooltip");
        Button btn = new Button("Hover Me");
        basicTooltip.add(btn);
        addSection("Basic Tooltip", basicTooltip);

        // Positions
        Tooltip tooltipTop = new Tooltip("Top").setPosition(Position.TOP);
        tooltipTop.add(new Button("Top"));

        Tooltip tooltipBottom = new Tooltip("Bottom").setPosition(Position.BOTTOM);
        tooltipBottom.add(new Button("Bottom"));

        Tooltip tooltipLeft = new Tooltip("Left").setPosition(Position.LEFT);
        tooltipLeft.add(new Button("Left"));

        Tooltip tooltipRight = new Tooltip("Right").setPosition(Position.RIGHT);
        tooltipRight.add(new Button("Right"));

        addSection("Positions", tooltipTop, tooltipBottom, tooltipLeft, tooltipRight);

        // Colors
        Tooltip tooltipPrimary = new Tooltip("Primary").setThemeColor(ThemeColor.PRIMARY);
        tooltipPrimary.add(new Button("Primary"));

        Tooltip tooltipSecondary = new Tooltip("Secondary").setThemeColor(ThemeColor.SECONDARY);
        tooltipSecondary.add(new Button("Secondary"));

        Tooltip tooltipAccent = new Tooltip("Accent").setThemeColor(ThemeColor.ACCENT);
        tooltipAccent.add(new Button("Accent"));

        Tooltip tooltipSuccess = new Tooltip("Success").setThemeColor(ThemeColor.SUCCESS);
        tooltipSuccess.add(new Button("Success"));

        Tooltip tooltipWarning = new Tooltip("Warning").setThemeColor(ThemeColor.WARNING);
        tooltipWarning.add(new Button("Warning"));

        Tooltip tooltipError = new Tooltip("Error").setThemeColor(ThemeColor.ERROR);
        tooltipError.add(new Button("Error"));

        addSection("Colors",
            tooltipPrimary, tooltipSecondary, tooltipAccent,
            tooltipSuccess, tooltipWarning, tooltipError
        );
    }
}
