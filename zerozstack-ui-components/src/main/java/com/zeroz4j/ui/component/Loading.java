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
import com.zeroz4j.ui.component.mixin.HasSizeVariants;

/**
 * A spinner shown while something is on its way.
 *
 * <p>The spin says "wait" to anybody who can see it, and nothing at all to anybody who cannot. So
 * the element carries {@code role="status"} and is named "Loading", and a screen reader says the
 * word when the spinner appears.</p>
 *
 * <p>Say what is being waited for wherever the page knows it:
 * {@code new Loading().withAriaLabel("Loading your orders")} is far more use than "Loading" on its
 * own.</p>
 */
public class Loading extends Component implements HasStyle, HasSize,
        HasColorVariants<Loading>,
        HasSizeVariants<Loading> {

    public Loading() {
        super("span");
        addClassName("loading");
        getElement().setAttribute("role", "status");
        setAriaLabel("Loading");
    }

    /**
     * Names what is being waited for, for anybody who cannot see the spinner.
     *
     * @param label the words, or null to say nothing at all
     * @return this spinner
     */
    public Loading withAriaLabel(String label) {
        setAriaLabel(label);
        return this;
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public String getThemePrefix() {
        return "loading";
    }
}
