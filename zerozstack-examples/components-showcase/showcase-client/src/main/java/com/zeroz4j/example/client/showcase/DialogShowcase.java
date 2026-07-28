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

public class DialogShowcase extends ComponentShowcase {

    public DialogShowcase() {
        addTitle("Dialog");
        addDescription("Dialog (Modal) is used to draw attention to important workflows, confirmation steps, or extra detail forms.");

        Dialog dialog = new Dialog();
        
        Div title = new Div("Confirm Action");
        title.addClassName("text-lg font-bold mb-4");
        
        Div message = new Div("Are you sure you want to perform this operation? This action cannot be undone.");
        message.addClassName("py-4 text-base-content/85");
        
        dialog.add(title, message);

        Button closeBtn = new Button("Close");
        closeBtn.addClickListener(e -> dialog.close());
        
        Button confirmBtn = new Button("Confirm");
        confirmBtn.addClassName("btn-primary");
        confirmBtn.addClickListener(e -> dialog.close());

        dialog.addAction(closeBtn);
        dialog.addAction(confirmBtn);

        // Action button to open dialog
        Button openBtn = new Button("Open Dialog");
        openBtn.addClassName("btn-primary");
        openBtn.addClickListener(e -> dialog.open());

        addSection("Dialog Example", openBtn, dialog);
    }
}
