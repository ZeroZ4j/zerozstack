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

import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.theme.TextStyle;
import org.teavm.jso.dom.html.HTMLElement;

import java.util.ArrayList;
import java.util.List;

/**
 * A row or column of events in the order they happened.
 *
 * <p>Each event has three parts: <b>when</b> it happened, <b>what</b> happened, and as much
 * description as you care to give. Add them one at a time:</p>
 *
 * <pre>{@code
 * Timeline history = new Timeline().vertical();
 * history.addEvent("09:14", "Order placed", "Paid by card, delivery to the office address.");
 * history.addEvent("11:02", "Picked", "Two of three items were in the first warehouse.");
 * history.addEvent("Tomorrow", "Out for delivery");
 * }</pre>
 *
 * <p>The line joining one event to the next is drawn for you and redrawn whenever an event is
 * added, so nothing has to be told which event is first or last.</p>
 *
 * <p><b>Long text wraps.</b> An event's words are never shortened and never cut: a long
 * description wraps onto more lines inside its box, and the box will not grow past about 20rem
 * wide - {@link #setEventWidth(String)} changes that, and {@code setEventWidth(null)} lets a box
 * be as wide as its text. A timeline laid out in a row scrolls sideways when it is longer than
 * the space it is in, rather than pushing the page out of shape.</p>
 *
 * <p>An event built by hand is still welcome: {@code add(myOwnListItem)} puts any component in as
 * a step, and only the events added through {@link #addEvent} get their connecting line managed.</p>
 */
public class Timeline extends Component implements HasComponents, HasStyle, HasSize {

    /**
     * One event on a timeline: when it happened, what happened, and the detail.
     *
     * <p>Created by {@link Timeline#addEvent}. Returned so it can be restyled or its parts reached
     * afterwards; there is no reason to build one directly.</p>
     */
    public static final class Item extends Component implements HasStyle {

        private final Div when = new Div();
        private final Div marker = new Div();
        private final Div box = new Div();
        private final Div title = new Div();
        private final Div detail = new Div();
        private final Component lineBefore = new Line();
        private final Component lineAfter = new Line();

        private Item(String whenText, String titleText, String detailText, String boxWidth) {
            super("li");
            when.addClassName("timeline-start whitespace-normal break-words");
            TextStyle.CAPTION.applyTo(when);
            when.setText(whenText == null ? "" : whenText);

            marker.addClassName("timeline-middle");
            marker.getElement().setInnerHTML(DOT);

            box.addClassName("timeline-end timeline-box whitespace-normal break-words");
            title.addClassName("font-semibold");
            title.setText(titleText == null ? "" : titleText);
            detail.addClassName("whitespace-normal break-words");
            TextStyle.CAPTION.applyTo(detail);
            detail.setText(detailText == null ? "" : detailText);
            if (detailText == null || detailText.isEmpty()) {
                detail.addClassName("hidden");
            }
            box.add(title, detail);
            setEventWidth(boxWidth);
        }

        /**
         * Caps how wide this event's box may grow before its text wraps.
         *
         * @param width any CSS length, or null for no cap at all
         */
        public void setEventWidth(String width) {
            if (width == null || width.isEmpty()) {
                box.getElement().getStyle().removeProperty("max-width");
                when.getElement().getStyle().removeProperty("max-width");
            } else {
                box.getElement().getStyle().setProperty("max-width", width);
                when.getElement().getStyle().setProperty("max-width", width);
            }
        }

        /**
         * Returns the box holding the event's words, for adding anything else to it.
         *
         * @return the event box
         */
        public Div getBox() {
            return box;
        }

        @Override
        public Component getComponent() {
            return this;
        }

        /** Rebuilds the row so the connecting lines sit either side of the marker. */
        private void relink(boolean hasLineBefore, boolean hasLineAfter) {
            HTMLElement li = getElement();
            while (li.getFirstChild() != null) {
                li.removeChild(li.getFirstChild());
            }
            if (hasLineBefore) {
                li.appendChild(lineBefore.getElement());
            }
            li.appendChild(when.getElement());
            li.appendChild(marker.getElement());
            li.appendChild(box.getElement());
            if (hasLineAfter) {
                li.appendChild(lineAfter.getElement());
            }
        }

        private static final class Line extends Component {
            private Line() {
                super("hr");
            }
        }

        private static final String DOT =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 20 20\" fill=\"currentColor\""
            + " class=\"h-4 w-4 opacity-70\" aria-hidden=\"true\"><circle cx=\"10\" cy=\"10\""
            + " r=\"4\"/></svg>";
    }

    private final List<Item> events = new ArrayList<>();
    private String eventWidth = "20rem";

    /** Creates an empty timeline laid out in a row. */
    public Timeline() {
        super("ul");
        addClassName("timeline overflow-x-auto");
    }

    /**
     * Adds an event to the end of the timeline.
     *
     * @param when when it happened, in whatever words suit - a date, a time, "Tomorrow"
     * @param title what happened, in a few words
     * @param detail the fuller description, or null for none
     * @return the new event, in case it needs styling
     */
    public Item addEvent(String when, String title, String detail) {
        Item item = new Item(when, title, detail, eventWidth);
        events.add(item);
        getElement().appendChild(item.getElement());
        relink();
        return item;
    }

    /**
     * Adds an event with no further description.
     *
     * @param when when it happened
     * @param title what happened
     * @return the new event
     */
    public Item addEvent(String when, String title) {
        return addEvent(when, title, null);
    }

    /**
     * Stands the timeline up so the events run down the page instead of across it.
     *
     * @param vertical true to run down the page
     */
    public void setVertical(boolean vertical) {
        if (vertical) {
            addClassName("timeline-vertical");
        } else {
            removeClassName("timeline-vertical");
        }
    }

    /**
     * Stands the timeline up and returns it, so it reads inside the expression that creates it.
     *
     * @return this timeline
     */
    public Timeline vertical() {
        setVertical(true);
        return this;
    }

    /**
     * Caps how wide an event's box may grow before its text wraps onto another line.
     *
     * <p>The cap is about 20rem to begin with, which keeps a line of description short enough to
     * read comfortably. Pass null to take the cap off, and a box becomes as wide as its longest
     * line of text. Either way no text is ever shortened.</p>
     *
     * @param width any CSS length, or null for no cap
     */
    public void setEventWidth(String width) {
        this.eventWidth = width;
        for (Item item : events) {
            item.setEventWidth(width);
        }
    }

    /**
     * Returns the width an event's box is capped at, or null when it is uncapped.
     *
     * @return the cap
     */
    public String getEventWidth() {
        return eventWidth;
    }

    @Override
    public Component getComponent() {
        return this;
    }

    private void relink() {
        for (int i = 0; i < events.size(); i++) {
            events.get(i).relink(i > 0, i < events.size() - 1);
        }
    }
}
