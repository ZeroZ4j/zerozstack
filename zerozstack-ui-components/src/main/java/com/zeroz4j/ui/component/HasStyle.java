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

public interface HasStyle {

    Component getComponent();

    default void addClassName(String className) {
        if (className != null && !className.isEmpty()) {
            for (String c : className.split(" ")) {
                if (c.trim().isEmpty()) continue;
                String current = getComponent().getElement().getClassName();
                if (current == null || current.isEmpty()) {
                    getComponent().getElement().setClassName(c.trim());
                } else if (!(" " + current + " ").contains(" " + c.trim() + " ")) {
                    getComponent().getElement().setClassName(current + " " + c.trim());
                }
            }
        }
    }

    default void removeClassName(String className) {
        if (className != null && !className.isEmpty()) {
            for (String c : className.split(" ")) {
                if (c.trim().isEmpty()) continue;
                String current = getComponent().getElement().getClassName();
                if (current != null && !current.isEmpty()) {
                    String updated = (" " + current + " ").replace(" " + c.trim() + " ", " ").trim();
                    getComponent().getElement().setClassName(updated);
                }
            }
        }
    }
    
    default void setClassName(String className) {
        getComponent().getElement().setClassName(className != null ? className : "");
    }
    
    default String getClassName() {
        return getComponent().getElement().getClassName();
    }
    
    default void setStyle(String name, String value) {
        getComponent().getElement().getStyle().setProperty(name, value);
    }
    
    default void removeStyle(String name) {
        getComponent().getElement().getStyle().removeProperty(name);
    }
}
