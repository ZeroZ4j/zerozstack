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

public class FlexLayout extends Component implements FlexComponent, HasComponents, HasSize, HasStyle {
    
    public enum FlexDirection {
        ROW("row"), COLUMN("column"), ROW_REVERSE("row-reverse"), COLUMN_REVERSE("column-reverse");
        private final String value;
        FlexDirection(String value) { this.value = value; }
        public String getValue() { return value; }
    }

    public enum FlexWrap {
        NOWRAP("nowrap"), WRAP("wrap"), WRAP_REVERSE("wrap-reverse");
        private final String value;
        FlexWrap(String value) { this.value = value; }
        public String getValue() { return value; }
    }

    public FlexLayout() {
        super("div");
        getElement().getStyle().setProperty("display", "flex");
    }

    public FlexLayout(Component... components) {
        this();
        add(components);
    }

    public void setFlexDirection(FlexDirection direction) {
        getElement().getStyle().setProperty("flex-direction", direction != null ? direction.getValue() : "");
    }

    public void setFlexWrap(FlexWrap wrap) {
        getElement().getStyle().setProperty("flex-wrap", wrap != null ? wrap.getValue() : "");
    }
    
    @Override
    public Component getComponent() {
        return this;
    }
}
