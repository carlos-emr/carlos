/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
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
    private static final String PLOT_BACKGROUND = "plotBackground";
    private static final String DOMAIN_MARGINS = "domainMargins";
    private static final String RANGE_MARGINS = "rangeMargins";
    private static final String RANGE_INCLUDES_ZERO = "rangeIncludesZero";
    private static final String ITEM_LABELS = "itemLabels";
    private static final String FONT_SIZE = "fontSize";
    private static final String FORMAT = "format";
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
    private boolean medicationPanel;
    private int excludedObservations;

    private MeasurementChart(String title, String xLabel, String yLabel) {
        this.title = title; this.xLabel = xLabel; this.yLabel = yLabel;
        // ChartFactory's "JFree" theme: a light gray plot with white dashed gridlines.
        style.put("theme", "jfree");
        style.put(PLOT_BACKGROUND, "#c0c0c0");
        style.put("gridlines", "#ffffff");
        style.put(DOMAIN_MARGINS, List.of(0.02, 0.02));
        style.put(RANGE_MARGINS, List.of(0.05, 0.05));
        style.put(RANGE_INCLUDES_ZERO, false);
        style.put("domainIncludesZero", false);
        style.put("rangeAxisPaint", null);
        style.put(ITEM_LABELS, null);
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
        chart.style.put(PLOT_BACKGROUND, "#ffffff");
        chart.style.put(DOMAIN_MARGINS, List.of(0.9, 0.9));
        chart.style.put(RANGE_MARGINS, List.of(0.05, 0.2));
        chart.style.put(RANGE_INCLUDES_ZERO, true);
        chart.datasets.add(rangeDataset(rangesLabel, FIRST_SERIES_PAINT, ranges, true));
        chart.excludedObservations += series.excludedObservations;
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
     * (lines with square/circle markers, zero on the value axis): numeric axes with 5% margins, the first series
     * pure red and the second pure blue.
     */
    static MeasurementChart scatter(String title, String xLabel, String yLabel, List<Series> series, boolean lines) {
        MeasurementChart chart = new MeasurementChart(title, xLabel, yLabel);
        chart.dateAxis = false;
        chart.style.put(DOMAIN_MARGINS, List.of(0.05, 0.05));
        chart.style.put(RANGE_INCLUDES_ZERO, lines);
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

    /**
     * Prescription intervals; the chart becomes a combined plot with a medication panel below, as the
     * former chart did even when no prescription matched (an empty "Meds" panel).
     */
    MeasurementChart withMedications(List<Map<String, Object>> values) {
        medications.addAll(values); medicationPanel = true; return this;
    }

    MeasurementChart withDomainMargins(double lower, double upper) { style.put(DOMAIN_MARGINS, List.of(lower, upper)); return this; }

    MeasurementChart withRangeUpperMargin(double upper) {
        style.put(RANGE_MARGINS, List.of(((List<?>) style.get(RANGE_MARGINS)).get(0), upper)); return this;
    }

    MeasurementChart withPlotBackground(String colour) { style.put(PLOT_BACKGROUND, colour); return this; }

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
        labels.put(FORMAT, format); labels.put("decimals", decimals); labels.put(FONT_SIZE, fontSize);
        style.put(ITEM_LABELS, labels);
        return this;
    }

    MeasurementChart withItemLabels(Map<String, Object> labels) {
        style.put(ITEM_LABELS, labels == null || labels.isEmpty() ? null : new LinkedHashMap<>(labels));
        return this;
    }

    /** The actual-lab chart's label rule by tens of points: date and value, value, value in 8pt, none. */
    static Map<String, Object> labItemLabels(int count) {
        return labItemLabels(count, 10);
    }

    /**
     * The actual-lab label rule in bands of {@code perBand} points: the lab-only chart counted in tens
     * and the lab-plus-medication chart in sixes.
     */
    static Map<String, Object> labItemLabels(int count, int perBand) {
        Map<String, Object> labels = new LinkedHashMap<>();
        switch (count / perBand) {
            case 0 -> { labels.put(FORMAT, "{1} \n {2}"); labels.put(FONT_SIZE, 12); }
            case 1 -> { labels.put(FORMAT, "{2}"); labels.put(FONT_SIZE, 12); }
            case 2 -> { labels.put(FORMAT, "{2}"); labels.put(FONT_SIZE, 8); }
            default -> { return Map.of(); }
        }
        labels.put("decimals", "0.###");
        return labels;
    }

    private void addSeries(SeriesCollection collection, String axis, String paint, boolean lines, boolean shapes) {
        excludedObservations += collection.excludedObservations;
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
        dataset.put("data", series.values.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(value -> Map.of("x", entry.getKey(), "y", value))).toList());
        datasets.add(dataset);
    }

    private static Map<String, Object> rangeDataset(String label, String paint, List<ReferenceRange> ranges, boolean widensAxis) {
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("label", label);
        dataset.put("kind", "range");
        dataset.put("yAxisID", "y");
        dataset.put("paint", paint);
        dataset.put("widensAxis", widensAxis);
        dataset.put("data", ranges.stream().map(range -> Map.<String, Object>of("x", range.date().toEpochMilli(), "low", range.low(), "high", range.high())).toList());
        return dataset;
    }

    MeasurementChart withExcludedObservations(int count) {
        excludedObservations += count;
        return this;
    }

    public Map<String, Object> toConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("title", title); config.put("xLabel", xLabel); config.put("yLabel", yLabel);
        config.put("datasets", formattedDatasets());
        java.util.Locale locale = java.util.Locale.getDefault(java.util.Locale.Category.FORMAT);
        java.text.DateFormatSymbols symbols = java.text.DateFormatSymbols.getInstance(locale);
        config.put("locale", locale.toLanguageTag());
        config.put("months", java.util.Arrays.asList(symbols.getShortMonths()));
        config.put("amPm", java.util.Arrays.asList(symbols.getAmPmStrings())); config.put("medications", medications);
        if (medicationPanel) config.put("medicationPanel", true);
        config.put("dateAxis", dateAxis);
        config.put("timeZone", browserTimeZone(ZoneId.systemDefault()));
        config.put("style", style);
        if (excludedObservations > 0) {
            config.put("exclusionNotice", excludedObservations
                    + " qualified or nonnumeric results are not plotted; consult the source history.");
        }
        if (secondAxis != null) config.put("secondAxis", secondAxis);
        return config;
    }

    /** Label formatting retains DecimalFormat's binary-double HALF_EVEN contract. */
    private List<Map<String, Object>> formattedDatasets() {
        Object configured = style.get(ITEM_LABELS);
        if (!(configured instanceof Map<?, ?> labels)) return datasets;
        String pattern = String.valueOf(labels.get("decimals"));
        java.text.DecimalFormat numbers = new java.text.DecimalFormat(pattern);
        java.text.SimpleDateFormat dates = new java.text.SimpleDateFormat("yyyy.MM.dd");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> dataset : datasets) {
            Map<String, Object> copy = new LinkedHashMap<>(dataset);
            List<Map<String, Object>> points = new ArrayList<>();
            for (Object raw : (List<?>) dataset.get("data")) {
                Map<?, ?> point = (Map<?, ?>) raw;
                Map<String, Object> decorated = new LinkedHashMap<>();
                point.forEach((key, value) -> decorated.put(String.valueOf(key), value));
                if (point.get("y") instanceof Number value) {
                    decorated.put("labelValue", numbers.format(value.doubleValue()));
                    decorated.put("labelDate", dates.format(new java.util.Date(((Number) point.get("x")).longValue())));
                }
                points.add(decorated);
            }
            copy.put("data", points);
            result.add(copy);
        }
        return result;
    }

    static final class Series {
        private String name;
        private final TreeMap<Long, List<Double>> values = new TreeMap<>();
        Series(String name) { this.name = name; }
        void setKey(String name) { this.name = name; }
        void addOrUpdate(Day day, double value) { values.put(day.start.toEpochMilli(), new ArrayList<>(List.of(value))); }
        // XYSeries accepted duplicate x coordinates; unlike a daily TimeSeries update, adding a
        // scatter observation must not erase another observation that falls on the same x value.
        void add(long x, long y) { values.computeIfAbsent(x, ignored -> new ArrayList<>()).add((double) y); }
        int getItemCount() { return values.values().stream().mapToInt(List::size).sum(); }
    }

    static final class SeriesCollection {
        private final List<Series> series = new ArrayList<>();
        private int excludedObservations;
        void excludeObservation() { excludedObservations++; }
        void addSeries(Series value) { series.add(value); }
    }

    static final class Day {
        private final Instant start;
        Day(Instant value) {
            start = value.atZone(ZoneId.systemDefault()).toLocalDate()
                    .atStartOfDay(ZoneId.systemDefault()).toInstant();
        }
        Instant getStart() { return start; }
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

    record ReferenceRange(Instant date, double low, double high) { }
}
