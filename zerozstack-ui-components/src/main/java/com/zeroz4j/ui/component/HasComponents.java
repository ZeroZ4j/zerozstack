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
import org.teavm.jso.dom.html.HTMLElement;

/**
 * A component that can contain other components.
 */
public interface HasComponents {
    
    Component getComponent();
    
    default void add(Component... components) {
        for (Component c : components) {
            getComponent().getElement().appendChild(c.getElement());
            c.onAttach();
        }
    }
    
    default void remove(Component... components) {
        for (Component c : components) {
            getComponent().getElement().removeChild(c.getElement());
            c.onDetach();
        }
    }
    
    default void removeAll() {
        HTMLElement el = getComponent().getElement();
        while (el.getLastChild() != null) {
            el.removeChild(el.getLastChild());
        }
    }
}
