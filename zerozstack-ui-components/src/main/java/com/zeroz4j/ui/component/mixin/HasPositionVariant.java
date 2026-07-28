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
package com.zeroz4j.ui.component.mixin;

import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.HasStyle;

public interface HasPositionVariant<T extends Component> extends HasStyle {

    String getThemePrefix();

    @SuppressWarnings("unchecked")
    default T setPosition(Position position) {
        String prefix = getThemePrefix();
        if (prefix == null) {
            throw new IllegalStateException("getThemePrefix() must not return null");
        }
        for (Position p : Position.values()) {
            removeClassName(prefix + "-" + p.getValue());
        }
        if (position != null) {
            addClassName(prefix + "-" + position.getValue());
        }
        return (T) this;
    }

    enum Position {
        TOP("top"),
        BOTTOM("bottom"),
        LEFT("left"),
        RIGHT("right");

        private final String value;

        Position(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
