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

public class Scroller extends Component implements HasComponents, HasSize, HasStyle {

    public enum ScrollDirection {
        BOTH, HORIZONTAL, VERTICAL, NONE
    }

    public Scroller() {
        super("div");
        setScrollDirection(ScrollDirection.BOTH);
    }

    public Scroller(Component content) {
        this();
        setContent(content);
    }

    public void setContent(Component content) {
        removeAll(); // clear existing
        add(content);
    }

    public void setScrollDirection(ScrollDirection direction) {
        getElement().getStyle().removeProperty("overflow");
        getElement().getStyle().removeProperty("overflow-x");
        getElement().getStyle().removeProperty("overflow-y");

        switch (direction) {
            case BOTH:
                getElement().getStyle().setProperty("overflow", "auto");
                break;
            case HORIZONTAL:
                getElement().getStyle().setProperty("overflow-x", "auto");
                getElement().getStyle().setProperty("overflow-y", "hidden");
                break;
            case VERTICAL:
                getElement().getStyle().setProperty("overflow-y", "auto");
                getElement().getStyle().setProperty("overflow-x", "hidden");
                break;
            case NONE:
                getElement().getStyle().setProperty("overflow", "hidden");
                break;
        }
    }

    @Override
    public Component getComponent() {
        return this;
    }
}
