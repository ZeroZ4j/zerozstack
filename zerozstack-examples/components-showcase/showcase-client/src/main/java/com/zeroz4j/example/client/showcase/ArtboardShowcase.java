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

public class ArtboardShowcase extends ComponentShowcase {

    public ArtboardShowcase() {
        addTitle("Artboard");
        addDescription("Artboards are fixed-size containers that simulate device screens for responsive design previewing.");

        Artboard artboard1 = new Artboard();
        artboard1.addClassName("phone-1 bg-neutral text-neutral-content flex items-center justify-center rounded-box shadow-md");
        artboard1.add(new Div("Phone 1 (320x568)"));

        Artboard artboard2 = new Artboard();
        artboard2.addClassName("phone-2 bg-neutral text-neutral-content flex items-center justify-center rounded-box shadow-md");
        artboard2.add(new Div("Phone 2 (375x667)"));

        addSection("Artboard Sizes", artboard1, artboard2);
    }
}
