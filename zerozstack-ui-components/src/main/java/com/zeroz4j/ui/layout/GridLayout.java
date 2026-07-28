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
package com.zeroz4j.ui.layout;

import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.HasComponents;
import com.zeroz4j.ui.component.HasSize;
import com.zeroz4j.ui.component.HasStyle;

public class GridLayout extends Component implements HasComponents, HasSize, HasStyle {

    public GridLayout() {
        super("div");
        getElement().getStyle().setProperty("display", "grid");
    }

    public GridLayout(int columns, int rows) {
        this();
        setColumns(columns);
        setRows(rows);
    }

    public void setColumns(int columns) {
        getElement().getStyle().setProperty("grid-template-columns", "repeat(" + columns + ", minmax(0, 1fr))");
    }

    public void setRows(int rows) {
        getElement().getStyle().setProperty("grid-template-rows", "repeat(" + rows + ", minmax(0, 1fr))");
    }

    public void setColumnGap(String gap) {
        getElement().getStyle().setProperty("column-gap", gap);
    }

    public void setRowGap(String gap) {
        getElement().getStyle().setProperty("row-gap", gap);
    }

    public void setGap(String gap) {
        getElement().getStyle().setProperty("gap", gap);
    }

    public void setColumnSpan(Component component, int span) {
        component.getElement().getStyle().setProperty("grid-column", "span " + span + " / span " + span);
    }

    public void setRowSpan(Component component, int span) {
        component.getElement().getStyle().setProperty("grid-row", "span " + span + " / span " + span);
    }

    @Override
    public Component getComponent() {
        return this;
    }
}
