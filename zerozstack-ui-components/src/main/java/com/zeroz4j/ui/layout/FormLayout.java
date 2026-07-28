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
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FormLayout extends Component implements HasComponents, HasSize, HasStyle {

    public static class ResponsiveStep {
        private final String minWidth;
        private final int columns;

        public ResponsiveStep(String minWidth, int columns) {
            this.minWidth = minWidth;
            this.columns = columns;
        }
        public String getMinWidth() { return minWidth; }
        public int getColumns() { return columns; }
    }

    private static final AtomicInteger instanceCounter = new AtomicInteger(0);
    private final String layoutId;
    private final List<ResponsiveStep> steps = new ArrayList<>();
    private HTMLElement styleElement;

    public FormLayout() {
        super("div");
        this.layoutId = "form-layout-" + instanceCounter.incrementAndGet();
        addClassName(layoutId);
        getElement().getStyle().setProperty("display", "grid");
        getElement().getStyle().setProperty("gap", "1rem");
        
        // Default single column
        setResponsiveSteps(new ResponsiveStep("0px", 1), new ResponsiveStep("600px", 2));
    }

    public FormLayout(Component... components) {
        this();
        add(components);
    }

    public void setResponsiveSteps(ResponsiveStep... newSteps) {
        steps.clear();
        for (ResponsiveStep step : newSteps) {
            steps.add(step);
        }
        updateStyles();
    }

    public void setResponsiveSteps(List<ResponsiveStep> newSteps) {
        steps.clear();
        steps.addAll(newSteps);
        updateStyles();
    }
    
    public void setColSpan(Component component, int span) {
        component.getElement().getStyle().setProperty("grid-column", "span " + span);
    }

    private void updateStyles() {
        if (styleElement == null) {
            styleElement = Window.current().getDocument().createElement("style");
            Window.current().getDocument().getHead().appendChild(styleElement);
        }
        
        StringBuilder css = new StringBuilder();
        for (ResponsiveStep step : steps) {
            css.append("@media (min-width: ").append(step.getMinWidth()).append(") { ");
            css.append(".").append(layoutId).append(" { ");
            css.append("grid-template-columns: repeat(").append(step.getColumns()).append(", minmax(0, 1fr)); ");
            css.append("} ");
            css.append("} ");
        }
        styleElement.setInnerHTML(css.toString());
    }

    @Override
    public Component getComponent() {
        return this;
    }
}
