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
package com.zeroz4j.ui.proof;

import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.Dialog;
import com.zeroz4j.ui.component.Drawer;
import com.zeroz4j.ui.component.Dropdown;
import com.zeroz4j.ui.component.Toast;
import com.zeroz4j.ui.component.Tooltip;
import com.zeroz4j.ui.layout.Div;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * The page the browser proof is driven against.
 *
 * <p>Every overlay on it is a real component from {@code zerozstack-ui-components}, compiled to
 * JavaScript exactly as an application's would be. Nothing here is hand-written HTML, which is the
 * point: a page of hand-written HTML would prove that the markup behaves, not that the library
 * produces that markup.</p>
 *
 * <p>Driven by {@code tools/overlay-proof/drive.mjs}. Element ids are the contract between the two;
 * changing one means changing both.</p>
 */
public final class OverlayProofPage {

    private OverlayProofPage() {
    }

    public static void main(String[] args) {
        HTMLElement root = Window.current().getDocument().getElementById("app-root");

        Div page = new Div();
        page.addClassName("p-8 flex flex-col gap-4 items-start");

        // Something focusable on the page behind every overlay. If Tab ever lands here while a
        // dialog is open, the focus trap has failed.
        Button behind = new Button("A button on the page behind");
        behind.setId("page-button");
        page.add(behind);

        page.add(defaultDialog());
        page.add(wideDialog());
        page.add(strictDialog());
        page.add(unownedDialog());
        page.add(dropdown());
        page.add(drawer());
        page.add(tooltip());
        page.add(toast());

        root.appendChild(page.getOuterElement());
    }

    /** Escape, the dim and both buttons all close it. Two focusables, so Tab has somewhere to go. */
    private static Div defaultDialog() {
        Dialog dialog = new Dialog("Confirm the change");
        dialog.setId("dlg-default");
        dialog.setWidth("32rem");
        dialog.add(new Div("Everything about this dialog is the library's, including the heading."));

        Button keep = new Button("Keep");
        keep.setId("dlg-default-keep");
        Button discard = new Button("Discard");
        discard.setId("dlg-default-discard");
        discard.addClickListener(e -> dialog.close());
        dialog.addAction(keep);
        dialog.addAction(discard);

        Button open = new Button("Open the default dialog");
        open.setId("open-default");
        open.addClickListener(e -> dialog.open());

        Div row = new Div();
        row.add(open, dialog);
        return row;
    }

    /** Sized with one call. The panel is never wider than the window, at any viewport. */
    private static Div wideDialog() {
        Dialog dialog = new Dialog("A wide panel");
        dialog.setId("dlg-wide");
        dialog.setWidth("56rem");
        dialog.add(new Div("setWidth(\"56rem\") sizes the panel, not the full-window overlay."));
        Button close = new Button("Close");
        close.setId("dlg-wide-close");
        close.addClickListener(e -> dialog.close());
        dialog.addAction(close);

        Button open = new Button("Open the wide dialog");
        open.setId("open-wide");
        open.addClickListener(e -> dialog.open());

        Div row = new Div();
        row.add(open, dialog);
        return row;
    }

    /** Refuses the dim, the way a form with half-written input has to. Escape still works. */
    private static Div strictDialog() {
        Dialog dialog = new Dialog("Unsaved changes");
        dialog.setId("dlg-strict");
        dialog.setCloseOnOutsideClick(false);
        dialog.add(new Div("Clicking outside this one does nothing."));
        Button close = new Button("Close");
        close.setId("dlg-strict-close");
        close.addClickListener(e -> dialog.close());
        dialog.addAction(close);

        Button open = new Button("Open the dialog that refuses the dim");
        open.setId("open-strict");
        open.addClickListener(e -> dialog.open());

        Div row = new Div();
        row.add(open, dialog);
        return row;
    }

    /**
     * A dialog the browser does not own — {@code setModal(false)}, the behaviour of 0.7.0. Nothing
     * moves the keyboard for it, so the component has to, and that is the path being checked here.
     */
    private static Div unownedDialog() {
        Dialog dialog = new Dialog("Not owned by the browser");
        dialog.setId("dlg-unowned");
        dialog.setModal(false);
        dialog.setCloseOnOutsideClick(false);
        dialog.add(new Div("The page behind this one is still live."));
        Button close = new Button("Close");
        close.setId("dlg-unowned-close");
        close.addClickListener(e -> dialog.close());
        dialog.addAction(close);

        Button open = new Button("Open the unowned dialog");
        open.setId("open-unowned");
        open.addClickListener(e -> dialog.open());

        Div row = new Div();
        row.add(open, dialog);
        return row;
    }

    /** A menu on the dropdown layer, left open by the driver so a dialog can be opened over it. */
    private static Div dropdown() {
        Dropdown menu = new Dropdown("Actions");
        menu.setId("dd-1");
        Button rename = new Button("Rename");
        rename.setId("dd-1-rename");
        menu.add(rename);

        Div row = new Div();
        row.add(menu);
        return row;
    }

    /** A drawer with two things in the panel, so the trap has a first and a last to wrap between. */
    private static Div drawer() {
        Drawer drawer = new Drawer("Settings");
        drawer.setId("drawer-1");

        Button first = new Button("First setting");
        first.setId("drawer-first");
        Button last = new Button("Last setting");
        last.setId("drawer-last");
        drawer.add(first, last);

        Button open = new Button("Open the drawer");
        open.setId("open-drawer");
        open.addClickListener(e -> drawer.open());
        drawer.addToPage(open);

        Div row = new Div();
        row.add(drawer);
        return row;
    }

    /** Not interactive. Here to prove it is on the top of the scale and that Escape hides it. */
    private static Div tooltip() {
        Tooltip tip = new Tooltip("The words the tip shows");
        tip.setId("tip-1");
        Button hover = new Button("Hover me");
        hover.setId("tip-1-target");
        tip.add(hover);

        Div row = new Div();
        row.add(tip);
        return row;
    }

    /**
     * A message, put on the page by a button so the driver can ask for a fresh one. Escape takes a
     * message away, which is the right behaviour and also means the driver cannot keep one around.
     */
    private static Div toast() {
        Div row = new Div();

        Button show = new Button("Show a message");
        show.setId("show-toast");
        show.addClickListener(e -> {
            Toast toast = new Toast();
            toast.setId("toast-1");
            toast.addClassName("toast-top toast-center");
            Div box = new Div("Saved");
            box.addClassName("alert alert-success");
            toast.add(box);
            row.add(toast);
        });

        row.add(show);
        return row;
    }
}
