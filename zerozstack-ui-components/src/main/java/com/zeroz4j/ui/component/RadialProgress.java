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

/**
 * A ring that fills up to show a percentage.
 *
 * <p>The ring is drawn entirely in CSS, so on its own it is a plain empty box that means nothing
 * to anybody who cannot see it. It carries {@code role="progressbar"} and a range of 0 to 100, and
 * the announced reading follows the {@code --value} style the application sets - so the number
 * that is read out and the amount of ring that is filled can never disagree.</p>
 */
public class RadialProgress extends Component implements HasText, HasStyle, HasSize {

    public RadialProgress() {
        super("div");
        addClassName("radial-progress");
        getElement().setAttribute("role", "progressbar");
        getElement().setAttribute("aria-valuemin", "0");
        getElement().setAttribute("aria-valuemax", "100");
    }

    public RadialProgress(String text) {
        this();
        setText(text);
    }

    /**
     * Sets a style, and keeps the announced reading in step with the drawn one.
     *
     * <p>How full the ring is drawn comes from the {@code --value} property. Anybody who cannot
     * see it needs that same number as the current reading, and setting it here means no caller
     * has to remember to.</p>
     *
     * @param name  the style property name
     * @param value the value
     */
    @Override
    public void setStyle(String name, String value) {
        // HasSize extends HasStyle, so the default has to be reached through the deeper one.
        HasSize.super.setStyle(name, value);
        if ("--value".equals(name)) {
            announceValue(value);
        }
    }

    /**
     * Names the ring for anybody who cannot see it - "Battery", "Storage used", "Progress".
     *
     * @param label the words, or null to take the name away again
     * @return this ring
     */
    public RadialProgress withAriaLabel(String label) {
        setAriaLabel(label);
        return this;
    }

    /**
     * A reading has to be a plain number to be read out. A value written as a sum or as a variable
     * cannot be, so rather than announce something wrong the ring says nothing.
     */
    private void announceValue(String value) {
        String number = value == null ? "" : value.trim();
        try {
            Double.parseDouble(number);
            getElement().setAttribute("aria-valuenow", number);
        } catch (NumberFormatException notANumber) {
            getElement().removeAttribute("aria-valuenow");
        }
    }

    @Override
    public Component getComponent() {
        return this;
    }
}
