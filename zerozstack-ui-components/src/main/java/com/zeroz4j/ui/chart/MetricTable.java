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

import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.HasComponents;
import com.zeroz4j.ui.component.HasStyle;
import com.zeroz4j.ui.component.HasText;
import com.zeroz4j.ui.component.Sparkline;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * A table whose cells are measurements: threshold-coloured numbers, inline trend sparklines,
 * in-cell bars and state pills, sortable by clicking a header.
 *
 * <p>The reason to use this instead of a plain {@code Table} is that a row of numbers does
 * not tell you which one is a problem, and a chart of forty series is unreadable. A metric
 * table gives every row its own miniature chart, so the anomalous one is visible without a
 * drill-down.</p>
 *
 * <pre>{@code
 * MetricTable<ContainerStat> table = new MetricTable<>();
 * table.addTextColumn("container", ContainerStat::name)
 *      .addValueColumn("memory", ContainerStat::memoryGb, ValueFormat.GIGABYTES,
 *                      Threshold.utilisation(60, 90))
 *      .addSparklineColumn("last 5m", ContainerStat::trend)
 *      .addStateColumn("state", ContainerStat::state);
 * table.setItems(stats);
 * }</pre>
 *
 * @param <T> the row type
 */
public final class MetricTable<T> extends Div {

    public enum Align { LEFT, RIGHT, CENTER }

    /** A concrete element wrapper — {@code Component} is abstract and cells need arbitrary tags. */
    private static final class El extends Component implements HasComponents, HasStyle, HasText {

        El(String tag, String classes) {
            super(tag);
            if (classes != null) {
                setClassName(classes);
            }
        }

        @Override
        public Component getComponent() {
            return this;
        }
    }

    private static final class Column<T> {
        String header;
        Align align = Align.LEFT;
        Function<T, Component> renderer;
        Comparator<T> comparator;
        String widthCss;
    }

    private final List<Column<T>> columns = new ArrayList<>();
    private List<T> items = new ArrayList<>();
    private final El table = new El("table", "table table-sm w-full");
    private final El head = new El("thead", null);
    private final El body = new El("tbody", null);

    private boolean sortable = true;
    private boolean zebra = true;
    private int sortColumn = -1;
    private boolean sortDescending;
    private String emptyText = "No rows";

    public MetricTable() {
        addClassName("w-full overflow-x-auto");
        table.add(head, body);
        add(table);
    }

    // -------------------------------------------------------------------- columns

    /** Plain text, sorted alphabetically. */
    public MetricTable<T> addTextColumn(String header, Function<T, String> value) {
        Column<T> column = new Column<>();
        column.header = header;
        column.renderer = item -> {
            String text = value.apply(item);
            El cell = new El("span", "truncate");
            cell.setText(text == null ? "" : text);
            return cell;
        };
        column.comparator = Comparator.comparing(item -> {
            String text = value.apply(item);
            return text == null ? "" : text;
        });
        return add(column);
    }

    /** A number, right-aligned, monospaced, coloured by threshold. */
    public MetricTable<T> addValueColumn(String header, ToDoubleFunction<T> value,
                                         ValueFormat format, List<Threshold> thresholds) {
        Column<T> column = new Column<>();
        column.header = header;
        column.align = Align.RIGHT;
        column.renderer = item -> {
            double number = value.applyAsDouble(item);
            ValueFormat effective = format == null ? ValueFormat.AUTO : format;
            El cell = new El("span", "font-mono font-semibold");
            cell.setText(Double.isNaN(number) ? "-" : effective.format(number));
            if (thresholds != null && !thresholds.isEmpty()) {
                cell.setStyle("color", Threshold.colorFor(thresholds, number, Palette.BASE_CONTENT));
            }
            return cell;
        };
        column.comparator = Comparator.comparingDouble(item -> {
            double number = value.applyAsDouble(item);
            // NaN sorts last in both directions rather than clumping at one end.
            return Double.isNaN(number) ? -Double.MAX_VALUE : number;
        });
        return add(column);
    }

    public MetricTable<T> addValueColumn(String header, ToDoubleFunction<T> value, ValueFormat format) {
        return addValueColumn(header, value, format, null);
    }

    /** An inline trend chart. Sorted by the most recent sample. */
    public MetricTable<T> addSparklineColumn(String header, Function<T, double[]> series) {
        Column<T> column = new Column<>();
        column.header = header;
        column.widthCss = "7rem";
        column.renderer = item -> {
            Div host = new Div();
            host.addClassName("text-primary");
            Sparkline spark = new Sparkline(96, 22);
            double[] values = series.apply(item);
            if (values != null && values.length > 1) {
                spark.setValues(values);
            }
            host.add(spark);
            return host;
        };
        column.comparator = Comparator.comparingDouble(item -> {
            double[] values = series.apply(item);
            return values == null || values.length == 0 ? 0 : values[values.length - 1];
        });
        return add(column);
    }

    /** A bar in the cell, against a shared scale — a compact {@link BarGauge} row. */
    public MetricTable<T> addBarColumn(String header, ToDoubleFunction<T> value,
                                       double min, double max, ValueFormat format,
                                       List<Threshold> thresholds) {
        Column<T> column = new Column<>();
        column.header = header;
        column.widthCss = "9rem";
        column.renderer = item -> {
            double number = value.applyAsDouble(item);
            Div host = new Div();
            host.addClassName("flex items-center gap-2");
            Div track = new Div();
            track.addClassName("h-1.5 flex-1 overflow-hidden rounded-full bg-base-300");
            Div bar = new Div();
            bar.addClassName("h-full rounded-full");
            bar.setStyle("width", Scales.fixed(Scales.normalise(number, min, max) * 100, 1) + "%");
            bar.setStyle("background-color", Threshold.colorFor(thresholds, number, Palette.PRIMARY));
            track.add(bar);
            Span caption = new Span(Double.isNaN(number)
                ? "-" : (format == null ? ValueFormat.AUTO : format).format(number));
            caption.addClassName("shrink-0 font-mono text-xs");
            host.add(track, caption);
            return host;
        };
        column.comparator = Comparator.comparingDouble(value::applyAsDouble);
        return add(column);
    }

    /** A coloured state pill. */
    public MetricTable<T> addStateColumn(String header, Function<T, String> state) {
        return addStateColumn(header, state, StateColor.DEFAULT);
    }

    public MetricTable<T> addStateColumn(String header, Function<T, String> state, StateColor colors) {
        Column<T> column = new Column<>();
        column.header = header;
        column.renderer = item -> {
            String value = state.apply(item);
            Div pill = new Div();
            pill.addClassName("inline-flex items-center gap-1.5 text-xs");
            Div dot = new Div();
            dot.addClassName("h-2 w-2 shrink-0 rounded-full");
            dot.setStyle("background-color", colors.colorFor(value));
            Span caption = new Span(value == null ? "-" : value);
            pill.add(dot, caption);
            return pill;
        };
        column.comparator = Comparator.comparing(item -> {
            String value = state.apply(item);
            return value == null ? "" : value;
        });
        return add(column);
    }

    /** Anything else. Not sortable unless a comparator is supplied separately. */
    public MetricTable<T> addComponentColumn(String header, Function<T, Component> renderer) {
        Column<T> column = new Column<>();
        column.header = header;
        column.renderer = renderer;
        return add(column);
    }

    private MetricTable<T> add(Column<T> column) {
        columns.add(column);
        render();
        return this;
    }

    // ------------------------------------------------------------------ public API

    public MetricTable<T> setItems(List<T> newItems) {
        this.items = newItems == null ? new ArrayList<>() : new ArrayList<>(newItems);
        render();
        return this;
    }

    public MetricTable<T> setSortable(boolean value) {
        this.sortable = value;
        render();
        return this;
    }

    public MetricTable<T> setZebra(boolean value) {
        this.zebra = value;
        render();
        return this;
    }

    public MetricTable<T> setEmptyText(String text) {
        this.emptyText = text;
        render();
        return this;
    }

    /** Sorts by a column index, descending first — the useful default for a metric. */
    public MetricTable<T> sortBy(int columnIndex, boolean descending) {
        this.sortColumn = columnIndex;
        this.sortDescending = descending;
        render();
        return this;
    }

    // --------------------------------------------------------------------- render

    private void render() {
        table.setClassName("table table-sm w-full" + (zebra ? " table-zebra" : ""));
        head.removeAll();
        body.removeAll();

        El headerRow = new El("tr", null);
        for (int c = 0; c < columns.size(); c++) {
            Column<T> column = columns.get(c);
            El cell = new El("th", alignClass(column.align)
                + " text-xs font-medium text-base-content/60"
                + (sortable && column.comparator != null ? " cursor-pointer select-none" : ""));
            if (column.widthCss != null) {
                cell.setStyle("width", column.widthCss);
            }
            String caption = column.header == null ? "" : column.header;
            if (sortColumn == c) {
                caption = caption + (sortDescending ? "  ▼" : "  ▲");
            }
            cell.setText(caption);
            if (sortable && column.comparator != null) {
                final int index = c;
                cell.addDomEventListener("click", event -> toggleSort(index));
            }
            headerRow.add(cell);
        }
        head.add(headerRow);

        List<T> ordered = sorted();
        if (ordered.isEmpty()) {
            El emptyRow = new El("tr", null);
            El emptyCell = new El("td", "py-6 text-center text-xs text-base-content/40");
            emptyCell.getElement().setAttribute("colspan", String.valueOf(Math.max(1, columns.size())));
            emptyCell.setText(emptyText);
            emptyRow.add(emptyCell);
            body.add(emptyRow);
            return;
        }

        for (T item : ordered) {
            El row = new El("tr", null);
            for (Column<T> column : columns) {
                El cell = new El("td", alignClass(column.align) + " whitespace-nowrap");
                Component content = column.renderer.apply(item);
                if (content != null) {
                    cell.add(content);
                }
                row.add(cell);
            }
            body.add(row);
        }
    }

    private List<T> sorted() {
        if (sortColumn < 0 || sortColumn >= columns.size() || columns.get(sortColumn).comparator == null) {
            return items;
        }
        List<T> ordered = new ArrayList<>(items);
        Comparator<T> comparator = columns.get(sortColumn).comparator;
        ordered.sort(sortDescending ? comparator.reversed() : comparator);
        return ordered;
    }

    private void toggleSort(int columnIndex) {
        if (sortColumn == columnIndex) {
            sortDescending = !sortDescending;
        } else {
            sortColumn = columnIndex;
            // First click on a metric almost always means "show me the worst", so start high.
            sortDescending = true;
        }
        render();
    }

    private static String alignClass(Align align) {
        return switch (align) {
            case RIGHT -> "text-right";
            case CENTER -> "text-center";
            default -> "text-left";
        };
    }
}
