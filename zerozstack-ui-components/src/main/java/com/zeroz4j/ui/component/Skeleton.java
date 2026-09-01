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
 * A grey block standing in for something that has not arrived yet.
 *
 * <p>It is decoration and nothing else, so it is hidden from screen readers. A screen of these
 * would otherwise be read out as a wall of empty boxes, which tells somebody who is waiting less
 * than silence does.</p>
 *
 * <p>The word "Loading" belongs on the area around them, not on each block - a {@link Loading}
 * spinner beside the heading, or a line of text the page replaces when the content lands.</p>
 */
public class Skeleton extends Component implements HasStyle, HasSize {

    public Skeleton() {
        super("div");
        addClassName("skeleton");
        getElement().setAttribute("aria-hidden", "true");
    }

    @Override
    public Component getComponent() {
        return this;
    }
}
