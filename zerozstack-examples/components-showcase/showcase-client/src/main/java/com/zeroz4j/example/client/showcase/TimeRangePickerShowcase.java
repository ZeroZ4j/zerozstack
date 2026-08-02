/*
 * Copyright 2026 Franz Schoning
 * Project: https://www.zeroz4j.com
 * Author: Franz Schoning - Principal Enterprise Architect (https://www.franzschoning.com)
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

import com.zeroz4j.signals.Effect;
import com.zeroz4j.ui.chart.Series;
import com.zeroz4j.ui.chart.TimeRangePicker;
import com.zeroz4j.ui.chart.TimeSeriesChart;
import com.zeroz4j.ui.chart.ValueFormat;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import java.util.ArrayList;
import java.util.List;

public class TimeRangePickerShowcase extends ComponentShowcase {

    public TimeRangePickerShowcase() {
        super();
        addTitle("Time Range Picker");
        addDescription("The quick-range selector every dashboard needs. The selection is a "
            + "ValueSignal, so panels bind to it with an Effect instead of being wired up by "
            + "hand — one picker drives every chart on the page, and a chart added later picks "
            + "up the current range with no extra plumbing.");

        TimeRangePicker picker = new TimeRangePicker(1);
        addSection("Default ranges", picker);

        DemoData data = new DemoData(1357L);
        TimeSeriesChart chart = new TimeSeriesChart();
        chart.setYFormat(ValueFormat.PERCENT);
        chart.setChartHeight(160);

        Span readout = new Span();
        readout.addClassName("font-mono text-xs text-base-content/60");

        TimeRangePicker driver = new TimeRangePicker(0);
        // The Effect is the whole point: no click listener, no manual wiring.
        Effect.create(() -> {
            TimeRangePicker.Range range = driver.range.get();
            long from = range.from();
            long to = range.to();
            int samples = 80;
            long step = Math.max(1, (to - from) / samples);
            long[] times = new long[samples];
            for (int i = 0; i < samples; i++) {
                times[i] = from + i * step;
            }
            chart.setTimeRange(from, to);
            chart.setData(times, new Series("gpu", data.wave(samples, 58, 26, 2.4, 5)).filled());
            readout.setText(range.label() + "  window " + (range.durationMillis() / 1000) + "s");
        });

        Div bound = new Div();
        bound.addClassName("flex w-full flex-col gap-3");
        Div bar = new Div();
        bar.addClassName("flex items-center gap-3");
        bar.add(driver, readout);
        bound.add(bar, chart);
        addSection("Bound to a chart through a signal", bound);

        List<TimeRangePicker.Range> custom = new ArrayList<>();
        custom.add(new TimeRangePicker.Range("30s", 30_000L));
        custom.add(new TimeRangePicker.Range("2m", 120_000L));
        custom.add(new TimeRangePicker.Range("10m", 600_000L));
        TimeRangePicker fastRanges = new TimeRangePicker();
        fastRanges.setRanges(custom);
        addSection("Custom ranges", fastRanges);
    }
}
