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

import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.browser.Window;
import org.teavm.jso.core.JSDate;

/**
 * A live telemetry chart with a fixed time window: samples are pushed in, the window slides,
 * old samples fall off the left edge.
 *
 * <p>The behaviour follows Smoothie Charts: <b>redraw is decoupled from data arrival</b>.
 * A chart that only repaints when a sample lands stutters at exactly the polling interval
 * and freezes whenever the feed stalls — which reads as "chart broken" rather than "probe
 * quiet". Calling {@link #start()} runs an animation loop that keeps the window ending at
 * <em>now</em>, so the trace scrolls smoothly at any sample rate and a stalled feed is
 * visible as a growing gap at the right edge.</p>
 *
 * <pre>{@code
 * RollingChart chart = new RollingChart(180, "used", "available");
 * chart.setWindow(120_000);              // two minutes on screen
 * chart.setYFormat(ValueFormat.GIGABYTES);
 * chart.start();
 * // from the vitals signal:
 * chart.push(v.memUsedGb(), v.memAvailableGb());
 * }</pre>
 *
 * <p>Samples live in a ring buffer of {@code capacity} slots, so memory is bounded no matter
 * how long the console stays open. Size it to a little more than
 * {@code window / sampleInterval}.</p>
 */
public final class RollingChart extends TimeSeriesChart {

    /**
     * How far behind {@code now} the right edge sits. Rendering a moment in the past means a
     * sample has usually landed before its slot scrolls into view, so the leading edge draws
     * a continuous line instead of flickering between "arrived" and "not yet".
     */
    private static final long DEFAULT_DELAY_MILLIS = 1000;

    private final int capacity;
    private final long[] ringTimes;
    private final double[][] ringValues;
    private final List<Series> channels = new ArrayList<>();

    private int size;
    private int head;

    private long windowMillis = 120_000;
    private long delayMillis = DEFAULT_DELAY_MILLIS;
    private int framesPerSecond = 12;
    private boolean streaming;
    private int frameHandle = -1;
    private double lastFrameAt;

    /**
     * @param capacity how many samples to retain; the ring never grows beyond this
     * @param seriesNames one channel per name, in push order
     */
    public RollingChart(int capacity, String... seriesNames) {
        this.capacity = Math.max(2, capacity);
        this.ringTimes = new long[this.capacity];
        int channelCount = seriesNames == null ? 0 : seriesNames.length;
        this.ringValues = new double[channelCount][this.capacity];
        for (int c = 0; c < channelCount; c++) {
            // A single channel reads better filled. Filling several stacks translucent areas on
            // top of each other and the overlap is unreadable, so multi-channel starts as lines —
            // call filled() on the one channel that should carry the area.
            Series channel = new Series(seriesNames[c]);
            channels.add(channelCount == 1 ? channel.filled() : channel);
        }
        setChartHeight(180);
        setEmptyText("Waiting for the first sample");
    }

    // ------------------------------------------------------------------ public API

    /** The series objects, in push order, so callers can restyle them. */
    public List<Series> channels() {
        return channels;
    }

    public Series channel(int index) {
        return channels.get(index);
    }

    /** Appends a sample stamped now. One value per channel, in construction order. */
    public void push(double... values) {
        push((long) JSDate.now(), values);
    }

    /** Appends a sample at an explicit time. Missing channels are recorded as gaps. */
    public void push(long epochMillis, double... values) {
        int slot = (head + size) % capacity;
        if (size == capacity) {
            // Full: overwrite the oldest and advance the window start.
            slot = head;
            head = (head + 1) % capacity;
        } else {
            size++;
        }
        ringTimes[slot] = epochMillis;
        for (int c = 0; c < ringValues.length; c++) {
            ringValues[c][slot] = values != null && c < values.length ? values[c] : Double.NaN;
        }
        if (!streaming) {
            render();
        }
    }

    /** Drops every sample and repaints to the empty state. */
    public void clear() {
        size = 0;
        head = 0;
        render();
    }

    public int sampleCount() {
        return size;
    }

    /** Visible time span in milliseconds. Default two minutes. */
    public RollingChart setWindow(long millis) {
        this.windowMillis = Math.max(1000, millis);
        render();
        return this;
    }

    public long window() {
        return windowMillis;
    }

    /** How far behind real time the right edge sits. Default one second. */
    public RollingChart setDelay(long millis) {
        this.delayMillis = Math.max(0, millis);
        return this;
    }

    /** Redraw rate while streaming. Higher is smoother and costs more; 12 reads as fluid. */
    public RollingChart setFramesPerSecond(int fps) {
        this.framesPerSecond = Math.max(1, Math.min(60, fps));
        return this;
    }

    /** Starts the scroll loop. Idempotent. */
    public RollingChart start() {
        if (!streaming) {
            streaming = true;
            scheduleFrame();
        }
        return this;
    }

    /**
     * Stops the scroll loop. The chart keeps whatever it last drew and still repaints on
     * {@link #push}, so a paused console is static but not stale.
     */
    public RollingChart stop() {
        streaming = false;
        if (frameHandle >= 0) {
            Window.cancelAnimationFrame(frameHandle);
            frameHandle = -1;
        }
        return this;
    }

    public boolean isStreaming() {
        return streaming;
    }

    @Override
    protected void onDetach() {
        super.onDetach();
        // Nothing is watching a detached chart; leaving the loop running would burn frames
        // for the rest of the session.
        stop();
    }

    // -------------------------------------------------------------------- internals

    private void scheduleFrame() {
        frameHandle = Window.requestAnimationFrame(frameTime -> {
            if (!streaming) {
                return;
            }
            if (frameTime - lastFrameAt >= 1000.0 / framesPerSecond) {
                lastFrameAt = frameTime;
                render();
            }
            scheduleFrame();
        });
    }

    /** Linearises the ring, sets the sliding window and repaints — once, not twice. */
    private void render() {
        long[] times = new long[size];
        for (int i = 0; i < size; i++) {
            times[i] = ringTimes[(head + i) % capacity];
        }
        for (int c = 0; c < channels.size(); c++) {
            double[] values = new double[size];
            for (int i = 0; i < size; i++) {
                values[i] = ringValues[c][(head + i) % capacity];
            }
            channels.get(c).values(values);
        }

        long end = streaming
            ? (long) JSDate.now() - delayMillis
            : (size > 0 ? times[size - 1] : (long) JSDate.now());

        beginBatch();
        setTimeRange(end - windowMillis, end);
        setData(times, channels);
        endBatch();
    }
}
