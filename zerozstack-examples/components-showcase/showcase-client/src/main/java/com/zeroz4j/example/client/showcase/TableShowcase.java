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

public class TableShowcase extends ComponentShowcase {

    public TableShowcase() {
        addTitle("Table");
        addDescription("Table component displays tabular data in rows and columns.");

        Table table = new Table();
        table.addClassName("table-zebra"); // Zebra striping

        // Create Header
        Component thead = new Component("thead") {};
        Component headerRow = new Component("tr") {};
        
        Component thId = new Component("th") {};
        thId.getElement().setTextContent("#");
        Component thName = new Component("th") {};
        thName.getElement().setTextContent("Name");
        Component thRole = new Component("th") {};
        thRole.getElement().setTextContent("Role");

        headerRow.getElement().appendChild(thId.getElement());
        headerRow.getElement().appendChild(thName.getElement());
        headerRow.getElement().appendChild(thRole.getElement());
        thead.getElement().appendChild(headerRow.getElement());

        // Create Body
        Component tbody = new Component("tbody") {};

        // Row 1
        Component row1 = new Component("tr") {};
        Component td1_1 = new Component("td") {};
        td1_1.getElement().setTextContent("1");
        Component td1_2 = new Component("td") {};
        td1_2.getElement().setTextContent("Alice");
        Component td1_3 = new Component("td") {};
        td1_3.getElement().setTextContent("Developer");
        row1.getElement().appendChild(td1_1.getElement());
        row1.getElement().appendChild(td1_2.getElement());
        row1.getElement().appendChild(td1_3.getElement());
        tbody.getElement().appendChild(row1.getElement());

        // Row 2
        Component row2 = new Component("tr") {};
        Component td2_1 = new Component("td") {};
        td2_1.getElement().setTextContent("2");
        Component td2_2 = new Component("td") {};
        td2_2.getElement().setTextContent("Bob");
        Component td2_3 = new Component("td") {};
        td2_3.getElement().setTextContent("Designer");
        row2.getElement().appendChild(td2_1.getElement());
        row2.getElement().appendChild(td2_2.getElement());
        row2.getElement().appendChild(td2_3.getElement());
        tbody.getElement().appendChild(row2.getElement());

        table.getElement().appendChild(thead.getElement());
        table.getElement().appendChild(tbody.getElement());

        addSection("Zebra Table", table);
    }
}
