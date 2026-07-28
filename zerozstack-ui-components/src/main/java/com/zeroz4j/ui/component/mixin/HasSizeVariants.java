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
import com.zeroz4j.ui.theme.ThemeSize;

public interface HasSizeVariants<T extends Component> extends HasStyle {

    String getThemePrefix();

    @SuppressWarnings("unchecked")
    default T setThemeSize(ThemeSize size) {
        String prefix = getThemePrefix();
        if (prefix == null) {
            throw new IllegalStateException("getThemePrefix() must not return null");
        }
        // Remove all possible size classes
        for (ThemeSize s : ThemeSize.values()) {
            removeClassName(prefix + "-" + s.getClassNameSuffix());
        }
        if (size != null) {
            addClassName(prefix + "-" + size.getClassNameSuffix());
        }
        return (T) this;
    }
}
