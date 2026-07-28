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

public interface FlexComponent {

    enum Alignment {
        START("flex-start"), END("flex-end"), CENTER("center"), STRETCH("stretch"), BASELINE("baseline");
        private final String value;
        Alignment(String value) { this.value = value; }
        public String getValue() { return value; }
    }

    enum JustifyContentMode {
        START("flex-start"), END("flex-end"), CENTER("center"), BETWEEN("space-between"), AROUND("space-around"), EVENLY("space-evenly");
        private final String value;
        JustifyContentMode(String value) { this.value = value; }
        public String getValue() { return value; }
    }

    Component getComponent();

    default void setAlignItems(Alignment alignment) {
        getComponent().getElement().getStyle().setProperty("align-items", alignment != null ? alignment.getValue() : "");
    }

    default Alignment getAlignItems() {
        String align = getComponent().getElement().getStyle().getPropertyValue("align-items");
        for (Alignment a : Alignment.values()) {
            if (a.getValue().equals(align)) return a;
        }
        return Alignment.STRETCH; // default
    }

    default void setJustifyContentMode(JustifyContentMode justifyContentMode) {
        getComponent().getElement().getStyle().setProperty("justify-content", justifyContentMode != null ? justifyContentMode.getValue() : "");
    }

    default JustifyContentMode getJustifyContentMode() {
        String justify = getComponent().getElement().getStyle().getPropertyValue("justify-content");
        for (JustifyContentMode j : JustifyContentMode.values()) {
            if (j.getValue().equals(justify)) return j;
        }
        return JustifyContentMode.START; // default
    }

    default void setFlexGrow(double flexGrow, Component... components) {
        for (Component c : components) {
            c.getElement().getStyle().setProperty("flex-grow", String.valueOf(flexGrow));
        }
    }

    default void setFlexShrink(double flexShrink, Component... components) {
        for (Component c : components) {
            c.getElement().getStyle().setProperty("flex-shrink", String.valueOf(flexShrink));
        }
    }
    
    default void setFlexBasis(String flexBasis, Component... components) {
        for (Component c : components) {
            c.getElement().getStyle().setProperty("flex-basis", flexBasis);
        }
    }

    default void expand(Component... components) {
        setFlexGrow(1.0, components);
    }
}
