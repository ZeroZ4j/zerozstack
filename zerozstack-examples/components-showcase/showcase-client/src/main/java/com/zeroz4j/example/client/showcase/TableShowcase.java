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
import com.zeroz4j.ui.component.Table;
import com.zeroz4j.ui.component.mixin.HasLayer;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.theme.Layer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Two hundred rows of plausible data rather than Alice and Bob: names in four scripts, a row with
 * a value nobody ever filled in, a header that stays put while the body scrolls, a header the
 * keyboard can sort by, and a total line that is not one of the rows.
 */
public class TableShowcase extends ComponentShowcase {

    /** One person in the list. A null value for {@code requests} means nobody ever recorded it. */
    private record Person(int id, String name, String team, String country, Integer requests,
                          String lastSeen) {
    }

    private static final String[] FIRST_NAMES = {
        "Anna", "Jürgen", "Sofía", "田中", "أحمد", "Malin", "Grzegorz", "Íñigo", "Nguyễn",
        "Katharina", "佐藤", "Ольга", "Mehmet", "Åsa", "François",
    };

    private static final String[] LAST_NAMES = {
        "Bergström", "Schröder", "Hernández", "花子", "الفارسي", "Lindqvist", "Wiśniewski",
        "Etxeberria", "Thị Hương", "Müller-Lüdenscheidt", "健太郎", "Петрова", "Yıldırım",
        "Ólafsdóttir", "Bérenger-Dupont",
    };

    private static final String[] TEAMS = {
        "Platform", "Billing", "Customer support", "Data", "Security",
        "Zahlungsabwicklung und Rechnungsstellung", "サポート", "Research",
    };

    private static final String[] COUNTRIES = {
        "Germany", "Sweden", "Spain", "Japan", "United Arab Emirates", "Poland", "Vietnam",
        "Türkiye", "Iceland", "France",
    };

    private final List<Person> people = build();

    /** Which column the list is sorted by, and which way. */
    private int sortColumn = 0;
    private boolean descending;

    private final Component tbody = element("tbody");
    private final Component headRow = element("tr");

    public TableShowcase() {
        super();
        addTitle("Table");
        addDescription("Two hundred people, sorted by any column, with the header staying put "
                + "while the rows scroll under it.");

        addWhatToCheck("Try this",
                "Tab to a column heading and press Enter or Space. The list should re-sort and say "
                        + "which way it is now sorted.",
                "Scroll the list. The header must stay where it is and stay readable.",
                "Find the row whose request count was never recorded. It should say so, not show "
                        + "a zero and not be blank.",
                "The last line is a total, not a person. It should not move when you sort.",
                "Names here are German, Japanese, Arabic and Vietnamese. Nothing should be cut off "
                        + "or squashed.",
                "Broken looks like: a heading you cannot reach with the keyboard, a header that "
                        + "scrolls away, an empty cell with no explanation, or the total sorting "
                        + "itself in among the people.");

        addSection("Two hundred people", scrollingTable());
        addSection("The same table, small and dense", smallTable());
    }

    // ------------------------------------------------------------------ the big table

    private Component scrollingTable() {
        Table table = new Table();
        table.setId("table-people");
        table.addClassName("table table-zebra table-pin-rows w-full");

        Component thead = element("thead");
        // A header that stays put is furniture on the sticky tier. Nothing here picks a number.
        HasLayer.applyTo(thead, Layer.STICKY);
        addClass(thead, "sticky top-0 bg-base-200");

        addHeading(headRow, "#", 0);
        addHeading(headRow, "Name", 1);
        addHeading(headRow, "Team", 2);
        addHeading(headRow, "Country", 3);
        addHeading(headRow, "Requests", 4);
        addHeading(headRow, "Last seen", 5);
        thead.getElement().appendChild(headRow.getElement());

        renderRows();

        // A total is not a person, so it lives in the table's foot and never takes part in a sort.
        Component tfoot = element("tfoot");
        Component totalRow = element("tr");
        totalRow.setId("table-total-row");
        addClass(totalRow, "font-semibold bg-base-200");
        totalRow.getElement().appendChild(cell("td", "").getElement());
        totalRow.getElement().appendChild(cell("td", "Total of " + people.size() + " people")
                .getElement());
        totalRow.getElement().appendChild(cell("td", "").getElement());
        totalRow.getElement().appendChild(cell("td", "").getElement());
        totalRow.getElement().appendChild(cell("td", String.valueOf(totalRequests())).getElement());
        totalRow.getElement().appendChild(cell("td", "").getElement());
        tfoot.getElement().appendChild(totalRow.getElement());

        table.getElement().appendChild(thead.getElement());
        table.getElement().appendChild(tbody.getElement());
        table.getElement().appendChild(tfoot.getElement());

        Div host = new Div();
        host.addClassName("w-full h-96 overflow-auto rounded-box border border-base-300");
        host.add(table);
        return host;
    }

    /** A heading that can be pressed. It is a real button, so the keyboard can reach it. */
    private void addHeading(Component row, String label, int column) {
        Component th = element("th");
        th.getElement().setAttribute("scope", "col");
        Button button = new Button(label + " ");
        button.addClassName("btn-ghost btn-xs px-1 font-semibold");
        button.addClickListener(e -> sortBy(column));
        th.getElement().appendChild(button.getElement());
        row.getElement().appendChild(th.getElement());
        markSort(th, column);
    }

    private void sortBy(int column) {
        if (sortColumn == column) {
            descending = !descending;
        } else {
            sortColumn = column;
            descending = false;
        }
        Comparator<Person> comparator = switch (column) {
            case 1 -> Comparator.comparing(Person::name);
            case 2 -> Comparator.comparing(Person::team);
            case 3 -> Comparator.comparing(Person::country);
            // A value nobody recorded sorts to the end either way, rather than pretending to be nought.
            case 4 -> Comparator.comparing(Person::requests,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case 5 -> Comparator.comparing(Person::lastSeen);
            default -> Comparator.comparingInt(Person::id);
        };
        people.sort(descending ? comparator.reversed() : comparator);
        renderRows();
        for (int i = 0; i < 6; i++) {
            markSort(headRow.getElement().querySelectorAll("th").item(i), i);
        }
    }

    private void markSort(Component th, int column) {
        markSort(th.getElement(), column);
    }

    /** Says which way the list is sorted, for somebody who cannot see the arrow. */
    private void markSort(org.teavm.jso.dom.html.HTMLElement th, int column) {
        if (th == null) {
            return;
        }
        if (sortColumn == column) {
            th.setAttribute("aria-sort", descending ? "descending" : "ascending");
        } else {
            th.removeAttribute("aria-sort");
        }
    }

    private void renderRows() {
        while (tbody.getElement().getLastChild() != null) {
            tbody.getElement().removeChild(tbody.getElement().getLastChild());
        }
        for (Person person : people) {
            Component tr = element("tr");
            tr.getElement().appendChild(cell("td", String.valueOf(person.id())).getElement());
            Component name = cell("td", person.name());
            name.getElement().setAttribute("scope", "row");
            tr.getElement().appendChild(name.getElement());
            tr.getElement().appendChild(cell("td", person.team()).getElement());
            tr.getElement().appendChild(cell("td", person.country()).getElement());
            tr.getElement().appendChild(requestsCell(person).getElement());
            tr.getElement().appendChild(cell("td", person.lastSeen()).getElement());
            tbody.getElement().appendChild(tr.getElement());
        }
    }

    /** A value nobody recorded says so in words, rather than being blank or pretending to be nought. */
    private static Component requestsCell(Person person) {
        Component td = element("td");
        addClass(td, "text-right tabular-nums");
        if (person.requests() == null) {
            td.getElement().setTextContent("not recorded");
            addClass(td, "italic text-base-content/50");
        } else {
            td.getElement().setTextContent(String.valueOf(person.requests()));
        }
        return td;
    }

    // ------------------------------------------------------------------ the small table

    private Component smallTable() {
        Table table = new Table();
        table.setId("table-people-small");
        table.addClassName("table table-xs table-zebra w-full");

        Component thead = element("thead");
        Component row = element("tr");
        for (String label : new String[] { "#", "Name", "Team", "Requests" }) {
            Component th = cell("th", label);
            th.getElement().setAttribute("scope", "col");
            row.getElement().appendChild(th.getElement());
        }
        thead.getElement().appendChild(row.getElement());

        Component body = element("tbody");
        for (int i = 0; i < 12; i++) {
            Person person = people.get(i);
            Component tr = element("tr");
            tr.getElement().appendChild(cell("td", String.valueOf(person.id())).getElement());
            tr.getElement().appendChild(cell("td", person.name()).getElement());
            tr.getElement().appendChild(cell("td", person.team()).getElement());
            tr.getElement().appendChild(requestsCell(person).getElement());
            body.getElement().appendChild(tr.getElement());
        }

        table.getElement().appendChild(thead.getElement());
        table.getElement().appendChild(body.getElement());

        Div host = new Div();
        host.addClassName("w-full overflow-x-auto rounded-box border border-base-300");
        host.add(table);
        return host;
    }

    // ------------------------------------------------------------------ the data

    private static List<Person> build() {
        DemoData data = new DemoData(20260828L);
        List<Person> list = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            String name = FIRST_NAMES[(int) (data.pick() * FIRST_NAMES.length) % FIRST_NAMES.length]
                    + " " + LAST_NAMES[(int) (data.pick() * LAST_NAMES.length) % LAST_NAMES.length];
            // Two rows out of two hundred were never filled in, which is what real data looks like.
            Integer requests = (i == 7 || i == 143) ? null : (int) (data.pick() * 9000) + 12;
            list.add(new Person(i, name,
                    TEAMS[(int) (data.pick() * TEAMS.length) % TEAMS.length],
                    COUNTRIES[(int) (data.pick() * COUNTRIES.length) % COUNTRIES.length],
                    requests,
                    (1 + (int) (data.pick() * 28)) + " Aug 2026"));
        }
        return list;
    }

    private int totalRequests() {
        int total = 0;
        for (Person person : people) {
            if (person.requests() != null) {
                total += person.requests();
            }
        }
        return total;
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

    /** A plain element is not a styled component, so its classes are set on the element itself. */
    private static void addClass(Component component, String extra) {
        String current = component.getElement().getClassName();
        component.getElement().setClassName(
                current == null || current.isEmpty() ? extra : current + " " + extra);
    }
}
