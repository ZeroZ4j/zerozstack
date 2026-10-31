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

/**
 * A plain HTML element of any tag, for the markup the component library has no component for:
 * a {@code section}, a {@code ul}, an {@code img}, a {@code form}.
 *
 * <pre>{@code
 * El list = new El("ul", "list-disc pl-5");
 * El item = new El("li");
 * item.setText("First");
 * list.add(item);
 * }</pre>
 *
 * <p>It is a real {@link Component}, so it joins the lifecycle: parts put in with {@code add(...)}
 * are detached with it. A click handler on one that is not a {@code button}, {@code a} or form
 * control cannot be reached with the keyboard, so use a {@link Button} for anything pressed.</p>
 */
public class El extends Component implements HasComponents, HasStyle, HasText {

    /**
     * Creates an element.
     *
     * @param tag the tag name, for example {@code "section"}
     */
    public El(String tag) {
        super(tag);
    }

    /**
     * Creates an element with CSS classes.
     *
     * @param tag     the tag name
     * @param classes space-separated class names
     */
    public El(String tag, String classes) {
        super(tag);
        if (classes != null && !classes.isEmpty()) {
            addClassName(classes);
        }
    }

    @Override
    public Component getComponent() {
        return this;
    }

    /**
     * Sets an attribute.
     *
     * @param name  the attribute name
     * @param value the value
     * @return this element, for chaining
     */
    public El attr(String name, String value) {
        getElement().setAttribute(name, value);
        return this;
    }
}
