/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Clinical chart data, independent of a charting library or servlet response. */
public final class MeasurementChart {
    private final String title;
    private final String xLabel;
    private final String yLabel;
    private final List<Map<String, Object>> datasets = new ArrayList<>();
    private final List<Map<String, Object>> medications = new ArrayList<>();
    private String secondAxis;
    private boolean dateAxis = true;
    private String pointLabels = "none";
    private boolean fixedDecimals;

    private MeasurementChart(String title, String xLabel, String yLabel) {
        this.title = title; this.xLabel = xLabel; this.yLabel = yLabel;
    }

    static MeasurementChart lines(String title, String xLabel, String yLabel, SeriesCollection series) {
        MeasurementChart chart = new MeasurementChart(title, xLabel, yLabel);
        chart.addSeries(series, "y");
        return chart;
    }

    static MeasurementChart scatter(String title, String xLabel, String yLabel, List<Series> series, boolean lines) {
        SeriesCollection collection = new SeriesCollection();
        series.forEach(collection::addSeries);
        MeasurementChart chart = lines(title, xLabel, yLabel, collection);
        chart.dateAxis = false;
        chart.datasets.forEach(data -> data.put("showLine", lines));
        return chart;
    }

    MeasurementChart withSecondAxis(String label, SeriesCollection series) {
        secondAxis = label; addSeries(series, "y2"); return this;
    }

    MeasurementChart withReferenceRanges(List<ReferenceRange> ranges) {
        if (ranges.isEmpty()) return this;
        List<Map<String, Object>> points = new ArrayList<>();
        for (ReferenceRange range : ranges) {
            points.add(Map.of("x", range.date().getTime(), "y", range.low(), "high", range.high()));
            points.add(Map.of("x", range.date().getTime(), "y", range.high(), "isRangeEnd", true));
        }
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("label", "Normal Reference Range");
        dataset.put("data", points); dataset.put("borderColor", "green");
        dataset.put("pointRadius", 0); dataset.put("showLine", false);
        dataset.put("yAxisID", "y"); dataset.put("referenceRanges", true);
        datasets.add(dataset);
        return this;
    }

    MeasurementChart withMedications(List<Map<String, Object>> values) { medications.addAll(values); return this; }

    MeasurementChart withPointLabels(int dateThreshold, int maxPoints, boolean fixed) {
        int count = datasets.stream().filter(data -> !Boolean.TRUE.equals(data.get("referenceRanges")))
                .mapToInt(data -> ((List<?>) data.get("data")).size()).max().orElse(0);
        pointLabels = count < dateThreshold ? "dateValue" : count < maxPoints ? "value" : "none";
        fixedDecimals = fixed;
        return this;
    }

    private void addSeries(SeriesCollection collection, String axis) {
        for (Series series : collection.series) {
            Map<String, Object> dataset = new LinkedHashMap<>();
            dataset.put("label", series.name == null ? "" : series.name);
            dataset.put("data", series.values.entrySet().stream().map(e -> Map.of("x", e.getKey(), "y", e.getValue())).toList());
            dataset.put("showLine", true); dataset.put("yAxisID", axis);
            dataset.put("borderColor", datasets.isEmpty() ? "red" : "blue");
            dataset.put("pointRadius", 3); datasets.add(dataset);
        }
    }

    public Map<String, Object> toConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("title", title); config.put("xLabel", xLabel); config.put("yLabel", yLabel);
        config.put("datasets", datasets); config.put("medications", medications);
        config.put("dateAxis", dateAxis);
        config.put("timeZone", browserTimeZone(ZoneId.systemDefault()));
        config.put("pointLabels", pointLabels);
        config.put("fixedDecimals", fixedDecimals);
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
