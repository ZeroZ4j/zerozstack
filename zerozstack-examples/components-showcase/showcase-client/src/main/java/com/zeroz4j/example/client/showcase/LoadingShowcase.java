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

public class LoadingShowcase extends ComponentShowcase {

    public LoadingShowcase() {
        super();
        addTitle("Loading");
        addDescription("Loading is used to show a loading state for an action or page.");

        // Section 1: Types
        Loading spinner = new Loading();
        spinner.addClassName("loading-spinner");

        Loading ring = new Loading();
        ring.addClassName("loading-ring");

        Loading ball = new Loading();
        ball.addClassName("loading-ball");

        Loading bars = new Loading();
        bars.addClassName("loading-bars");

        Loading infinity = new Loading();
        infinity.addClassName("loading-infinity");

        Loading dots = new Loading();
        dots.addClassName("loading-dots");

        addSection("Loading Style Types", spinner, ring, ball, bars, infinity, dots);

        // Section 2: Colors
        Loading primary = new Loading().setThemeColor(ThemeColor.PRIMARY);
        primary.addClassName("loading-spinner");

        Loading secondary = new Loading().setThemeColor(ThemeColor.SECONDARY);
        secondary.addClassName("loading-spinner");

        Loading accent = new Loading().setThemeColor(ThemeColor.ACCENT);
        accent.addClassName("loading-spinner");

        Loading neutral = new Loading().setThemeColor(ThemeColor.NEUTRAL);
        neutral.addClassName("loading-spinner");

        Loading info = new Loading().setThemeColor(ThemeColor.INFO);
        info.addClassName("loading-spinner");

        Loading success = new Loading().setThemeColor(ThemeColor.SUCCESS);
        success.addClassName("loading-spinner");

        Loading warning = new Loading().setThemeColor(ThemeColor.WARNING);
        warning.addClassName("loading-spinner");

        Loading error = new Loading().setThemeColor(ThemeColor.ERROR);
        error.addClassName("loading-spinner");

        addSection("Color Variants", primary, secondary, accent, neutral, info, success, warning, error);

        // Section 3: Sizes
        Loading xs = new Loading().setThemeSize(ThemeSize.XS);
        xs.addClassName("loading-spinner");

        Loading sm = new Loading().setThemeSize(ThemeSize.SM);
        sm.addClassName("loading-spinner");

        Loading md = new Loading().setThemeSize(ThemeSize.MD);
        md.addClassName("loading-spinner");

        Loading lg = new Loading().setThemeSize(ThemeSize.LG);
        lg.addClassName("loading-spinner");

        addSection("Size Variants", xs, sm, md, lg);
    }
}
