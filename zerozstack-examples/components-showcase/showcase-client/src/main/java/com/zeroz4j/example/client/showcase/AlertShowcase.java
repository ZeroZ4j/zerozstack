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

public class AlertShowcase extends ComponentShowcase {

    public AlertShowcase() {
        addTitle("Alert");
        addDescription("Alerts are used to display important messages and status updates to the user.");

        // Theme colors
        Alert primary = new Alert("Primary alert message").setThemeColor(ThemeColor.PRIMARY);
        Alert secondary = new Alert("Secondary alert message").setThemeColor(ThemeColor.SECONDARY);
        Alert accent = new Alert("Accent alert message").setThemeColor(ThemeColor.ACCENT);
        Alert neutral = new Alert("Neutral alert message").setThemeColor(ThemeColor.NEUTRAL);
        Alert info = new Alert("Info: New update is available.").setThemeColor(ThemeColor.INFO);
        Alert success = new Alert("Success: Your profile has been updated!").setThemeColor(ThemeColor.SUCCESS);
        Alert warning = new Alert("Warning: Disk space is running low.").setThemeColor(ThemeColor.WARNING);
        Alert error = new Alert("Error: Failed to save changes.").setThemeColor(ThemeColor.ERROR);

        addSection("Alert Colors", primary, secondary, accent, neutral, info, success, warning, error);
    }
}
