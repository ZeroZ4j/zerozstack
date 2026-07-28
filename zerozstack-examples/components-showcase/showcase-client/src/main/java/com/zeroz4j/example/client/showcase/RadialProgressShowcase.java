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

public class RadialProgressShowcase extends ComponentShowcase {

    public RadialProgressShowcase() {
        super();
        addTitle("RadialProgress");
        addDescription("RadialProgress displays progress values in a circular format, configured via custom CSS properties.");

        // Section 1: Standard Values
        RadialProgress r1 = new RadialProgress("20%");
        r1.setStyle("--value", "20");

        RadialProgress r2 = new RadialProgress("60%");
        r2.setStyle("--value", "60");

        RadialProgress r3 = new RadialProgress("90%");
        r3.setStyle("--value", "90");

        addSection("Standard Progress Circle", r1, r2, r3);

        // Section 2: Colorful (using text utility classes)
        RadialProgress primary = new RadialProgress("45%");
        primary.setStyle("--value", "45");
        primary.addClassName("text-primary");

        RadialProgress secondary = new RadialProgress("55%");
        secondary.setStyle("--value", "55");
        secondary.addClassName("text-secondary");

        RadialProgress success = new RadialProgress("85%");
        success.setStyle("--value", "85");
        success.addClassName("text-success");

        RadialProgress error = new RadialProgress("15%");
        error.setStyle("--value", "15");
        error.addClassName("text-error");

        addSection("Colors (Text Helpers)", primary, secondary, success, error);

        // Section 3: Custom Size & Thickness
        RadialProgress custom = new RadialProgress("70%");
        custom.setStyle("--value", "70");
        custom.setStyle("--size", "8rem");
        custom.setStyle("--thickness", "12px");
        custom.addClassName("text-accent");

        addSection("Custom Size and Thickness", custom);
    }
}
