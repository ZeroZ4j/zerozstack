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

public class DiffShowcase extends ComponentShowcase {

    public DiffShowcase() {
        addTitle("Diff");
        addDescription("Diff component compares two elements or images by overlaying them with a draggable slider.");

        Diff diff = new Diff();
        diff.addClassName("aspect-[16/9] w-full h-48 border border-base-300 rounded-box overflow-hidden");

        Div item1 = new Div();
        item1.addClassName("diff-item-1");
        
        Div content1 = new Div("ZEROZ4J - PRIMARY");
        content1.addClassName("bg-primary text-primary-content text-3xl font-black grid place-content-center h-full");
        item1.add(content1);

        Div item2 = new Div();
        item2.addClassName("diff-item-2");
        
        Div content2 = new Div("ZEROZ4J - BASE");
        content2.addClassName("bg-base-200 text-base-content text-3xl font-black grid place-content-center h-full");
        item2.add(content2);

        Div resizer = new Div();
        resizer.addClassName("diff-resizer");

        diff.add(item1, item2, resizer);

        addSection("Diff Example", diff);
    }
}
