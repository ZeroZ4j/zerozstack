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

public class RatingShowcase extends ComponentShowcase {

    public RatingShowcase() {
        addTitle("Rating");
        addDescription("Rating component displays a set of stars to rate something.");

        // Basic Rating
        Rating basicRating = new Rating();
        basicRating.setValue(3);
        addSection("Basic Rating", basicRating);

        // Sizes
        Rating ratingXs = new Rating().setThemeSize(ThemeSize.XS);
        ratingXs.setValue(1);
        Rating ratingSm = new Rating().setThemeSize(ThemeSize.SM);
        ratingSm.setValue(2);
        Rating ratingMd = new Rating().setThemeSize(ThemeSize.MD);
        ratingMd.setValue(3);
        Rating ratingLg = new Rating().setThemeSize(ThemeSize.LG);
        ratingLg.setValue(4);

        addSection("Sizes",
            ratingXs, ratingSm, ratingMd, ratingLg
        );

        // Data Binding Demo
        ValueSignal<Integer> signal = new ValueSignal<>(3);
        Rating component = new Rating();
        component.bindValue(signal);
        Span output = new Span();
        output.bindText(new Computed<>(() -> "Current value: " + signal.get()));
        addSection("Data Binding Demo", component, output);
    }
}
