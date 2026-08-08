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
package com.zeroz4j.ui.chart;

/**
 * How a numeric value is rendered on an axis, in a tooltip or on a tile.
 *
 * <p>Charts default to {@link #AUTO}, which picks the decimal count from the tick step.
 * Supply one of the named formats — or your own lambda — when the unit matters.</p>
 */
@FunctionalInterface
public interface ValueFormat {

    String format(double value);

    /** Compact decimal: 0.42, 17, 1.2K, 3.4M. */
    ValueFormat AUTO = Scales::compact;

    /** Whole numbers only. */
    ValueFormat INTEGER = value -> Scales.fixed(value, 0);

    /** Percentage, no decimals: {@code 74 %}. */
    ValueFormat PERCENT = value -> Scales.fixed(value, 0) + " %";

    /** Percentage with one decimal. */
    ValueFormat PERCENT_1 = value -> Scales.fixed(value, 1) + " %";

    /** Binary size from a byte count: 4.2 GB. */
    ValueFormat BYTES = Scales::bytes;

    /** Binary size from a gibibyte count — the unit most system probes already report in. */
    ValueFormat GIGABYTES = value -> Scales.fixed(value, 1) + " GB";

    /** Elapsed milliseconds: 5m 08s. */
    ValueFormat DURATION = value -> Scales.duration((long) value);

    /** Degrees Celsius. Escaped so the source stays pure ASCII whatever the editor encoding. */
    ValueFormat CELSIUS = value -> Scales.fixed(value, 0) + " °C";

    /** A fixed number of decimals. */
    static ValueFormat decimals(int count) {
        return value -> Scales.fixed(value, count);
    }

    /** Compact value followed by a unit suffix, e.g. {@code unit("req/s")}. */
    static ValueFormat unit(String suffix) {
        return value -> Scales.compact(value) + " " + suffix;
    }
}
