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
 * The trail of pages above this one - "Home / Orders / Order 4821".
 *
 * <p>It is a real {@code <nav>} named "Breadcrumb". That name is what lets a screen reader list it
 * alongside the main menu and jump straight to it, and what keeps the two apart. Change it with
 * {@link #setAriaLabel(String)} only in the odd case of a page carrying two trails.</p>
 */
public class Breadcrumbs extends Component implements HasComponents, HasStyle, HasSize {

    public Breadcrumbs() {
        super("nav");
        addClassName("breadcrumbs");
        setAriaLabel("Breadcrumb");
    }

    /**
     * Renames the trail for anybody who cannot see it.
     *
     * @param label the words, or null to take the name away again
     * @return this trail
     */
    public Breadcrumbs withAriaLabel(String label) {
        setAriaLabel(label);
        return this;
    }

    @Override
    public Component getComponent() {
        return this;
    }
}

