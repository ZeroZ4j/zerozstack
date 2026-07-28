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

import com.zeroz4j.ui.layout.Div;

public class Card extends Component implements HasComponents, HasSize, HasStyle {

    private final Div body;

    public Card() {
        super("div");
        addClassName("card");
        addClassName("bg-base-100");
        addClassName("shadow-xl");
        
        body = new Div();
        body.addClassName("card-body");
        getElement().appendChild(body.getElement());
    }

    @Override
    public Component getComponent() {
        return this;
    }
    
    @Override
    public void add(Component... components) {
        body.add(components);
    }
    
    @Override
    public void remove(Component... components) {
        body.remove(components);
    }
    
    @Override
    public void removeAll() {
        body.removeAll();
    }
}
