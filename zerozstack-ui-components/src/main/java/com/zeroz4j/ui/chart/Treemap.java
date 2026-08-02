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
package com.zeroz4j.ui.chart;

import com.zeroz4j.ui.layout.Div;
import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.dom.xml.Element;

/**
 * Proportional area, for the question "what is taking up all the space".
 *
 * <p>A treemap answers that at a glance where a bar chart cannot: with fifty model
 * directories, a bar chart is fifty rows of scrolling, and a pie chart is unreadable past
 * six. A treemap shows all fifty at once and makes the big ones impossible to miss.</p>
 *
 * <pre>{@code
 * Treemap disk = new Treemap();
 * disk.setFormat(ValueFormat.BYTES);
 * disk.setNodes(List.of(
 *     new Treemap.Node("huggingface", 412e9, List.of(
 *         new Treemap.Node("llama-3.3-70b", 140e9),
 *         new Treemap.Node("nemotron-49b",   98e9))),
 *     new Treemap.Node("docker images", 88e9)));
 * }</pre>
 *
 * <p>Laid out with the squarified algorithm, which keeps tiles close to square. Long thin
 * slivers are not merely ugly — area is much harder to judge as an aspect ratio grows, so
 * squarer tiles make the comparison the chart exists for actually possible.</p>
 */
public final class Treemap extends ChartBase {

    /** A tile. Leave {@code value} to be summed from children by passing 0. */
    public record Node(String label, double value, String color, List<Node> children) {

        public Node(String label, double value) {
            this(label, value, null, null);
        }

        public Node(String label, double value, List<Node> children) {
            this(label, value, null, children);
        }

        /** Own value, or the sum of children when it was not given. */
        public double weight() {
            if (value > 0) {
                return value;
            }
            double total = 0;
            if (children != null) {
                for (Node child : children) {
                    total += child.weight();
                }
            }
            return total;
        }
    }

    /** One node placed in the layout, kept for hit testing. */
    private record Tile(Node node, double x, double y, double w, double h, int depth, String color,
                        boolean hasDrawnChildren) {
    }

    private record Sized(Node node, double area, String color) {
    }

    private List<Node> nodes = new ArrayList<>();
    private final List<Tile> tiles = new ArrayList<>();
    private ValueFormat format = ValueFormat.AUTO;
    private int maxDepth = 2;
    private double padding = 2;
    private boolean labels = true;

    public Treemap() {
        setChartHeight(260);
        setMargins(2, 2, 2, 2);
        setLegendVisible(false);
    }

    // ------------------------------------------------------------------ public API

    public Treemap setNodes(List<Node> newNodes) {
        this.nodes = newNodes == null ? new ArrayList<>() : newNodes;
        refresh();
        return this;
    }

    public Treemap setFormat(ValueFormat newFormat) {
        this.format = newFormat == null ? ValueFormat.AUTO : newFormat;
        refresh();
        return this;
    }

    /** How many levels to draw. 1 flattens to top-level tiles only. Default 2. */
    public Treemap setMaxDepth(int depth) {
        this.maxDepth = Math.max(1, depth);
        refresh();
        return this;
    }

    /** Gap between tiles in pixels. */
    public Treemap setPadding(double pixels) {
        this.padding = Math.max(0, pixels);
        refresh();
        return this;
    }

    public Treemap setLabelsVisible(boolean visible) {
        this.labels = visible;
        refresh();
        return this;
    }

    public double total() {
        double sum = 0;
        for (Node node : nodes) {
            sum += node.weight();
        }
        return sum;
    }

    // --------------------------------------------------------------------- drawing

    @Override
    protected boolean hasData() {
        return !nodes.isEmpty() && total() > 0;
    }

    @Override
    protected void draw() {
        tiles.clear();
        List<Sized> sized = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node.weight() > 0) {
                sized.add(new Sized(node, node.weight(),
                    node.color() != null ? node.color() : Palette.series(i)));
            }
        }
        layout(sized, plotLeft(), plotTop(), plotWidth(), plotHeight(), 1);

        for (Tile tile : tiles) {
            Element block = rect(tile.x() + padding / 2, tile.y() + padding / 2,
                tile.w() - padding, tile.h() - padding, tile.color());
            block.setAttribute("rx", "3");
            // Deeper tiles sit on their parent's colour; opacity separates the levels
            // without needing a second palette.
            block.setAttribute("fill-opacity", tile.depth() == 1 ? "0.55" : "0.9");
            block.setAttribute("stroke", Palette.BASE_100);
            block.setAttribute("stroke-width", "1");
            add(block);
        }

        if (labels) {
            for (Tile tile : tiles) {
                drawLabel(tile);
            }
        }
    }

    private void drawLabel(Tile tile) {
        double innerWidth = tile.w() - padding - 8;
        double innerHeight = tile.h() - padding;
        if (innerWidth < 26 || innerHeight < 14) {
            return;
        }
        String caption = tile.node().label() == null ? "" : tile.node().label();
        int fits = (int) (innerWidth / 6.0);
        if (caption.length() > fits) {
            caption = fits <= 1 ? "" : caption.substring(0, Math.max(1, fits - 1)) + "…";
        }
        if (caption.isEmpty()) {
            return;
        }
        double textX = tile.x() + padding / 2 + 5;
        double textY = tile.y() + padding / 2 + 10;
        Element label = text(textX, textY, caption, "start", 10, 1);
        label.setAttribute("fill", Palette.BASE_CONTENT);
        label.setAttribute("font-weight", tile.depth() == 1 ? "600" : "400");
        add(label);

        // A parent's own value line would sit in the strip its children occupy. The caption alone
        // is enough there; the figure is one hover away.
        if (innerHeight > 30 && !tile.hasDrawnChildren()) {
            add(monoText(textX, textY + 13, format.format(tile.node().weight()), "start", 9, 0.6));
        }
    }

    // -------------------------------------------------------------------- squarify

    /**
     * Squarified treemap layout: fill the rectangle row by row along its short side, growing
     * each row while the worst aspect ratio in it keeps improving.
     */
    private void layout(List<Sized> items, double x, double y, double w, double h, int depth) {
        if (items.isEmpty() || w <= 0 || h <= 0) {
            return;
        }
        List<Sized> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> Double.compare(b.area(), a.area()));

        double total = 0;
        for (Sized item : sorted) {
            total += item.area();
        }
        if (total <= 0) {
            return;
        }
        double scale = (w * h) / total;
        double[] areas = new double[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            areas[i] = sorted.get(i).area() * scale;
        }

        int index = 0;
        while (index < sorted.size() && w > 0.5 && h > 0.5) {
            double shortSide = Math.min(w, h);
            int end = index;
            double rowSum = 0;
            double bestWorst = Double.MAX_VALUE;
            while (end < sorted.size()) {
                double candidateSum = rowSum + areas[end];
                double worst = worstRatio(areas, index, end, candidateSum, shortSide);
                if (worst > bestWorst) {
                    break;
                }
                bestWorst = worst;
                rowSum = candidateSum;
                end++;
            }
            if (end == index) {
                // Degenerate area; take one item anyway so the loop always advances.
                rowSum = areas[index];
                end = index + 1;
            }

            double thickness = rowSum / shortSide;
            double offset = 0;
            boolean vertical = w >= h;
            for (int k = index; k < end; k++) {
                double extent = areas[k] / thickness;
                double tileX = vertical ? x : x + offset;
                double tileY = vertical ? y + offset : y;
                double tileW = vertical ? thickness : extent;
                double tileH = vertical ? extent : thickness;
                place(sorted.get(k), tileX, tileY, tileW, tileH, depth);
                offset += extent;
            }
            if (vertical) {
                x += thickness;
                w -= thickness;
            } else {
                y += thickness;
                h -= thickness;
            }
            index = end;
        }
    }

    private void place(Sized item, double x, double y, double w, double h, int depth) {
        List<Node> children = item.node().children();
        // Children are inset so the parent's border and label stay legible. The headroom has to
        // clear the caption's whole line box, or the first child lands on top of it.
        double inset = 4;
        double headroom = labels && h > 34 ? 20 : inset;
        double childX = x + inset;
        double childY = y + headroom;
        double childW = w - inset * 2;
        double childH = h - headroom - inset;

        boolean drawChildren = depth < maxDepth && children != null && !children.isEmpty()
            && childW >= 8 && childH >= 8;
        tiles.add(new Tile(item.node(), x, y, w, h, depth, item.color(), drawChildren));
        if (!drawChildren) {
            return;
        }
        List<Sized> sizedChildren = new ArrayList<>();
        for (Node child : children) {
            if (child.weight() > 0) {
                sizedChildren.add(new Sized(child, child.weight(),
                    child.color() != null ? child.color() : item.color()));
            }
        }
        layout(sizedChildren, childX, childY, childW, childH, depth + 1);
    }

    /**
     * The worst aspect ratio in a row of the given total area laid across {@code shortSide}.
     * Only the smallest and largest members can be the worst, so the extremes suffice.
     */
    private static double worstRatio(double[] areas, int from, int to, double rowSum, double shortSide) {
        double min = Double.MAX_VALUE;
        double max = 0;
        for (int i = from; i <= to; i++) {
            min = Math.min(min, areas[i]);
            max = Math.max(max, areas[i]);
        }
        if (rowSum <= 0 || min <= 0) {
            return Double.MAX_VALUE;
        }
        double sideSquared = shortSide * shortSide;
        double sumSquared = rowSum * rowSum;
        return Math.max(sideSquared * max / sumSquared, sumSquared / (sideSquared * min));
    }

    // ----------------------------------------------------------------------- hover

    @Override
    protected void onPointerMove(double x, double y) {
        // Iterate backwards so a child tile wins over the parent it sits inside.
        for (int i = tiles.size() - 1; i >= 0; i--) {
            Tile tile = tiles.get(i);
            if (x >= tile.x() && x <= tile.x() + tile.w() && y >= tile.y() && y <= tile.y() + tile.h()) {
                Div content = new Div();
                content.addClassName("flex flex-col gap-0.5");
                Div name = new Div(tile.node().label());
                name.addClassName("font-semibold");
                Div value = new Div(format.format(tile.node().weight())
                    + "  ·  " + Scales.fixed(tile.node().weight() / total() * 100, 1) + "%");
                value.addClassName("font-mono text-base-content/70");
                content.add(name, value);
                showTooltip(x, y, content);
                return;
            }
        }
        hideTooltip();
    }
}
