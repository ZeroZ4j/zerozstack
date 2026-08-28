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

import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.Tab;
import com.zeroz4j.ui.layout.Div;
import java.util.ArrayList;
import java.util.List;

/**
 * Since 0.8.0 a tab is a real button with {@code setSelected}, so the keyboard can reach it and
 * only one of them says it is chosen. This page uses that, and asks the questions three tabs
 * called "Tab 1" never could: eleven of them, long headings, and one that is not available.
 */
public class TabShowcase extends ComponentShowcase {

    public TabShowcase() {
        super();
        addTitle("Tab");
        addDescription("A row of headings, one chosen at a time, with the chosen one's content "
                + "underneath. Each heading is a real button, so the keyboard reaches it.");

        addWhatToCheck("Try this",
                "Tab onto a heading and press Enter, then Space. Both should choose it.",
                "Check that choosing one un-chooses the one before it — never two at once.",
                "The panel underneath must change with the heading, and say which heading it belongs to.",
                "One heading in the last row is not available. Tab should skip it.",
                "Make the window narrow. Eleven headings should wrap onto more lines, not run off "
                        + "the side.",
                "Broken looks like: two headings both looking chosen, a heading Tab cannot reach, "
                        + "or the panel not changing when the heading does.");

        addSection("Four sections, with the content underneath", withPanels());
        addSection("Eleven headings, some of them long", manyTabs());
        addSection("Bordered and lifted", styles());
        addSection("One heading that is not available", withDisabled());
    }

    // ------------------------------------------------------------------ sections

    private static Component withPanels() {
        String[] names = { "Overview", "Members", "Billing history", "Danger zone" };
        String[] bodies = {
            "Everything about this workspace at a glance.",
            "The twelve people who can open this workspace, and what each of them may do.",
            "Every invoice since March 2024, and what was paid against it.",
            "Deleting the workspace happens here. Nothing on this page can be undone.",
        };

        Div bar = new Div();
        bar.setId("tabs-with-panels");
        bar.addClassName("tabs tabs-boxed w-full flex-wrap");
        bar.getElement().setAttribute("role", "tablist");

        Div panel = new Div();
        panel.setId("tabs-panel");
        panel.addClassName("rounded-box border border-base-300 p-4 mt-3 w-full");
        panel.getElement().setAttribute("role", "tabpanel");

        List<Tab> tabs = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            Tab tab = new Tab(names[i]);
            tab.setId("tab-" + i);
            tab.addClickListener(e -> {
                for (int j = 0; j < tabs.size(); j++) {
                    tabs.get(j).setSelected(j == index);
                }
                panel.setText(bodies[index]);
                // The panel says which heading it belongs to, so it is not an orphan box.
                panel.getElement().setAttribute("aria-labelledby", "tab-" + index);
            });
            tabs.add(tab);
            bar.add(tab);
        }
        tabs.get(0).setSelected(true);
        panel.setText(bodies[0]);
        panel.getElement().setAttribute("aria-labelledby", "tab-0");

        Div host = new Div();
        host.addClassName("w-full");
        host.add(bar, panel);
        return host;
    }

    private static Component manyTabs() {
        String[] names = {
            "All", "Open", "Waiting for the customer", "Waiting for us", "Escalated",
            "Closed this week", "Closed this month", "Deleted",
            "Rechnungsprüfung und Freigabe", "サポート案件", "Everything else",
        };
        Div bar = new Div();
        bar.setId("tabs-many");
        bar.addClassName("tabs tabs-bordered w-full flex-wrap");
        bar.getElement().setAttribute("role", "tablist");

        List<Tab> tabs = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            Tab tab = new Tab(names[i]);
            tab.addClickListener(e -> {
                for (int j = 0; j < tabs.size(); j++) {
                    tabs.get(j).setSelected(j == index);
                }
            });
            tabs.add(tab);
            bar.add(tab);
        }
        tabs.get(1).setSelected(true);
        return bar;
    }

    private static Component styles() {
        Div host = new Div();
        host.addClassName("flex flex-col gap-4 w-full");
        host.add(styled("tabs tabs-bordered", "Bordered"),
                styled("tabs tabs-lifted", "Lifted"),
                styled("tabs tabs-boxed", "Boxed"));
        return host;
    }

    private static Component styled(String classes, String name) {
        Div bar = new Div();
        bar.addClassName(classes + " w-full flex-wrap");
        bar.getElement().setAttribute("role", "tablist");
        List<Tab> tabs = new ArrayList<>();
        for (String label : new String[] { name + " one", name + " two", name + " three" }) {
            Tab tab = new Tab(label);
            tab.addClickListener(e -> {
                for (Tab other : tabs) {
                    other.setSelected(other == tab);
                }
            });
            tabs.add(tab);
            bar.add(tab);
        }
        tabs.get(0).setSelected(true);
        return bar;
    }

    private static Component withDisabled() {
        Div bar = new Div();
        bar.setId("tabs-with-disabled");
        bar.addClassName("tabs tabs-boxed w-full flex-wrap");
        bar.getElement().setAttribute("role", "tablist");

        Tab first = new Tab("Available");
        first.setSelected(true);
        Tab second = new Tab("Also available");
        Tab locked = new Tab("Not available on this plan");
        locked.setId("tab-locked");
        // Not available means not reachable: taken out of the keyboard's order and said out loud.
        locked.getElement().setAttribute("disabled", "true");
        locked.getElement().setAttribute("aria-disabled", "true");
        locked.getElement().setAttribute("tabindex", "-1");
        locked.addClassName("tab-disabled opacity-50");

        first.addClickListener(e -> {
            first.setSelected(true);
            second.setSelected(false);
        });
        second.addClickListener(e -> {
            first.setSelected(false);
            second.setSelected(true);
        });

        bar.add(first, second, locked);
        return bar;
    }
}
