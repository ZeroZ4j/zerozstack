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

import com.zeroz4j.ui.component.Badge;
import com.zeroz4j.ui.component.Breadcrumbs;
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.Dialog;
import com.zeroz4j.ui.component.Dropdown;
import com.zeroz4j.ui.component.KpiTile;
import com.zeroz4j.ui.component.Link;
import com.zeroz4j.ui.component.Menu;
import com.zeroz4j.ui.component.Select;
import com.zeroz4j.ui.component.Tab;
import com.zeroz4j.ui.component.Table;
import com.zeroz4j.ui.component.TextField;
import com.zeroz4j.ui.component.Timeline;
import com.zeroz4j.ui.component.Tooltip;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import java.util.Arrays;

/**
 * Every text-bearing component given text it was not designed for: a German compound noun, a
 * Japanese sentence, an Arabic sentence that runs right to left, and 400 characters with no space
 * in them anywhere.
 */
public class LongTextShowcase extends ComponentShowcase {

    /** A real German legal compound. Sixty-three letters, no space to break at. */
    private static final String GERMAN =
            "Rindfleischetikettierungsüberwachungsaufgabenübertragungsgesetz";

    /** Longer still, and the one that pushes a page sideways if anything does. */
    private static final String GERMAN_LONGER =
            "Donaudampfschifffahrtselektrizitätenhauptbetriebswerkbauunterbeamtengesellschaft";

    /** A Japanese sentence. No spaces at all, and the browser has to break it by character. */
    private static final String JAPANESE =
            "この画面は、非常に長い日本語の文章が表のセルやタブの見出しの中でどのように"
            + "折り返されるかを確かめるためのものです。";

    /** An Arabic sentence. It runs right to left, so the whole element has to know that. */
    private static final String ARABIC =
            "هذه جملة عربية طويلة تستخدم للتحقق مما إذا كان النص الذي يبدأ من اليمين "
            + "وينتهي إلى اليسار يظهر بشكل صحيح داخل الجداول والقوائم والعناوين.";

    /** Four hundred characters with nothing to break at. */
    private static final String UNBREAKABLE = unbreakable();

    public LongTextShowcase() {
        super();
        addTitle("Long words and other languages");
        addDescription("Every part of this page holds text that is longer than the space it was "
                + "given, in four different scripts. The point is to find what breaks.");

        addWhatToCheck("Try this",
                "Scroll the page sideways. If it moves at all, something has pushed it out.",
                "Look at every table cell, tab heading and menu entry. Text may wrap or fade out "
                        + "at the edge, but it must not be cut off with no way to read it.",
                "The Arabic lines have to start at the right-hand edge and run leftwards.",
                "Where text is shortened, check the whole text is still in the page — a tip on "
                        + "pointing, or the title attribute — and not thrown away in Java.",
                "Make the window narrow and look again.",
                "Broken looks like: a sideways scrollbar on the whole page, a word running out "
                        + "over the panel next to it, or a name that no longer says which row it is.");

        addSection("The four texts", theTexts());
        addSection("Table cells", table());
        addSection("Tab headings", tabs());
        addSection("Menu entries", menu());
        addSection("Breadcrumbs", breadcrumbs());
        addSection("Badges and chips", badges());
        addSection("KPI tiles", kpiTiles());
        addSection("Timeline events", timeline());
        addSection("Tooltips", tooltips());
        addSection("Dialog titles", dialogs());
        addSection("Field captions, helper text and options", fields());
    }

    // ------------------------------------------------------------------ sections

    private Component[] theTexts() {
        Div box = new Div();
        box.addClassName("flex flex-col gap-3 w-full");
        box.add(labelled("German, 63 letters", GERMAN));
        box.add(labelled("German, 80 letters", GERMAN_LONGER));
        box.add(labelled("Japanese", JAPANESE));
        box.add(rtl(labelled("Arabic, right to left", ARABIC)));
        box.add(labelled("400 characters, nothing to break at", UNBREAKABLE));
        return new Component[] { box };
    }

    private Component[] table() {
        Table table = new Table();
        table.setId("long-text-table");
        table.addClassName("table-zebra table-sm w-full");

        Component thead = element("thead");
        Component headRow = element("tr");
        headRow.getElement().appendChild(cell("th", "Name").getElement());
        headRow.getElement().appendChild(cell("th", GERMAN).getElement());
        headRow.getElement().appendChild(cell("th", "Note").getElement());
        thead.getElement().appendChild(headRow.getElement());

        Component tbody = element("tbody");
        tbody.getElement().appendChild(
                row("German", GERMAN_LONGER, "A real compound noun.").getElement());
        tbody.getElement().appendChild(
                row("Japanese", JAPANESE, "No spaces anywhere.").getElement());
        Component arabicRow = row("Arabic", ARABIC, "Runs right to left.");
        arabicRow.getElement().querySelectorAll("td").item(1).setAttribute("dir", "rtl");
        tbody.getElement().appendChild(arabicRow.getElement());
        tbody.getElement().appendChild(
                row("Unbreakable", UNBREAKABLE, "400 characters, one word.").getElement());

        table.getElement().appendChild(thead.getElement());
        table.getElement().appendChild(tbody.getElement());

        Div host = new Div();
        host.addClassName("w-full overflow-x-auto");
        host.add(table);
        return new Component[] { host };
    }

    private Component[] tabs() {
        Div bar = new Div();
        bar.setId("long-text-tabs");
        bar.addClassName("tabs tabs-boxed w-full flex-wrap");

        Tab first = new Tab(GERMAN);
        first.setSelected(true);
        Tab second = new Tab(JAPANESE);
        Tab third = new Tab(ARABIC);
        third.getElement().setAttribute("dir", "rtl");
        Tab fourth = new Tab(UNBREAKABLE);

        for (Tab tab : new Tab[] { first, second, third, fourth }) {
            tab.addClickListener(e -> {
                first.setSelected(tab == first);
                second.setSelected(tab == second);
                third.setSelected(tab == third);
                fourth.setSelected(tab == fourth);
            });
        }
        bar.add(first, second, third, fourth);
        return new Component[] { bar };
    }

    private Component[] menu() {
        Div readout = new Div("Nothing chosen yet.");
        readout.setId("long-text-menu-readout");
        readout.addClassName("text-sm text-base-content/60 mt-2 break-words");

        Menu menu = new Menu();
        menu.setId("long-text-menu");
        menu.addClassName("bg-base-100 rounded-box w-full");
        menu.addTitle(GERMAN);
        menu.addItem(GERMAN_LONGER, e -> readout.setText("Chosen: " + GERMAN_LONGER));
        menu.addItem(JAPANESE, e -> readout.setText("Chosen: " + JAPANESE));
        menu.addItem(ARABIC, e -> readout.setText("Chosen: " + ARABIC));
        menu.addItem(UNBREAKABLE, e -> readout.setText("Chosen: 400 characters with no space"));
        menu.addLink("A link with a very long name: " + GERMAN, "#long-text-menu");

        Dropdown dropdown = new Dropdown(GERMAN);
        dropdown.setId("long-text-dropdown");
        Button japaneseEntry = new Button(JAPANESE,
                e -> readout.setText("Chosen from the drop-down: " + JAPANESE));
        japaneseEntry.addClassName("btn-ghost btn-sm justify-start w-full font-normal");
        Button germanEntry = new Button(GERMAN_LONGER,
                e -> readout.setText("Chosen from the drop-down: " + GERMAN_LONGER));
        germanEntry.addClassName("btn-ghost btn-sm justify-start w-full font-normal");
        dropdown.add(japaneseEntry, germanEntry);

        Div host = new Div();
        host.addClassName("flex flex-col gap-4 w-full");
        host.add(menu, dropdown, readout);
        return new Component[] { host };
    }

    private Component[] breadcrumbs() {
        Breadcrumbs crumbs = new Breadcrumbs();
        crumbs.setId("long-text-breadcrumbs");
        crumbs.addClassName("text-sm w-full");
        crumbs.withAriaLabel("Where you are");

        Component list = element("ul");
        list.getElement().appendChild(crumb(new Link("Home", "#long-text-breadcrumbs")).getElement());
        list.getElement().appendChild(crumb(new Link(GERMAN, "#long-text-breadcrumbs")).getElement());
        list.getElement().appendChild(crumb(new Link(JAPANESE, "#long-text-breadcrumbs")).getElement());
        Component last = element("li");
        last.getElement().setTextContent(GERMAN_LONGER);
        list.getElement().appendChild(last.getElement());
        crumbs.getElement().appendChild(list.getElement());

        Div host = new Div();
        host.addClassName("w-full overflow-x-auto");
        host.add(crumbs);
        return new Component[] { host };
    }

    private Component[] badges() {
        Div host = new Div();
        host.addClassName("flex flex-wrap gap-2 w-full");
        Badge german = new Badge(GERMAN);
        german.addClassName("badge-outline");
        Badge japanese = new Badge(JAPANESE);
        japanese.addClassName("badge-primary");
        Badge arabic = new Badge(ARABIC);
        arabic.addClassName("badge-secondary");
        arabic.getElement().setAttribute("dir", "rtl");
        Badge unbreakable = new Badge(UNBREAKABLE.substring(0, 120));
        unbreakable.addClassName("badge-accent");
        host.add(german, japanese, arabic, unbreakable);
        return new Component[] { host };
    }

    private Component[] kpiTiles() {
        Div host = new Div();
        host.addClassName("grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 w-full");
        host.add(new KpiTile(GERMAN).value("1.284", "Vorgänge"));
        host.add(new KpiTile(JAPANESE).value("98,7", "%"));
        KpiTile arabic = new KpiTile(ARABIC).value("42", "طلب");
        arabic.getElement().setAttribute("dir", "rtl");
        host.add(arabic);
        host.add(new KpiTile(UNBREAKABLE.substring(0, 80)).value("7"));
        return new Component[] { host };
    }

    private Component[] timeline() {
        Timeline timeline = new Timeline();
        timeline.setId("long-text-timeline");
        timeline.vertical();
        timeline.addEvent("08:00", GERMAN, "A short detail.");
        timeline.addEvent("09:30", JAPANESE, GERMAN_LONGER);
        timeline.addEvent("11:15", "Arabic", ARABIC);
        timeline.addEvent("14:45", "Four hundred characters", UNBREAKABLE);

        Div host = new Div();
        host.addClassName("w-full");
        host.add(timeline);
        return new Component[] { host };
    }

    private Component[] tooltips() {
        Div host = new Div();
        host.addClassName("flex flex-wrap gap-4 w-full");

        Tooltip germanTip = new Tooltip(GERMAN_LONGER);
        germanTip.add(new Button("Point at me for a very long German word"));

        Tooltip japaneseTip = new Tooltip(JAPANESE);
        japaneseTip.add(new Button("Point at me for a Japanese sentence"));

        Tooltip unbreakableTip = new Tooltip(UNBREAKABLE);
        unbreakableTip.add(new Button("Point at me for 400 characters"));

        host.add(germanTip, japaneseTip, unbreakableTip);
        return new Component[] { host };
    }

    private Component[] dialogs() {
        Div host = new Div();
        host.addClassName("flex flex-wrap gap-3 w-full");

        host.add(dialogButton("long-text-dialog-german", GERMAN_LONGER,
                "The heading above is one word of eighty letters."));
        host.add(dialogButton("long-text-dialog-japanese", JAPANESE,
                "The heading above is a Japanese sentence with no spaces in it."));
        host.add(dialogButton("long-text-dialog-unbreakable", UNBREAKABLE,
                "The heading above is four hundred characters with nothing to break at."));
        return new Component[] { host };
    }

    private Component[] fields() {
        TextField field = new TextField().withLabel(GERMAN_LONGER);
        field.setId("long-text-field");
        field.setHelperText(JAPANESE);
        field.setValue(UNBREAKABLE.substring(0, 100));

        TextField arabicField = new TextField().withLabel(ARABIC);
        arabicField.setId("long-text-field-arabic");
        arabicField.getElement().setAttribute("dir", "rtl");
        arabicField.setHelperText(ARABIC);

        Select select = new Select().withLabel("Choose one");
        select.setId("long-text-select");
        select.setItems(Arrays.asList(GERMAN, GERMAN_LONGER, JAPANESE, ARABIC,
                UNBREAKABLE.substring(0, 200)));

        Div host = new Div();
        host.addClassName("flex flex-col gap-4 w-full");
        host.add(field, arabicField, select);
        return new Component[] { host };
    }

    // ------------------------------------------------------------------ helpers

    private static Component dialogButton(String id, String title, String body) {
        Dialog dialog = new Dialog(title);
        dialog.setId(id);
        dialog.add(new Div(body));
        Button close = new Button("Close", e -> dialog.close());
        close.addClassName("btn-primary");
        dialog.addAction(close);

        Button open = new Button("Open " + id.substring("long-text-dialog-".length()));
        open.setId(id + "-open");
        open.addClickListener(e -> dialog.open());

        Div wrapper = new Div();
        wrapper.add(open, dialog);
        return wrapper;
    }

    private static Div labelled(String what, String text) {
        Div box = new Div();
        box.addClassName("rounded-box border border-base-300 p-3 w-full min-w-0");
        Span caption = new Span(what);
        caption.addClassName("block text-xs uppercase tracking-wide text-base-content/50 mb-1");
        Div value = new Div(text);
        value.addClassName("break-words");
        box.add(caption, value);
        return box;
    }

    private static Div rtl(Div box) {
        box.getElement().setAttribute("dir", "rtl");
        return box;
    }

    private static Component crumb(Link link) {
        Component li = element("li");
        li.getElement().appendChild(link.getOuterElement());
        return li;
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
        // The whole text stays in the page even where the design shortens it.
        cell.getElement().setAttribute("title", text);
        return cell;
    }

    private static Component element(String tag) {
        return new Component(tag) {
        };
    }

    private static String unbreakable() {
        StringBuilder sb = new StringBuilder();
        String[] chunks = { "keinLeerzeichen", "noSpaceHere", "sinEspacio", "pasDespace" };
        int i = 0;
        while (sb.length() < 400) {
            sb.append(chunks[i % chunks.length]);
            i++;
        }
        return sb.substring(0, 400);
    }
}
