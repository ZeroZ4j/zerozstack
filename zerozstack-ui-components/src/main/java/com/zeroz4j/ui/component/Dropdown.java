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
import com.zeroz4j.ui.layout.Span;
import com.zeroz4j.ui.component.HasStyle;

public class Dropdown extends Component implements HasComponents, HasStyle {

    private final Component summary;
    private final Div content;

    public Dropdown(String label) {
        super("details");
        addClassName("dropdown");
        
        class Summary extends Component implements HasStyle {
            public Summary() { super("summary"); }
            @Override public Component getComponent() { return this; }
        }
        Summary sum = new Summary();
        sum.addClassName("btn");
        sum.addClassName("m-1");
        sum.getElement().setTextContent(label);
        summary = sum;
        
        content = new Div();
        content.addClassName("dropdown-content");
        content.addClassName("z-[1]");
        content.addClassName("menu");
        content.addClassName("p-2");
        content.addClassName("shadow");
        content.addClassName("bg-base-100");
        content.addClassName("rounded-box");
        content.addClassName("w-52");
        
        getElement().appendChild(summary.getElement());
        getElement().appendChild(content.getElement());
    }
    
    @Override
    public Component getComponent() {
        return this;
    }
    
    @Override
    public void add(Component... components) {
        content.add(components);
    }
    
    @Override
    public void remove(Component... components) {
        content.remove(components);
    }
    
    @Override
    public void removeAll() {
        content.removeAll();
    }
}
