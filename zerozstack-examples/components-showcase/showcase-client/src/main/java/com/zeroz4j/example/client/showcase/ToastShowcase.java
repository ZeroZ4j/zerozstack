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

public class ToastShowcase extends ComponentShowcase {

    public ToastShowcase() {
        addTitle("Toast");
        addDescription("Toast is a wrapper to stack alert messages on the corner of the screen.");

        // Simple text toast
        Toast toastText = new Toast("Message sent successfully!");
        toastText.addClassName("relative");
        toastText.addClassName("z-50");

        // Toast with an Alert inside
        Toast toastAlert = new Toast();
        toastAlert.addClassName("relative");
        
        Alert alert = new Alert("New message received!");
        alert.addClassName("alert-info");
        toastAlert.add(alert);

        addSection("Simple Toast", toastText);
        addSection("Toast with Alert", toastAlert);
    }
}
