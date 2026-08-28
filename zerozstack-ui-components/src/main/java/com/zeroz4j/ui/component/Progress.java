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

import com.zeroz4j.ui.component.mixin.HasColorVariants;

/**
 * A bar that fills up as something finishes.
 *
 * <p>This is the browser's own {@code <progress>}, so the reading is announced for free. What it
 * has no way to know is what the reading is <i>of</i>. Give it words with
 * {@link #setAriaLabel(String)} where the page around it does not already say - a bar under the
 * word "Uploading" needs nothing, a bar on its own needs a name.</p>
 */
public class Progress extends Component implements HasStyle, HasSize,
        HasColorVariants<Progress> {

    public Progress() {
        super("progress");
        addClassName("progress");
    }

    /**
     * Names the bar for anybody who cannot see it - "Upload", "Import", "Disk used".
     *
     * <p>There is no default here on purpose. Most bars are already explained by the words above
     * them, and a made-up name that disagrees with what is on the screen is worse than no name.</p>
     *
     * @param label the words, or null to take the name away again
     * @return this bar
     */
    public Progress withAriaLabel(String label) {
        setAriaLabel(label);
        return this;
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public String getThemePrefix() {
        return "progress";
    }
}
