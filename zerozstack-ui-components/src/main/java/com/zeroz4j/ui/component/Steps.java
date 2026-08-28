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
package com.zeroz4j.ui.component;

public class Steps extends Component implements HasComponents, HasStyle, HasSize {

    public Steps() {
        super("ul");
        addClassName("steps");
        // The trail never grows past what it was put in. daisyUI lays it out as an inline grid,
        // whose width follows its contents, so one long step name made the whole page scroll
        // sideways; it already scrolls inside itself, and now it is allowed to.
        getElement().getStyle().setProperty("max-width", "100%");
    }

    @Override
    public Component getComponent() {
        return this;
    }

    /**
     * Adds one or more steps, and lets each of their names break rather than set the width of the
     * whole trail.
     *
     * <p>A trail is laid out as an inline grid, so its width follows the longest name in it. One
     * long name - a German compound noun, a file path - therefore made the whole page scroll
     * sideways. "anywhere" rather than "break-word": only "anywhere" makes the browser count the
     * broken name when it works out how narrow the step may be.</p>
     *
     * @param components the steps to add
     */
    @Override
    public void add(Component... components) {
        HasComponents.super.add(components);
        for (Component step : components) {
            step.getElement().getStyle().setProperty("overflow-wrap", "anywhere");
            step.getElement().getStyle().setProperty("min-width", "0");
        }
    }
}

