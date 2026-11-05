/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Clinical chart data and the look of the JFreeChart chart it replaces, independent of a charting
 * library or servlet response. Each factory and modifier mirrors one JFreeChart call the former
 * action made (ChartFactory theme, axis margins, zero rules, renderers, item labels), and the
 * renderer ({@code library/chartjs/carlos-chart-draw.js}) reproduces JFreeChart's auto ranges, tick
 * units, fonts and colours from this description; {@code scripts/fixtures/chart/jfreechart} records
 * what JFreeChart drew for the same descriptions.
 */
public final class MeasurementChart {
    /** The start of JFreeChart's default series paint sequence; the renderer holds the full sequence. */
    static final String[] PAINT_SEQUENCE = {"#ff5555", "#5555ff", "#55ff55", "#ffff55", "#ff55ff", "#55ffff"};
    static final String FIRST_SERIES_PAINT = PAINT_SEQUENCE[0];
    static final String GREEN = "#00ff00";

    private final String title;
    private final String xLabel;
    private final String yLabel;
    private final List<Map<String, Object>> datasets = new ArrayList<>();
    private final List<Map<String, Object>> medications = new ArrayList<>();
    private final Map<String, Object> style = new LinkedHashMap<>();
    private String secondAxis;
    private boolean dateAxis = true;

    private MeasurementChart(String title, String xLabel, String yLabel) {
        this.title = title; this.xLabel = xLabel; this.yLabel = yLabel;
        // ChartFactory's "JFree" theme: a light gray plot with white dashed gridlines.
        style.put("theme", "jfree");
        style.put("plotBackground", "#c0c0c0");
        style.put("gridlines", "#ffffff");
        style.put("domainMargins", List.of(0.02, 0.02));
        style.put("rangeMargins", List.of(0.05, 0.05));
        style.put("rangeIncludesZero", false);
        style.put("domainIncludesZero", false);
        style.put("rangeAxisPaint", null);
        style.put("itemLabels", null);
    }

    /** {@code ChartFactory.createTimeSeriesChart}: 2% date margins, a value axis that need not include zero, lines only. */
    static MeasurementChart lines(String title, String xLabel, String yLabel, SeriesCollection series) {
        MeasurementChart chart = new MeasurementChart(title, xLabel, yLabel);
        chart.addSeries(series, "y", null, true, false);
        return chart;
    }

    /**
     * {@code ChartFactory.createHighLowChart} as the reference range chart used it: the range
     * dataset first, in the first series paint, zero on the value axis with a 20% top margin, 90%
     * date margins and a white plot. The series itself went through the high-low renderer, which
     * drew it as a plain line without shapes or labels.
     */
    static MeasurementChart highLow(String title, String xLabel, String yLabel, SeriesCollection series,
                                    String rangesLabel, List<ReferenceRange> ranges) {
        MeasurementChart chart = new MeasurementChart(title, xLabel, yLabel);
        chart.style.put("plotBackground", "#ffffff");
        chart.style.put("domainMargins", List.of(0.9, 0.9));
        chart.style.put("rangeMargins", List.of(0.05, 0.2));
        chart.style.put("rangeIncludesZero", true);
        chart.datasets.add(rangeDataset(rangesLabel, FIRST_SERIES_PAINT, ranges, true));
        // One renderer drew both datasets, so series i of the measurement reused its paint i, and
        // its legend item carried the high-low renderer's square.
        int index = 0;
        for (Series value : series.series) {
            chart.addSeries(value, "y", PAINT_SEQUENCE[Math.min(index++, PAINT_SEQUENCE.length - 1)], true, false);
            chart.datasets.getLast().put("highLow", true);
        }
        return chart;
    }

    /**
     * The scatter servlet's {@code createScatterPlot} (squares only) and {@code createXYLineChart}
     * (lines with squares, zero on the value axis): numeric axes with 5% margins, the first series
     * pure red and the second pure blue.
     */
    static MeasurementChart scatter(String title, String xLabel, String yLabel, List<Series> series, boolean lines) {
        MeasurementChart chart = new MeasurementChart(title, xLabel, yLabel);
        chart.dateAxis = false;
        chart.style.put("domainMargins", List.of(0.05, 0.05));
        chart.style.put("rangeIncludesZero", lines);
        SeriesCollection collection = new SeriesCollection();
        series.forEach(collection::addSeries);
        String[] paints = {"#ff0000", "#0000ff"};
        int index = 0;
        for (Series value : collection.series) {
            chart.addSeries(value, "y", paints[Math.min(index++, paints.length - 1)], lines, true);
        }
        return chart;
    }

    /** A second measurement type on its own axis, drawn by a plain renderer in black without shapes. */
    MeasurementChart withSecondAxis(String label, SeriesCollection series) {
        secondAxis = label; addSeries(series, "y2", "#000000", true, false); return this;
    }

    /**
     * Reference ranges through a second, default high-low renderer mapped to the value axis: the
     * plot's paint sequence colours them after the series, and their bounds widen the axis.
     */
    MeasurementChart withReferenceRanges(String label, List<ReferenceRange> ranges) {
        if (!ranges.isEmpty()) datasets.add(rangeDataset(label, null, ranges, true));
        return this;
    }

    /**
     * Reference ranges in green, mapped to a second index of the same axis object: the former
     * chart painted that axis green as a side effect, and did not widen it to the range bounds.
     */
    MeasurementChart withGreenReferenceRanges(String label, List<ReferenceRange> ranges) {
        if (ranges.isEmpty()) return this;
        datasets.add(rangeDataset(label, GREEN, ranges, false));
        style.put("rangeAxisPaint", GREEN);
        return this;
    }

    /** Prescription intervals; the chart becomes a combined plot with a medication panel below. */
    MeasurementChart withMedications(List<Map<String, Object>> values) { medications.addAll(values); return this; }

    MeasurementChart withDomainMargins(double lower, double upper) { style.put("domainMargins", List.of(lower, upper)); return this; }

    MeasurementChart withRangeUpperMargin(double upper) {
        style.put("rangeMargins", List.of(((List<?>) style.get("rangeMargins")).get(0), upper)); return this;
    }

    MeasurementChart withPlotBackground(String colour) { style.put("plotBackground", colour); return this; }

    MeasurementChart withGridlines(String colour) { style.put("gridlines", colour); return this; }

    /** Filled squares on every point of the line series, as {@code setDefaultShapesVisible(true)} did. */
    MeasurementChart withShapes() {
        datasets.stream().filter(data -> "line".equals(data.get("kind"))).forEach(data -> data.put("shapes", true));
        return this;
    }

    /**
     * Item labels above the points of the first series: {@code format} is JFreeChart's
     * {@code "{1} \n {2}"} (date yyyy.MM.dd and value) or {@code "{2}"}, {@code decimals} its
     * DecimalFormat pattern, in a font of {@code fontSize} pixels.
     */
    MeasurementChart withItemLabels(String format, String decimals, int fontSize) {
        Map<String, Object> labels = new LinkedHashMap<>();
        labels.put("format", format); labels.put("decimals", decimals); labels.put("fontSize", fontSize);
        style.put("itemLabels", labels);
        return this;
    }

    MeasurementChart withItemLabels(Map<String, Object> labels) {
        style.put("itemLabels", labels == null ? null : new LinkedHashMap<>(labels));
        return this;
    }

    /** The actual-lab charts' label rule by tens of points: date and value, value, value in 8pt, none. */
    static Map<String, Object> labItemLabels(int count) {
        Map<String, Object> labels = new LinkedHashMap<>();
        switch (count / 10) {
            case 0 -> { labels.put("format", "{1} \n {2}"); labels.put("fontSize", 12); }
            case 1 -> { labels.put("format", "{2}"); labels.put("fontSize", 12); }
            case 2 -> { labels.put("format", "{2}"); labels.put("fontSize", 8); }
            default -> { return null; }
        }
        labels.put("decimals", "0.###");
        return labels;
    }

    private void addSeries(SeriesCollection collection, String axis, String paint, boolean lines, boolean shapes) {
        for (Series series : collection.series) addSeries(series, axis, paint, lines, shapes);
    }

    private void addSeries(Series series, String axis, String paint, boolean lines, boolean shapes) {
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("label", series.name == null ? "" : series.name);
        dataset.put("kind", "line");
        dataset.put("yAxisID", axis);
        dataset.put("paint", paint);
        dataset.put("lines", lines);
        dataset.put("shapes", shapes);
        dataset.put("data", series.values.entrySet().stream().map(e -> Map.of("x", e.getKey(), "y", e.getValue())).toList());
        datasets.add(dataset);
    }

    private static Map<String, Object> rangeDataset(String label, String paint, List<ReferenceRange> ranges, boolean widensAxis) {
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("label", label);
        dataset.put("kind", "range");
        dataset.put("yAxisID", "y");
        dataset.put("paint", paint);
        dataset.put("widensAxis", widensAxis);
        dataset.put("data", ranges.stream().map(range -> Map.<String, Object>of("x", range.date().getTime(), "low", range.low(), "high", range.high())).toList());
        return dataset;
    }

    public Map<String, Object> toConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("title", title); config.put("xLabel", xLabel); config.put("yLabel", yLabel);
        config.put("datasets", datasets); config.put("medications", medications);
        config.put("dateAxis", dateAxis);
        config.put("timeZone", browserTimeZone(ZoneId.systemDefault()));
        config.put("style", style);
        if (secondAxis != null) config.put("secondAxis", secondAxis);
        return config;
    }

    static final class Series {
        private String name;
        private final TreeMap<Long, Double> values = new TreeMap<>();
        Series(String name) { this.name = name; }
        void setKey(String name) { this.name = name; }
        void addOrUpdate(Day day, double value) { values.put(day.getStart().getTime(), value); }
        void add(long x, long y) { values.put(x, (double) y); }
        int getItemCount() { return values.size(); }
    }

    static final class SeriesCollection {
        private final List<Series> series = new ArrayList<>();
        void addSeries(Series value) { series.add(value); }
    }

    static final class Day {
        private final Date start;
        Day(Date value) {
            start = Date.from(value.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                    .atStartOfDay(ZoneId.systemDefault()).toInstant());
        }
        Date getStart() { return new Date(start.getTime()); }
    }

    /**
     * The zone id Chromium's Intl accepts: an IANA region as is, a fixed offset as {@code +HH:MM}
     * (Java's {@code GMT+03:00} and {@code Z} are rejected there), and UTC as {@code UTC}.
     */
    static String browserTimeZone(ZoneId zone) {
        ZoneId normalized = zone.normalized();
        if (normalized instanceof java.time.ZoneOffset offset) {
            return offset.getTotalSeconds() == 0 ? "UTC" : offset.getId();
        }
        return normalized.getId();
    }

    record ReferenceRange(Date date, double low, double high) { }
}
