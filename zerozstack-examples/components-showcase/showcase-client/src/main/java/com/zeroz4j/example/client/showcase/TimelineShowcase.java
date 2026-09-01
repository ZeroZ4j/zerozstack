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

import com.zeroz4j.ui.component.Timeline;
import com.zeroz4j.ui.layout.Div;

public class TimelineShowcase extends ComponentShowcase {

    public TimelineShowcase() {
        addTitle("Timeline");
        addDescription("Events in the order they happened, across the page or down it. Add them "
            + "one at a time; the line joining them is drawn for you.");

        Timeline across = new Timeline();
        across.addEvent("1984", "First Macintosh", "Apple released the first Macintosh computer.");
        across.addEvent("2001", "iPod launched", "Apple announced a portable media player.");
        across.addEvent("2007", "iPhone debut", "Steve Jobs introduced the iPhone.");
        addSection("Across the page", host(across));

        Timeline down = new Timeline().vertical();
        down.addEvent("09:14", "Order placed", "Paid by card, delivered to the office address.");
        down.addEvent("11:02", "Picked", "Two of the three items were in the first warehouse.");
        down.addEvent("14:30", "Left the depot", "On the van, seventh of nineteen stops.");
        down.addEvent("Tomorrow", "Expected");
        addSection("Down the page", host(down));

        Timeline wordy = new Timeline().vertical();
        wordy.addEvent("Monday, 3 August, 09:14 in the morning",
            "The overnight reconciliation job finished with warnings",
            "Four hundred and six rows matched, eleven did not, and one of those eleven is a "
                + "duplicate payment against an invoice that was already settled in June. None of "
                + "these words are shortened, cut or hidden behind a hover: the box stops growing "
                + "at about twenty rem and the text wraps inside it.");
        wordy.addEvent("Monday, 3 August, 09:20", "Somebody was told",
            "The finance mailbox got the report.");
        addSection("A deliberately long label - it wraps, and nothing is thrown away", host(wordy));
    }

    /** A timeline is as wide as the space it is given. */
    private static Div host(Timeline timeline) {
        Div box = new Div();
        box.addClassName("w-full");
        box.add(timeline);
        return box;
    }
}
