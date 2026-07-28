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

/** Dashboard stat tile: label, big value, optional delta line and trend sparkline. */
public final class KpiTile extends Div {

    private final Div value = new Div();
    private final Div delta = new Div();
    private final Sparkline trend = new Sparkline(120, 28);

    public KpiTile(String label) {
        addClassName("rounded-xl border border-base-300 bg-base-200/50 p-4 flex flex-col gap-1 "
            + "min-w-[10rem]");
        Div labelDiv = new Div(label);
        labelDiv.addClassName("text-xs uppercase tracking-wide text-base-content/50");
        value.addClassName("text-2xl font-bold font-mono");
        delta.addClassName("text-xs text-base-content/60");
        Div trendWrap = new Div();
        trendWrap.addClassName("text-primary mt-1");
        trendWrap.add(trend);
        add(labelDiv, value, delta, trendWrap);
    }

    public KpiTile value(String text) {
        value.setText(text);
        return this;
    }

    public KpiTile delta(String text, boolean positive) {
        delta.setText(text);
        delta.setClassName("text-xs " + (positive ? "text-success" : "text-error"));
        return this;
    }

    public KpiTile trend(double[] values) {
        trend.setValues(values);
        return this;
    }
}

