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

import com.zeroz4j.ui.component.LaneTimeline;
import com.zeroz4j.ui.layout.Div;
import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.core.JSDate;

public class LaneTimelineShowcase extends ComponentShowcase {

    public LaneTimelineShowcase() {
        super();
        addTitle("Lane Timeline");
        addDescription("A replay control: one lane per session, coloured by outcome, with event "
            + "ticks and a draggable cursor that drives a time-travel signal. Play at 1x, 4x or "
            + "16x, or click Live to return to the present. Distinct from State Timeline, which "
            + "is a read-only view of arbitrary states with no cursor.");

        long now = (long) JSDate.now();
        long start = now - 8 * 60_000L;
        DemoData data = new DemoData(1717L);

        List<LaneTimeline.Lane> lanes = new ArrayList<>();
        lanes.add(lane("worker-0 qwen36-27b", "COMPLETED", start, start + 5 * 60_000L, data, 6));
        lanes.add(lane("worker-1 sonnet-4-6", "RUNNING", start + 60_000L, 0, data, 4));
        lanes.add(lane("worker-2 gpt-oss-120b", "FAILED", start + 30_000L, start + 3 * 60_000L, data, 3));
        lanes.add(lane("worker-3", "KILLED", start + 2 * 60_000L, start + 4 * 60_000L, data, 2));
        lanes.add(lane("worker-4 devstral-small-2508", "COMPLETED", start, start + 7 * 60_000L, data, 8));

        LaneTimeline timeline = new LaneTimeline();
        timeline.setLanes(lanes);

        Div host = new Div();
        host.addClassName("w-full");
        host.add(timeline);
        addSection("Five worker sessions - drag the cursor or press play. The name column is as "
            + "wide as the longest name needs; hover a name to see it whole.", host);

        List<LaneTimeline.Lane> wordy = new ArrayList<>(lanes);
        wordy.add(lane("worker-5 mixtral-8x22b-instruct-v0.1-quantised", "RUNNING",
            start + 90_000L, 0, data, 5));

        LaneTimeline fixed = new LaneTimeline();
        fixed.setLabelWidth(110);
        fixed.setLanes(wordy);
        Div fixedHost = new Div();
        fixedHost.addClassName("w-full");
        fixedHost.add(fixed);
        addSection("The same lanes with the name column pinned to 110 pixels, plus one very long "
            + "name - the browser fades out the end of it, the whole name is still in the page, "
            + "and hovering shows it", fixedHost);

        LaneTimeline wrapped = new LaneTimeline();
        wrapped.setLabelWidth(110);
        wrapped.setLabelWrap(true);
        wrapped.setLanes(wordy);
        Div wrappedHost = new Div();
        wrappedHost.addClassName("w-full");
        wrappedHost.add(wrapped);
        addSection("The same again with wrapping turned on - the long name runs onto more lines "
            + "and its lane grows to fit", wrappedHost);
    }

    private static LaneTimeline.Lane lane(String label, String outcome, long openedAt, long closedAt,
                                          DemoData data, int eventCount) {
        long end = closedAt > 0 ? closedAt : openedAt + 6 * 60_000L;
        List<Long> events = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            events.add(openedAt + (long) (data.pick() * (end - openedAt)));
        }
        return new LaneTimeline.Lane(label, outcome, openedAt, closedAt, events);
    }
}
