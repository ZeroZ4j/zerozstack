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

public interface HasSize extends HasStyle {

    default void setWidth(String width) {
        getComponent().getElement().getStyle().setProperty("width", width);
    }
    
    default void setHeight(String height) {
        getComponent().getElement().getStyle().setProperty("height", height);
    }
    
    default void setWidthFull() {
        setWidth("100%");
    }
    
    default void setHeightFull() {
        setHeight("100%");
    }
    
    default void setSizeFull() {
        setWidthFull();
        setHeightFull();
    }
}
