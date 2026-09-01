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
import java.util.ArrayList;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * A component that can contain other components.
 *
 * <p>Adding and removing through these methods is what gives the things inside a lifecycle:
 * {@link Component#onAttach()} when they go in, {@link Component#onDetach()} when they come out,
 * and the same for everything nested inside them. Appending an element straight to
 * {@code getElement()} skips all of that.</p>
 */
public interface HasComponents {

    Component getComponent();

    /** Puts each component in, at the end, and starts it. */
    default void add(Component... components) {
        Component self = getComponent();
        for (Component c : components) {
            self.getElement().appendChild(c.getOuterElement());
            self.trackedChildren().add(c);
            c.attach();
        }
    }

    /** Takes each component out and shuts it down, along with everything inside it. */
    default void remove(Component... components) {
        Component self = getComponent();
        for (Component c : components) {
            self.getElement().removeChild(c.getOuterElement());
            self.trackedChildren().remove(c);
            c.detach();
        }
    }

    /** Empties this container, shutting down everything that was in it. */
    default void removeAll() {
        Component self = getComponent();
        for (Component c : new ArrayList<>(self.trackedChildren())) {
            c.detach();
        }
        self.trackedChildren().clear();
        HTMLElement el = self.getElement();
        while (el.getLastChild() != null) {
            el.removeChild(el.getLastChild());
        }
    }

    /**
     * Empties this container and fills it with {@code newContents} instead.
     *
     * <p>This is the supported way to swap what is showing - a screen for another screen, a list
     * for the rebuilt list. Use it instead of {@code getElement().setInnerHTML("")}: emptying an
     * element by hand takes the old contents off the page without telling them, so their timers
     * keep ticking, their effects keep firing and their subscriptions keep arriving, all
     * rebuilding something nobody is looking at any more - and throwing the keyboard off whatever
     * the person moved on to. Everything leaving gets its {@link Component#onDetach()}, nested
     * parts included.</p>
     *
     * @param newContents what to show instead - none, one, or several
     */
    default void replaceContents(Component... newContents) {
        removeAll();
        if (newContents != null) {
            add(newContents);
        }
    }
}
