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

import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.HasText;
import com.zeroz4j.ui.component.HasStyle;

public class CardTitle extends Component implements HasText, HasStyle {

    public CardTitle() {
        super("h2");
        addClassName("card-title");
    }

    public CardTitle(String text) {
        this();
        setText(text);
    }

    @Override
    public Component getComponent() {
        return this;
    }
}
