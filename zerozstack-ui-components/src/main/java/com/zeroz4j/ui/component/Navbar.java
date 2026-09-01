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
 * The bar of links across the top of the page.
 *
 * <p>It is marked as navigation and named "Main", so a screen reader can offer it as the page's
 * main menu and skip straight to it instead of walking the whole header. A second bar on the same
 * page - a toolbar for one section, say - should be given its own name with
 * {@link #setAriaLabel(String)}, because two menus both called "Main" are no easier to tell apart
 * than two with no name at all.</p>
 */
public class Navbar extends Component implements HasComponents, HasStyle, HasSize {

    public Navbar() {
        super("div");
        addClassName("navbar");
        getElement().setAttribute("role", "navigation");
        setAriaLabel("Main");
    }

    /**
     * Renames this bar for anybody who cannot see it - "Account", "Section".
     *
     * @param label the words, or null to take the name away again
     * @return this bar
     */
    public Navbar withAriaLabel(String label) {
        setAriaLabel(label);
        return this;
    }

    @Override
    public Component getComponent() {
        return this;
    }
}

