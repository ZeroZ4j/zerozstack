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

import org.teavm.jso.core.JSDate;

/**
 * Sample data for the chart showcases.
 *
 * <p>Deterministic on purpose: every generator runs off a seeded linear congruential
 * generator, never {@code Math.random}. A gallery that redraws differently on each load
 * cannot be screenshot-tested, and "the chart looks wrong" becomes impossible to reproduce.
 * Only the timestamps move, because they are anchored to the clock.</p>
 */
final class DemoData {

    private long seed;

    DemoData(long seed) {
        this.seed = seed;
    }

    /** Numerical Recipes LCG — short, portable, and identical under TeaVM and the JVM. */
    private double next() {
        seed = (seed * 1664525L + 1013904223L) & 0xFFFFFFFFL;
        return (double) seed / 4294967296.0;
    }

    /** Uniform in [-1, 1). */
    private double signed() {
        return next() * 2 - 1;
    }

    // ------------------------------------------------------------------ generators

    /** {@code count} timestamps ending now, {@code stepMillis} apart. */
    static long[] timestamps(int count, long stepMillis) {
        long now = (long) JSDate.now();
        long[] times = new long[count];
        for (int i = 0; i < count; i++) {
            times[i] = now - (long) (count - 1 - i) * stepMillis;
        }
        return times;
    }

    /** A smooth wave with a little noise — reads like a real utilisation metric. */
    double[] wave(int count, double base, double amplitude, double periods, double noise) {
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            double phase = (double) i / count * periods * 2 * Math.PI;
            values[i] = base + Math.sin(phase) * amplitude + signed() * noise;
        }
        return values;
    }

    /** A random walk clamped to a range — reads like memory in use. */
    double[] walk(int count, double start, double step, double min, double max) {
        double[] values = new double[count];
        double current = start;
        for (int i = 0; i < count; i++) {
            current += signed() * step;
            current = Math.max(min, Math.min(max, current));
            values[i] = current;
        }
        return values;
    }

    /** A walk with a sustained shift partway through, so charts have something to show. */
    double[] withStep(int count, double before, double after, double atFraction, double noise) {
        double[] values = new double[count];
        int breakpoint = (int) (count * atFraction);
        for (int i = 0; i < count; i++) {
            values[i] = (i < breakpoint ? before : after) + signed() * noise;
        }
        return values;
    }

    /** Punches a gap into a series, so gap handling is visible in the gallery. */
    static double[] withGap(double[] values, int from, int length) {
        double[] copy = new double[values.length];
        System.arraycopy(values, 0, copy, 0, values.length);
        for (int i = from; i < from + length && i < copy.length; i++) {
            copy[i] = Double.NaN;
        }
        return copy;
    }

    double[] positive(int count, double scale) {
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            values[i] = next() * scale;
        }
        return values;
    }

    /** Two overlapping normal-ish populations — the case a mean would hide. */
    double[] bimodal(int count, double firstCentre, double secondCentre, double spread) {
        double[] samples = new double[count];
        for (int i = 0; i < count; i++) {
            double centre = next() < 0.62 ? firstCentre : secondCentre;
            // Sum of three uniforms approximates a normal well enough for a demo.
            double bell = (next() + next() + next()) / 3 * 2 - 1;
            samples[i] = centre + bell * spread;
        }
        return samples;
    }

    /** Picks from a state vocabulary, mostly the first entry so the timeline reads as healthy. */
    String pickState(String[] states, double disturbance) {
        return next() < disturbance ? states[1 + (int) (next() * (states.length - 1))] : states[0];
    }

    double pick() {
        return next();
    }
}
