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

import com.zeroz4j.ui.component.*;
import com.zeroz4j.ui.layout.*;
import com.zeroz4j.ui.theme.*;
import com.zeroz4j.signals.*;

public class TimelineShowcase extends ComponentShowcase {

    public TimelineShowcase() {
        addTitle("Timeline");
        addDescription("Timeline component is used to show a list of events in chronological order.");

        Timeline timeline = new Timeline();

        class TimelineItem extends Component {
            public TimelineItem(String date, String title, String desc, boolean first, boolean last) {
                super("li");
                
                if (!first) {
                    Component hr1 = new Component("hr") {};
                    getElement().appendChild(hr1.getElement());
                }

                Div start = new Div();
                start.addClassName("timeline-start");
                start.getElement().setTextContent(date);

                Div middle = new Div();
                middle.addClassName("timeline-middle");
                middle.getElement().setInnerHTML("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 20 20\" fill=\"currentColor\" class=\"h-5 w-5\"><path fill-rule=\"evenodd\" d=\"M10 18a8 8 0 100-16 8 8 0 000 16zm3.857-9.809a.75.75 0 00-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 10-1.06 1.061l2.5 2.5a.75.75 0 001.137-.089l4-5.5z\" clip-rule=\"evenodd\" /></svg>");

                Div end = new Div();
                end.addClassName("timeline-end");
                end.addClassName("timeline-box");
                
                Div titleDiv = new Div();
                titleDiv.addClassName("font-black");
                titleDiv.getElement().setTextContent(title);
                
                Div descDiv = new Div();
                descDiv.getElement().setTextContent(desc);
                
                end.add(titleDiv, descDiv);

                getElement().appendChild(start.getElement());
                getElement().appendChild(middle.getElement());
                getElement().appendChild(end.getElement());

                if (!last) {
                    Component hr2 = new Component("hr") {};
                    getElement().appendChild(hr2.getElement());
                }
            }
        }

        timeline.add(
            new TimelineItem("1984", "First Macintosh", "Apple released the first Macintosh computer.", true, false),
            new TimelineItem("2001", "iPod launched", "Apple announced the iPod portable digital media player.", false, false),
            new TimelineItem("2007", "iPhone debut", "Steve Jobs introduced the iPhone.", false, true)
        );

        addSection("Horizontal Timeline", timeline);
    }
}
