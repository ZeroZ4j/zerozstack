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

public class Dialog extends Component implements HasComponents, HasStyle {

    private final Div modalBox;
    private final Div modalAction;
    private final Div content;
    
    public Dialog() {
        super("dialog");
        addClassName("modal");
        
        modalBox = new Div();
        modalBox.addClassName("modal-box");
        
        content = new Div();
        
        modalAction = new Div();
        modalAction.addClassName("modal-action");
        
        modalBox.add(content);
        modalBox.add(modalAction);
        
        getElement().appendChild(modalBox.getElement());
    }

    public void open() {
        // We need to call showModal() on the dialog element natively
        // In TeaVM, we can use JSMethods or cast to an interface if available
        // For simplicity we set an attribute for open.
        // Actually, daisyui modals can be opened by adding "modal-open" class
        addClassName("modal-open");
    }
    
    public void close() {
        removeClassName("modal-open");
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
    
    public void addAction(Component component) {
        modalAction.add(component);
    }
}
