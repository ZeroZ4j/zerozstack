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

import com.zeroz4j.ui.component.mixin.HasLayer;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import com.zeroz4j.ui.theme.Layer;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.MouseEvent;
import org.teavm.jso.dom.html.HTMLElement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The menu that appears where you right-click.
 *
 * <p>Build it once, attach it to as many components as you like. Entries fixed at build time come
 * from {@link #item(String, String, Runnable)}; entries that depend on what was clicked come from
 * the supplier passed to {@link #attachTo(Component, Supplier)}, which is asked afresh every
 * time.</p>
 *
 * <pre>{@code
 * ContextMenu menu = new ContextMenu()
 *         .item("copy", "Copy", this::copy)
 *         .item("trash", "Delete", this::delete);
 * menu.attachTo(row, null);
 * }</pre>
 *
 * <h2>Keyboard</h2>
 *
 * <p>Opening the menu moves the keyboard onto its first entry, so it can be walked with Tab and
 * chosen with Enter. Escape shuts it and puts the keyboard back where it was — usually the row that
 * was right-clicked. Clicking anywhere else shuts it too.</p>
 *
 * <p>The keyboard is <b>not</b> held inside the menu. Tab off the last entry and you are back on
 * the page, which is right for something this small and this temporary; if you want a thing the
 * keyboard cannot leave, you want a {@link Dialog}.</p>
 *
 * <h2>Stacking</h2>
 *
 * <p>It sits on {@link Layer#DROPDOWN}, the same tier as any other menu opened from a control.
 * Before this it carried a hand-written 1000, which is exactly the kind of guess {@link Layer}
 * exists to replace — and which still lost to an open modal {@link Dialog}, because that is in the
 * browser's top layer and no number reaches it.</p>
 */
public final class ContextMenu {

    public record Item(String icon, String label, Runnable action) {}

    private final Div menu = new Div();
    private final List<Item> fixedItems = new ArrayList<>();
    private boolean mounted;
    private boolean closeOnEsc = true;
    private boolean opened;
    /** Where the keyboard was when the menu opened, so it can be put back when it shuts. */
    private HTMLElement openedFrom;

    public ContextMenu() {
        menu.addClassName("menu bg-base-200 rounded-box shadow-xl border border-base-300 "
            + "w-56 p-1 text-sm");
        menu.setStyle("position", "fixed");
        HasLayer.applyTo(menu, Layer.DROPDOWN);
        menu.getElement().setAttribute("role", "menu");
        menu.setVisible(false);
    }

    public ContextMenu item(String icon, String label, Runnable action) {
        fixedItems.add(new Item(icon, label, action));
        return this;
    }

    /**
     * Whether Escape shuts the menu and puts the keyboard back where it was. On by default.
     *
     * @param closeOnEsc true to let Escape close it
     * @return this menu
     */
    public ContextMenu closeOnEsc(boolean closeOnEsc) {
        this.closeOnEsc = closeOnEsc;
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

    /** Shuts the menu and puts the keyboard back on whatever was right-clicked. */
    public void close() {
        if (!opened) {
            return;
        }
        HTMLElement target = openedFrom;
        hide();
        if (target != null) {
            Js.focus(target);
        }
    }

    /** Whether the menu is showing. */
    public boolean isOpened() {
        return opened;
    }

    /** Shuts the menu and leaves the keyboard where it is — what a click elsewhere does. */
    private void hide() {
        opened = false;
        openedFrom = null;
        menu.setVisible(false);
    }

    private void show(int x, int y, List<Item> items) {
        if (!mounted) {
            Window.current().getDocument().getBody().appendChild(menu.getElement());
            Window.current().getDocument().addEventListener("click",
                Component.threaded((EventListener<MouseEvent>) e -> hide()));
            // Escape is read straight, not on a green thread: shutting a menu has to happen inside
            // the browser's own call, and the body suspends on nothing.
            Window.current().getDocument().addEventListener("keydown",
                (EventListener<Event>) e -> {
                    if (closeOnEsc && "Escape".equals(Js.eventKey(e)) && isOpened()) {
                        close();
                    }
                });
            mounted = true;
        }
        openedFrom = Js.activeElement();
        menu.removeAll();
        for (Item item : items) {
            Div row = new Div();
            row.addClassName("flex items-center gap-2 px-3 py-1.5 rounded-lg cursor-pointer "
                + "hover:bg-base-300");
            // A menu entry is a thing you choose, so it has to be reachable and announced as one.
            row.getElement().setAttribute("role", "menuitem");
            row.getElement().setAttribute("tabindex", "0");
            if (item.icon() != null) {
                row.add(Icon.of(item.icon(), "w-3.5 h-3.5 opacity-70"));
            }
            Span label = new Span(item.label());
            row.getElement().appendChild(label.getElement());
            row.getElement().addEventListener("click", Component.threaded(e -> {
                hide();
                item.action().run();
            }));
            row.getElement().addEventListener("keydown", (EventListener<Event>) e -> {
                String key = Js.eventKey(e);
                if ("Enter".equals(key) || " ".equals(key)) {
                    hide();
                    new Thread(() -> item.action().run()).start();
                }
            });
            menu.add(row);
        }
        menu.setStyle("left", x + "px");
        menu.setStyle("top", y + "px");
        menu.setVisible(true);
        opened = true;
        // The keyboard follows the menu, so the entries can be walked without the mouse.
        Js.focusFirstInside(menu.getElement());
    }
}
