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

import com.zeroz4j.signals.Computed;
import com.zeroz4j.signals.ValueSignal;
import com.zeroz4j.ui.component.Select;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import com.zeroz4j.ui.theme.ThemeColor;
import com.zeroz4j.ui.theme.ThemeSize;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A picker with three options tells you nothing. This one has a hundred and forty, one of which
 * is a sentence, so what a long list does to the width of the control is visible.
 */
public class SelectShowcase extends ComponentShowcase {

    private static final List<String> MANY = manyOptions();

    public SelectShowcase() {
        super();
        addTitle("Select");
        addDescription("A box you pick one thing from. The lists here are as long and as wordy as "
                + "real ones get.");

        addWhatToCheck("Try this",
                "Open the long list with the keyboard: Tab to it, then press the down arrow or "
                        + "Alt and down arrow. Type the first letters of an entry to jump to it.",
                "The list of a hundred and forty has to scroll inside itself, not run off the screen.",
                "One entry is a whole sentence. Check the closed box does not stretch to fit it.",
                "The disabled box should be skipped by Tab. The read-only one should not.",
                "Broken looks like: the box growing as wide as its longest entry, an open list "
                        + "you cannot scroll, or a required box with no visible mark.");

        addSection("A hundred and forty time zones", longList());
        addSection("One entry that is a whole sentence", oneLongLabel());
        addSection("Caption, helper text and a required mark", withHelp());
        addSection("Wrong, and saying why", withError());
        addSection("Disabled and read-only", disabledAndReadOnly());
        addSection("Colours", colours());
        addSection("Sizes", sizes());
        addSection("Bound to a signal", bound());
    }

    // ------------------------------------------------------------------ sections

    private static Div longList() {
        Select select = new Select().withLabel("Time zone");
        select.setId("select-long-list");
        select.setItems(MANY);
        select.setValue("Europe/Berlin");
        select.setHelperText("A hundred and forty entries. Type the first letters to jump.");
        return host(select);
    }

    private static Div oneLongLabel() {
        List<String> items = new ArrayList<>();
        items.add("Standard");
        items.add("Keep every version of every file for seven years, including the ones that were "
                + "deleted, and make them searchable by the person who last changed them");
        items.add("Bare minimum");
        Select select = new Select().withLabel("Retention rule");
        select.setId("select-long-label");
        select.setItems(items);
        select.setValue(items.get(1));
        select.setHelperText("The middle entry is 150 characters long.");
        return host(select);
    }

    private static Div withHelp() {
        Select select = new Select().withLabel("Country");
        select.setId("select-required");
        select.setItems(Arrays.asList("", "Germany", "Austria", "Switzerland"));
        select.setHelperText("We send the invoice here.");
        select.setRequiredIndicatorVisible(true);
        return host(select);
    }

    private static Div withError() {
        Select select = new Select().withLabel("Payment method");
        select.setId("select-error");
        select.setItems(Arrays.asList("", "Card", "Direct debit", "Bank transfer"));
        select.setRequiredIndicatorVisible(true);
        select.setErrorMessage("Choose how you would like to pay.");
        return host(select);
    }

    private static Div disabledAndReadOnly() {
        Select disabled = new Select().withLabel("Plan (cannot be changed here)");
        disabled.setId("select-disabled");
        disabled.setItems(Arrays.asList("Free", "Team", "Enterprise"));
        disabled.setValue("Team");
        disabled.setEnabled(false);
        disabled.setHelperText("Disabled. Tab skips it, and a screen reader will not read it out.");

        Select readOnly = new Select().withLabel("Account number (read only)");
        readOnly.setId("select-readonly");
        readOnly.setItems(Arrays.asList("DE89 3704 0044 0532 0130 00"));
        readOnly.getElement().setAttribute("aria-readonly", "true");
        readOnly.setHelperText("Read only. Tab still reaches it, so it can still be read out.");

        Div host = new Div();
        host.addClassName("flex flex-col gap-4 w-full");
        host.add(disabled, readOnly);
        return host;
    }

    private static Div colours() {
        Div host = new Div();
        host.addClassName("flex flex-wrap gap-4 w-full");
        ThemeColor[] colours = { ThemeColor.PRIMARY, ThemeColor.SECONDARY, ThemeColor.ACCENT,
            ThemeColor.NEUTRAL, ThemeColor.INFO, ThemeColor.SUCCESS, ThemeColor.WARNING,
            ThemeColor.ERROR };
        String[] names = { "Primary", "Secondary", "Accent", "Neutral", "Info", "Success",
            "Warning", "Error" };
        for (int i = 0; i < colours.length; i++) {
            Select select = new Select().setThemeColor(colours[i]);
            select.withLabel(names[i]);
            select.setItems(Arrays.asList(names[i] + " one", names[i] + " two"));
            host.add(select);
        }
        return host;
    }

    private static Div sizes() {
        Div host = new Div();
        host.addClassName("flex flex-wrap items-end gap-4 w-full");
        ThemeSize[] sizes = { ThemeSize.XS, ThemeSize.SM, ThemeSize.MD, ThemeSize.LG };
        String[] names = { "Extra small", "Small", "Medium", "Large" };
        for (int i = 0; i < sizes.length; i++) {
            Select select = new Select().setThemeSize(sizes[i]);
            select.withLabel(names[i]);
            select.setItems(Arrays.asList("One", "Two", "Three"));
            host.add(select);
        }
        return host;
    }

    private static Div bound() {
        ValueSignal<String> signal = new ValueSignal<>("Europe/Berlin");
        Select select = new Select().withLabel("Time zone");
        select.setId("select-bound");
        select.setItems(MANY);
        select.bindValue(signal);

        Span readout = new Span();
        readout.addClassName("text-sm");
        readout.bindText(new Computed<>(() -> "Currently chosen: " + signal.get()));

        Div host = new Div();
        host.addClassName("flex flex-col gap-2 w-full");
        host.add(select, readout);
        return host;
    }

    // ------------------------------------------------------------------ helpers

    private static Div host(Select select) {
        Div host = new Div();
        host.addClassName("w-full max-w-sm");
        host.add(select);
        return host;
    }

    /** A hundred and forty real time-zone names, which is what a long list looks like. */
    private static List<String> manyOptions() {
        String[] cities = {
            "Abidjan", "Accra", "Addis Ababa", "Algiers", "Amsterdam", "Anchorage", "Andorra",
            "Ankara", "Antananarivo", "Asunción", "Athens", "Auckland", "Baghdad", "Baku",
            "Bangkok", "Barbados", "Beirut", "Belgrade", "Berlin", "Bermuda", "Bogotá",
            "Bratislava", "Brisbane", "Brussels", "Bucharest", "Budapest", "Buenos Aires",
            "Cairo", "Calcutta", "Caracas", "Casablanca", "Chicago", "Chisinau", "Colombo",
            "Copenhagen", "Dakar", "Damascus", "Dar es Salaam", "Denver", "Dhaka", "Dubai",
            "Dublin", "Edmonton", "Gibraltar", "Guatemala", "Halifax", "Havana", "Helsinki",
            "Ho Chi Minh", "Hong Kong", "Honolulu", "Istanbul", "Jakarta", "Jerusalem",
            "Johannesburg", "Kabul", "Karachi", "Kathmandu", "Khartoum", "Kiev", "Kigali",
            "Kuala Lumpur", "Kuwait", "Lagos", "La Paz", "Lima", "Lisbon", "Ljubljana", "London",
            "Los Angeles", "Luxembourg", "Madrid", "Malta", "Managua", "Manila", "Mexico City",
            "Minsk", "Monaco", "Monrovia", "Montevideo", "Moscow", "Nairobi", "Nassau",
            "New York", "Nicosia", "Nouakchott", "Oslo", "Panama", "Paramaribo", "Paris",
            "Perth", "Phnom Penh", "Prague", "Reykjavik", "Riga", "Riyadh", "Rome", "Samarkand",
            "San Juan", "Santiago", "São Paulo", "Sarajevo", "Seoul", "Shanghai", "Singapore",
            "Skopje", "Sofia", "Stockholm", "Sydney", "Taipei", "Tallinn", "Tashkent", "Tbilisi",
            "Tegucigalpa", "Tehran", "Thimphu", "Tirane", "Tokyo", "Toronto", "Tripoli", "Tunis",
            "Ulaanbaatar", "Vancouver", "Vienna", "Vientiane", "Vilnius", "Warsaw", "Winnipeg",
            "Yangon", "Yerevan", "Zagreb", "Zurich",
        };
        String[] regions = { "Africa", "America", "Asia", "Atlantic", "Australia", "Europe",
            "Indian", "Pacific" };
        List<String> options = new ArrayList<>();
        options.add("Europe/Berlin");
        int i = 0;
        for (String city : cities) {
            options.add(regions[i % regions.length] + "/" + city.replace(' ', '_'));
            i++;
        }
        return options;
    }
}
