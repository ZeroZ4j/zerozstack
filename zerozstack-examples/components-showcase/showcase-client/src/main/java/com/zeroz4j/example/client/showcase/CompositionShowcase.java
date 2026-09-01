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
package com.zeroz4j.example.client.showcase;

import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.ContextMenu;
import com.zeroz4j.ui.component.Dialog;
import com.zeroz4j.ui.component.Drawer;
import com.zeroz4j.ui.component.Dropdown;
import com.zeroz4j.ui.component.Select;
import com.zeroz4j.ui.component.Table;
import com.zeroz4j.ui.component.TextField;
import com.zeroz4j.ui.component.Toast;
import com.zeroz4j.ui.component.Tooltip;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.FormLayout;
import com.zeroz4j.ui.layout.Span;
import java.util.Arrays;
import org.teavm.jso.browser.Window;

/**
 * One thing inside another inside another. Every overlay in this library works on its own; this
 * page is the only place that asks whether they still work when one is opened from inside another.
 */
public class CompositionShowcase extends ComponentShowcase {

    /** Everything that happened, newest last, so the reader can see one action means one event. */
    private final Div log = new Div("Nothing has happened yet.");

    private int toastCount;

    public CompositionShowcase() {
        super();
        addTitle("One thing inside another");
        addDescription("A panel that slides in from the side, holding a form, holding a button "
                + "that opens a box over the page, holding a menu that opens another box. Each of "
                + "these works on its own. This page asks whether they still work together.");

        addWhatToCheck("Try this, with the keyboard only",
                "Open the side panel. The cursor should land inside it, not stay on the page behind.",
                "From the form inside it, open the box. Tab should now stay inside the box.",
                "Open the drop-down list inside that box. It should appear over the box, not behind it.",
                "Open the second box from the first. Escape should close only the second one.",
                "Ask for a message while a box is open. The message must be readable, not hidden.",
                "Close everything. The cursor should come back to the button you started from.",
                "Broken looks like: a list that appears behind the box, Escape closing two things "
                        + "at once, or the cursor jumping to the top of the page.");

        log.setId("composition-log");
        log.addClassName("text-sm font-mono whitespace-pre-line");

        addSection("Start here", drawerSection());
        addSection("What happened", log);
    }

    // ------------------------------------------------------------------ the stack

    private Component[] drawerSection() {
        Drawer drawer = new Drawer("Order settings");
        drawer.setId("composition-drawer");
        drawer.addClassName("h-96 border border-base-300 rounded-box overflow-hidden");

        Dialog first = firstDialog();
        Dialog second = secondDialog();
        wire(first, second);

        // The form lives inside the drawer, and its button opens the dialog.
        TextField reference = new TextField().withLabel("Order reference");
        reference.setId("composition-reference");
        reference.setValue("ORD-2026-0042");

        Select shipping = new Select().withLabel("Shipping");
        shipping.setId("composition-shipping");
        shipping.setItems(Arrays.asList("Standard", "Express", "Collect in person"));
        shipping.setValue("Express");

        Button openFirst = new Button("Choose the delivery address");
        openFirst.setId("composition-open-dialog");
        openFirst.addClassName("btn-primary");
        openFirst.addClickListener(e -> {
            note("Box opened from the form inside the side panel.");
            first.open();
        });

        FormLayout form = new FormLayout();
        form.add(reference, shipping, openFirst);
        drawer.add(form, new Span("The side panel is still open behind the box."));

        Button openDrawer = new Button("Open the side panel");
        openDrawer.setId("composition-open-drawer");
        openDrawer.addClassName("btn-primary");
        openDrawer.addClickListener(e -> {
            note("Side panel opened.");
            drawer.open();
        });

        drawer.addCloseListener(e -> note("Side panel closed."));

        Div page = new Div();
        page.addClassName("flex flex-col items-center justify-center gap-4 h-full p-4 text-center");
        page.add(new Span("This is the ordinary page. The side panel covers it."), openDrawer);
        drawer.addToPage(page);

        return new Component[] { drawer, first, second };
    }

    /** The box opened from the form: a drop-down, a right-click menu, a table and a second box. */
    private Dialog firstDialog() {
        Dialog dialog = new Dialog("Delivery address");
        dialog.setId("composition-dialog-1");
        dialog.setWidth("42rem");

        Select country = new Select().withLabel("Country");
        country.setId("composition-country");
        country.setItems(Arrays.asList("Germany", "Austria", "Switzerland", "Netherlands", "Japan"));
        country.setValue("Germany");

        // A menu opened from a control inside a box. The library puts menus one tier below boxes,
        // so this is the case that tells you whether that is enough.
        Dropdown dropdown = new Dropdown("Saved addresses");
        dropdown.setId("composition-dropdown");
        dropdown.add(addressItem("Head office, Berlin"), addressItem("Warehouse, Hamburg"),
                addressItem("Home, München"));

        Table table = addressTable();

        // Right-click any row. The same question as the drop-down, asked a second way.
        ContextMenu rowMenu = new ContextMenu();
        rowMenu.item("copy", "Copy this row", () -> note("Right-click menu: copied a row."));
        rowMenu.item("x", "Remove this row", () -> note("Right-click menu: removed a row."));
        rowMenu.attachTo(table, null);

        Button toastButton = new Button("Tell me it was saved");
        toastButton.setId("composition-toast");
        toastButton.addClickListener(e -> raiseToast("Address saved while the box was open."));

        Tooltip tip = new Tooltip("A tip attached to a control inside a box.");
        Button tipTarget = new Button("Point at me");
        tipTarget.setId("composition-tooltip-target");
        tip.add(tipTarget);

        Div body = new Div();
        body.addClassName("flex flex-col gap-4");
        body.add(country, dropdown, table, toastButton, tip);
        dialog.add(body);

        return dialog;
    }

    private Dialog secondDialog() {
        Dialog dialog = new Dialog("Is this address right?");
        dialog.setId("composition-dialog-2");
        dialog.add(new Div("Hauptstraße 14, 10827 Berlin. Escape should close this box and leave "
                + "the first one open behind it."));
        Button yes = new Button("Yes, use it");
        yes.setId("composition-dialog-2-yes");
        yes.addClassName("btn-primary");
        yes.addClickListener(e -> dialog.close());
        dialog.addAction(yes);
        dialog.addCloseListener(e -> note("Second box closed."));
        return dialog;
    }

    private void wire(Dialog first, Dialog second) {
        Button openSecond = new Button("Check this address");
        openSecond.setId("composition-open-dialog-2");
        openSecond.addClickListener(e -> {
            note("Second box opened from inside the first.");
            second.open();
        });
        Button close = new Button("Close");
        close.setId("composition-dialog-1-close");
        close.addClickListener(e -> first.close());
        first.addAction(openSecond);
        first.addAction(close);
        first.addCloseListener(e -> note("First box closed."));
    }

    // ------------------------------------------------------------------ helpers

    /** A message asked for while a box is open. It has to be readable, or it is no message. */
    private void raiseToast(String text) {
        toastCount++;
        Toast toast = new Toast(text + " (" + toastCount + ")");
        toast.setId("composition-toast-" + toastCount);
        toast.addClassName("toast-top toast-end");
        Window.current().getDocument().getBody().appendChild(toast.getElement());
        note("Message raised: " + text);
        Window.setTimeout(toast::close, 6000);
    }

    private static Component addressItem(String label) {
        Button item = new Button(label);
        item.addClassName("btn-ghost justify-start w-full");
        return item;
    }

    private static Table addressTable() {
        Table table = new Table();
        table.setId("composition-table");
        table.addClassName("table-zebra table-sm");
        Component thead = element("thead");
        Component headRow = element("tr");
        headRow.getElement().appendChild(cell("th", "Name").getElement());
        headRow.getElement().appendChild(cell("th", "Street").getElement());
        headRow.getElement().appendChild(cell("th", "Town").getElement());
        thead.getElement().appendChild(headRow.getElement());

        Component tbody = element("tbody");
        tbody.getElement().appendChild(row("Head office", "Hauptstraße 14", "Berlin").getElement());
        tbody.getElement().appendChild(row("Warehouse", "Speicherstadt 3", "Hamburg").getElement());
        tbody.getElement().appendChild(row("Home", "Leopoldstraße 90", "München").getElement());

        table.getElement().appendChild(thead.getElement());
        table.getElement().appendChild(tbody.getElement());
        return table;
    }

    private static Component row(String a, String b, String c) {
        Component tr = element("tr");
        tr.getElement().appendChild(cell("td", a).getElement());
        tr.getElement().appendChild(cell("td", b).getElement());
        tr.getElement().appendChild(cell("td", c).getElement());
        return tr;
    }

    private static Component cell(String tag, String text) {
        Component cell = element(tag);
        cell.getElement().setTextContent(text);
        return cell;
    }

    private static Component element(String tag) {
        return new Component(tag) {
        };
    }

    private void note(String text) {
        String current = log.getElement().getTextContent();
        if (current == null || current.startsWith("Nothing has happened")) {
            current = "";
        }
        log.getElement().setTextContent(current.isEmpty() ? text : current + "\n" + text);
    }
}
