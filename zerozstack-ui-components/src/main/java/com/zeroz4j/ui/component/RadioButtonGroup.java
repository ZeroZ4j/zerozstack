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

import org.teavm.jso.dom.html.HTMLInputElement;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.browser.Window;
import java.util.List;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.xml.Node;
import org.teavm.jso.dom.xml.NodeList;

public class RadioButtonGroup extends AbstractField<RadioButtonGroup, String> {

    private final String groupName;

    public RadioButtonGroup(String groupName) {
        super("div", null);
        this.groupName = groupName;
        addClassName("flex");
        addClassName("flex-col");
        addClassName("gap-2");
    }
    
    public void setItems(List<String> items) {
        // Clear existing
        while (getElement().getLastChild() != null) {
            getElement().removeChild(getElement().getLastChild());
        }
        
        for (String item : items) {
            HTMLElement label = Window.current().getDocument().createElement("label");
            label.setClassName("label cursor-pointer justify-start gap-4");
            
            HTMLInputElement radio = (HTMLInputElement) Window.current().getDocument().createElement("input");
            radio.setAttribute("type", "radio");
            radio.setName(groupName);
            radio.setClassName("radio radio-primary");
            radio.setValue(item);
            
            HTMLElement span = Window.current().getDocument().createElement("span");
            span.setClassName("label-text");
            span.setTextContent(item);
            
            label.appendChild(radio);
            label.appendChild(span);
            
            getElement().appendChild(label);
            
            EventListener<Event> changeListener = evt -> {
                if (radio.isChecked()) {
                    setModelValue(radio.getValue(), true);
                }
            };
            radio.addEventListener("change", threaded(changeListener));
        }
    }

    @Override
    protected void setPresentationValue(String value) {
        NodeList<? extends Node> children = getElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node labelNode = children.get(i);
            if (labelNode.getNodeType() == Node.ELEMENT_NODE) {
                HTMLElement label = (HTMLElement) labelNode;
                HTMLInputElement radio = (HTMLInputElement) label.getFirstChild();
                radio.setChecked(value != null && value.equals(radio.getValue()));
            }
        }
    }
}
