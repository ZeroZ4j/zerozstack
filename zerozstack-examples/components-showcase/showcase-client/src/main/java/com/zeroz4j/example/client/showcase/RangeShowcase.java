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

public class RangeShowcase extends ComponentShowcase {

    public RangeShowcase() {
        addTitle("Range");
        addDescription("Range slider is used to select a value from a range.");

        // Basic Range
        Range basicRange = new Range();
        basicRange.setValue(40.0);
        addSection("Basic Range", basicRange);

        // Colors
        Range rangePrimary = new Range().setThemeColor(ThemeColor.PRIMARY);
        rangePrimary.setValue(20.0);
        Range rangeSecondary = new Range().setThemeColor(ThemeColor.SECONDARY);
        rangeSecondary.setValue(30.0);
        Range rangeAccent = new Range().setThemeColor(ThemeColor.ACCENT);
        rangeAccent.setValue(40.0);
        Range rangeNeutral = new Range().setThemeColor(ThemeColor.NEUTRAL);
        rangeNeutral.setValue(50.0);
        Range rangeInfo = new Range().setThemeColor(ThemeColor.INFO);
        rangeInfo.setValue(60.0);
        Range rangeSuccess = new Range().setThemeColor(ThemeColor.SUCCESS);
        rangeSuccess.setValue(70.0);
        Range rangeWarning = new Range().setThemeColor(ThemeColor.WARNING);
        rangeWarning.setValue(80.0);
        Range rangeError = new Range().setThemeColor(ThemeColor.ERROR);
        rangeError.setValue(90.0);
        
        addSection("Colors", 
            rangePrimary, rangeSecondary, rangeAccent, rangeNeutral, 
            rangeInfo, rangeSuccess, rangeWarning, rangeError
        );

        // Sizes
        Range rangeXs = new Range().setThemeSize(ThemeSize.XS);
        rangeXs.setValue(10.0);
        Range rangeSm = new Range().setThemeSize(ThemeSize.SM);
        rangeSm.setValue(30.0);
        Range rangeMd = new Range().setThemeSize(ThemeSize.MD);
        rangeMd.setValue(50.0);
        Range rangeLg = new Range().setThemeSize(ThemeSize.LG);
        rangeLg.setValue(70.0);

        addSection("Sizes",
            rangeXs, rangeSm, rangeMd, rangeLg
        );

        // Data Binding Demo
        ValueSignal<Double> signal = new ValueSignal<>(50.0);
        Range component = new Range();
        component.bindValue(signal);
        Span output = new Span();
        output.bindText(new Computed<>(() -> "Current value: " + signal.get()));
        addSection("Data Binding Demo", component, output);
    }
}
