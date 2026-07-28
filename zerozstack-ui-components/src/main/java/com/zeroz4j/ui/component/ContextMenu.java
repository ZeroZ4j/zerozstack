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
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Right-click menu. Build once, attach to any component; items may be added per-show via
 * the dynamic supplier so the menu reflects the row it was opened on.
 */
public final class ContextMenu {

    public record Item(String icon, String label, Runnable action) {}

    private final Div menu = new Div();
    private final List<Item> fixedItems = new ArrayList<>();
    private boolean mounted;

    public ContextMenu() {
        menu.addClassName("menu bg-base-200 rounded-box shadow-xl border border-base-300 "
            + "w-56 p-1 text-sm");
        menu.setStyle("position", "fixed");
        menu.setStyle("z-index", "1000");
        menu.setVisible(false);
    }

    public ContextMenu item(String icon, String label, Runnable action) {
        fixedItems.add(new Item(icon, label, action));
        return this;
    }

    /** Attach to a component; extraItems (nullable) computes row-specific entries on open. */
    public void attachTo(Component target, Supplier<List<Item>> extraItems) {
        target.getElement().addEventListener("contextmenu", Component.threaded((EventListener<MouseEvent>) e -> {
            e.preventDefault();
            e.stopPropagation();
            List<Item> items = new ArrayList<>(fixedItems);
            if (extraItems != null) {
                items.addAll(extraItems.get());
            }
            show(e.getClientX(), e.getClientY(), items);
        }));
    }

    private void show(int x, int y, List<Item> items) {
        if (!mounted) {
            Window.current().getDocument().getBody().appendChild(menu.getElement());
            Window.current().getDocument().addEventListener("click",
                Component.threaded((EventListener<MouseEvent>) e -> menu.setVisible(false)));
            mounted = true;
        }
        menu.removeAll();
        for (Item item : items) {
            Div row = new Div();
            row.addClassName("flex items-center gap-2 px-3 py-1.5 rounded-lg cursor-pointer "
                + "hover:bg-base-300");
            if (item.icon() != null) {
                row.add(Icon.of(item.icon(), "w-3.5 h-3.5 opacity-70"));
            }
            Span label = new Span(item.label());
            row.getElement().appendChild(label.getElement());
            row.getElement().addEventListener("click", Component.threaded(e -> {
                menu.setVisible(false);
                item.action().run();
            }));
            menu.add(row);
        }
        menu.setStyle("left", x + "px");
        menu.setStyle("top", y + "px");
        menu.setVisible(true);
    }
}

