/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Records what JFreeChart 1.5.6 drew for the measurement charts before library/chartjs/carlos-chart-draw.js
 * replaced it: each chart is built exactly as the former MeasurementGraphAction22Action and
 * ScatterPlotChartServlet built it, with synthetic data, and its ranges, ticks, labels, paints, fonts and
 * legend are written as JSON fixtures (with a PNG beside each) for scripts/chart-draw-playwright-checks.js.
 * See README.md in this directory for how to run it; JFreeChart is no longer a project dependency.
 */
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import org.jfree.chart.*;
import org.jfree.chart.axis.*;
import org.jfree.chart.labels.*;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.AbstractRenderer;
import org.jfree.chart.renderer.xy.*;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.gantt.*;
import org.jfree.data.time.*;
import org.jfree.data.xy.*;

public class JfcReference {
    static final TimeZone TZ = TimeZone.getTimeZone("America/Toronto");
    static File out;

    public static void main(String[] args) throws Exception {
        out = new File(args[0]); out.mkdirs();
        TimeZone.setDefault(TZ);
        String title = "Data Graph for FAKE-JOHN, FAKE-SMITH M 41 years";
        String jfree = style("jfree", "#c0c0c0", "#ffffff", 0.02, 0.02, 0.05, 0.05, false, false, null, null);
        String highLow = style("jfree", "#ffffff", "#ffffff", 0.9, 0.9, 0.05, 0.2, true, false, null, null);
        String labRef = style("jfree", "#ffffff", "#ffffff", 0.9, 0.9, 0.05, 0.34, false, false, "#00ff00", itemLabels("{1} \n {2}", "0.00", 12));
        String actualLab = style("jfree", "#ffffff", "#c0c0c0", 0.9, 0.9, 0.05, 0.34, false, false, null, itemLabels("{1} \n {2}", "0.###", 12));
        String scatterStyle = style("jfree", "#c0c0c0", "#ffffff", 0.05, 0.05, 0.05, 0.05, false, false, null, null);
        String bpStyle = style("jfree", "#c0c0c0", "#ffffff", 0.05, 0.05, 0.05, 0.05, true, false, null, null);

        TimeSeriesCollection two = series("ALT", pts("2022-03-30", 23, "2023-09-30", 37));
        record("default-2pt", defaultChart(title, "ALT", two, null, null), 1000, 600,
                input(title, "Days", "ALT", true, jfree, line("ALT", "y", null, true, false, two), "[]", null));
        TimeSeriesCollection twelve = series("WT", pts("2021-01-15", 61.5, "2021-04-02", 63, "2021-07-20", 64.2, "2021-11-05", 66, "2022-02-10", 65.5,
                "2022-05-30", 68, "2022-09-14", 70.25, "2023-01-03", 71, "2023-04-21", 72.8, "2023-08-08", 74, "2023-11-28", 75.5, "2024-01-20", 77));
        record("default-12pt", defaultChart(title, "WT", twelve, null, null), 1000, 600,
                input(title, "Days", "WT", true, jfree, line("WT", "y", null, true, false, twelve), "[]", null));
        TimeSeriesCollection second = series("HT", pts("2021-02-01", 170, "2022-06-01", 171, "2023-10-01", 172));
        record("default-two-types", defaultChart(title, "WT", twelve, "HT", second), 1000, 600,
                input(title, "Days", "WT", true, jfree, line("WT", "y", null, true, false, twelve) + ", " + line("HT", "y2", "#000000", true, false, second), "[]", "HT"));
        OHLCDataItem[] none = new OHLCDataItem[0];
        OHLCDataItem[] altRanges = {new OHLCDataItem(day("2022-03-30"), 7, 40, 7, 40, 1045), new OHLCDataItem(day("2023-09-30"), 7, 40, 7, 40, 1045)};
        record("inclRef-none", referenceRangeChart(two, none), 1000, 600,
                input("HighLowChartDemo2", "Time", "Value", true, highLow, range("DREFERENCE RANGE", "#ff5555", none, true) + ", " + line("ALT", "y", "#ff5555", true, false, two, true), "[]", null));
        record("inclRef-2pt", referenceRangeChart(two, altRanges), 1000, 600,
                input("HighLowChartDemo2", "Time", "Value", true, highLow, range("DREFERENCE RANGE", "#ff5555", altRanges, true) + ", " + line("ALT", "y", "#ff5555", true, false, two, true), "[]", null));
        List<TaskSeries> demoDrugs = List.of(
                task("beclomethasone dipropionate 50 mcg nasal pump spray", "2023-11-14", "2023-11-15"),
                task("Asaphen 81 mg chewable tablet", "2023-11-14", "2024-02-14"));
        record("chartmeds-2", chartMeds(title, demoDrugs), 1000, 600, input(title, "Days", "MEDS", true, jfree, "", meds(demoDrugs), null));
        List<TaskSeries> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String name = i == 0 ? "SYNTHETIC MEDICATION WITH A VERY LONG PRODUCT NAME 500MG TABLET" : "SYNTHETIC MEDICATION " + (i + 1);
            many.add(task(name, plus("2024-01-15", i * 10), plus("2024-01-15", i * 10 + 20)));
        }
        many.add(task("SYNTHETIC SINGLE DOSE", "2024-02-14", "2024-02-14"));
        record("chartmeds-13", chartMeds(title, many), 1000, 600, input(title, "Days", "MEDS", true, jfree, "", meds(many), null));
        XYSeries sc = new XYSeries("ALT-in U/L"); sc.add(0, 23); sc.add(549, 37);
        record("scatter-2pt", scatter("ALT-in U/L", sc), 550, 360,
                input("ALT-in U/L", "Day (note: only the last data on the same observation date is plotted)", "Test Results", false, scatterStyle, xy("ALT-in U/L", "#ff0000", false, true, sc), "[]", null));
        XYSeries sc2 = new XYSeries("WT-in kg"); for (int i = 0; i < 12; i++) sc2.add(i * 90, 60 + (i * 7) % 11 + 0.5 * i);
        record("scatter-12pt", scatter("WT-in kg", sc2), 550, 360,
                input("WT-in kg", "Day (note: only the last data on the same observation date is plotted)", "Test Results", false, scatterStyle, xy("WT-in kg", "#ff0000", false, true, sc2), "[]", null));
        XYSeries systolic = new XYSeries("Systolic"), diastolic = new XYSeries("Diastolic");
        double[] sys = {120, 130, 125, 118}, dia = {80, 85, 82, 78};
        for (int i = 0; i < sys.length; i++) { systolic.add(i + 1, sys[i]); diastolic.add(i + 1, dia[i]); }
        record("bp-lines", bloodPressure("BP-in mmHg", sys, dia), 550, 360,
                input("BP-in mmHg", "Tests (note: only the last data on the same observation date is plotted)", "Hgmm", false, bpStyle,
                        xy("Systolic", "#ff0000", true, true, systolic) + ", " + xy("Diastolic", "#0000ff", true, true, diastolic), "[]", null));
        TimeSeriesCollection lab = series("Hemoglobin", pts("2023-01-10", 132, "2023-03-15", 128.5, "2023-06-20", 135, "2023-09-01", 140, "2023-12-12", 138));
        OHLCDataItem[] ranges = {new OHLCDataItem(day("2023-01-10"), 120, 160, 120, 160, 1045), new OHLCDataItem(day("2023-12-12"), 120, 160, 120, 160, 1045)};
        record("labRef", labChart(title, "g/L", lab, ranges, null, false), 1000, 600,
                input(title, "Days", "g/L", true, labRef, line("Hemoglobin", "y", null, true, true, lab) + ", " + range("Reference Range", "#00ff00", ranges, false), "[]", null));
        record("actualLab", labChart(title, "g/L", lab, ranges, null, true), 1000, 600,
                input(title, "Days", "g/L", true, actualLab, line("Hemoglobin", "y", null, true, true, lab) + ", " + range("Normal Reference Range", null, ranges, true), "[]", null));
        List<TaskSeries> labDrugs = List.of(task("Asaphen 81 mg chewable tablet", "2023-02-01", "2023-11-01"), task("SYNTHETIC MEDICATION 2", "2023-05-01", "2023-05-01"));
        record("actualLab-plusmeds", labChart(title, "g/L", lab, ranges, labDrugs, true), 1000, 600,
                input(title, "Days", "g/L", true, actualLab, line("Hemoglobin", "y", null, true, true, lab) + ", " + range("Normal Reference Range", null, ranges, true), meds(labDrugs), null));
        System.out.println("done");
    }

    // --- builders mirroring the base branch -----------------------------------------------------

    static JFreeChart defaultChart(String chartTitle, String yName, TimeSeriesCollection dataset, String yName2, TimeSeriesCollection dataset2) {
        JFreeChart chart = ChartFactory.createTimeSeriesChart(chartTitle, "Days", yName, dataset, true, true, true);
        if (dataset2 != null) {
            XYPlot plot = chart.getXYPlot();
            NumberAxis axis2 = new NumberAxis(yName2);
            axis2.setAutoRangeIncludesZero(false);
            plot.setRangeAxis(1, axis2);
            plot.setDataset(1, dataset2);
            plot.mapDatasetToRangeAxis(1, 1);
            XYItemRenderer renderer = plot.getRenderer();
            renderer.setDefaultToolTipGenerator(StandardXYToolTipGenerator.getTimeSeriesInstance());
            StandardXYItemRenderer renderer2 = new StandardXYItemRenderer();
            renderer2.setSeriesPaint(0, Color.black);
            plot.setRenderer(1, renderer2);
        }
        return chart;
    }

    static JFreeChart referenceRangeChart(TimeSeriesCollection dataset, OHLCDataItem[] ohlc) {
        JFreeChart chart = ChartFactory.createHighLowChart("HighLowChartDemo2", "Time", "Value", new DefaultOHLCDataset("DREFERENCE RANGE", ohlc), true);
        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setDataset(1, dataset);
        plot.getDomainAxis().setAutoRange(true);
        plot.getDomainAxis().setUpperMargin(0.9);
        plot.getDomainAxis().setLowerMargin(0.9);
        plot.getRangeAxis().setUpperMargin(plot.getRangeAxis().getUpperMargin() * 4);
        ValueAxis va = plot.getRangeAxis();
        va.setAutoRange(true);
        XYItemRenderer renderer = plot.getRenderer();
        XYItemLabelGenerator generator = new StandardXYItemLabelGenerator("{1} \n {2}", new SimpleDateFormat("yyyy.MM.dd"), new DecimalFormat("0.00"));
        renderer.setSeriesItemLabelGenerator(0, generator);
        renderer.setDefaultItemLabelsVisible(true);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainCrosshairPaint(Color.GRAY);
        if (renderer instanceof XYLineAndShapeRenderer rend) { rend.setDefaultShapesVisible(true); rend.setDefaultShapesFilled(true); }
        plot.setRenderer(renderer);
        chart.setBackgroundPaint(Color.white);
        return chart;
    }

    static JFreeChart labChart(String chartTitle, String units, TimeSeriesCollection dataset, OHLCDataItem[] ranges, List<TaskSeries> drugs, boolean actualLab) {
        JFreeChart chart = ChartFactory.createTimeSeriesChart(chartTitle, "Days", units, dataset, true, true, true);
        XYPlot plot = chart.getXYPlot();
        plot.getDomainAxis().setAutoRange(true);
        plot.getDomainAxis().setUpperMargin(plot.getDomainAxis().getUpperMargin() * 6);
        plot.getDomainAxis().setLowerMargin(plot.getDomainAxis().getLowerMargin() * 6);
        plot.getRangeAxis().setUpperMargin(plot.getRangeAxis().getUpperMargin() * 1.7);
        plot.getDomainAxis().setUpperMargin(0.9);
        plot.getDomainAxis().setLowerMargin(0.9);
        plot.getRangeAxis().setUpperMargin(plot.getRangeAxis().getUpperMargin() * 4);
        ValueAxis va = plot.getRangeAxis();
        va.setAutoRange(true);
        XYItemRenderer renderer = plot.getRenderer();
        XYItemLabelGenerator generator;
        if (actualLab) {
            // actualLabChartRef: labels by tens of points, 0.### values
            generator = new StandardXYItemLabelGenerator("{2}", new SimpleDateFormat("yyyy.MM.dd"), new DecimalFormat("0.###"));
            int count = dataset.getSeries(0).getItemCount();
            switch (count / 10) {
                case 0 -> { generator = new StandardXYItemLabelGenerator("{1} \n {2}", new SimpleDateFormat("yyyy.MM.dd"), new DecimalFormat("0.###")); renderer.setDefaultItemLabelsVisible(true); }
                case 1 -> renderer.setDefaultItemLabelsVisible(true);
                case 2 -> { renderer.setDefaultItemLabelFont(new Font("helvetica", Font.PLAIN, 8)); renderer.setDefaultItemLabelsVisible(true); }
                default -> renderer.setDefaultItemLabelsVisible(false);
            }
        } else {
            // labChartRef: date and value labels always, 0.00 values
            generator = new StandardXYItemLabelGenerator("{1} \n {2}", new SimpleDateFormat("yyyy.MM.dd"), new DecimalFormat("0.00"));
            renderer.setDefaultItemLabelsVisible(true);
        }
        renderer.setSeriesItemLabelGenerator(0, generator);
        if (renderer instanceof XYLineAndShapeRenderer rend) { rend.setDefaultShapesVisible(true); rend.setDefaultShapesFilled(true); }
        plot.setRenderer(renderer);
        if (actualLab) {
            if (ranges.length > 0) {
                XYDataset referenceRangeDataset = new DefaultOHLCDataset("Normal Reference Range", ranges);
                plot.setDataset(1, referenceRangeDataset);
                plot.mapDatasetToRangeAxis(1, 0);
                plot.setRenderer(1, new HighLowRenderer());
            }
            plot.setBackgroundPaint(Color.WHITE);
            plot.setDomainCrosshairPaint(Color.GRAY);
            plot.setRangeGridlinePaint(Color.lightGray);
            plot.setDomainGridlinePaint(Color.lightGray);
        } else {
            plot.setBackgroundPaint(Color.WHITE);
            plot.setDomainCrosshairPaint(Color.GRAY);
            if (ranges.length > 0) {
                XYDataset referenceRangeDataset = new DefaultOHLCDataset("Reference Range", ranges);
                plot.setRenderer(1, setAxisAndDataSet(1, plot, plot.getRangeAxis(), referenceRangeDataset, Color.GREEN, new HighLowRenderer()));
            }
        }
        if (drugs == null) return chart;
        TaskSeriesCollection datasetDrug = new TaskSeriesCollection();
        drugs.forEach(datasetDrug::add);
        XYTaskDataset drugDataset = new XYTaskDataset(datasetDrug);
        drugDataset.setTransposed(true);
        drugDataset.setSeriesWidth(0.6);
        SymbolAxis yAxis = new SymbolAxis("Meds", drugs.stream().map(s -> symbol((String) s.getKey())).toArray(String[]::new));
        yAxis.setGridBandsVisible(false);
        XYBarRenderer xyrenderer = new XYBarRenderer();
        xyrenderer.setUseYInterval(true);
        xyrenderer.setBarPainter(new StandardXYBarPainter());
        XYPlot xyplot = new XYPlot(drugDataset, plot.getDomainAxis(), yAxis, xyrenderer);
        xyplot.getDomainAxis().setUpperMargin(0.9);
        xyplot.getDomainAxis().setLowerMargin(0.9);
        CombinedDomainXYPlot cplot = new CombinedDomainXYPlot(new DateAxis("Date/Time"));
        cplot.add(plot);
        cplot.add(xyplot);
        chart = new JFreeChart(chartTitle, cplot);
        chart.setBackgroundPaint(Color.white);
        return chart;
    }

    static XYItemRenderer setAxisAndDataSet(int i, XYPlot plot, ValueAxis axis, XYDataset dataset, Paint p, XYItemRenderer renderer) {
        plot.setRangeAxis(i, axis);
        plot.setDataset(i, dataset);
        plot.mapDatasetToRangeAxis(i, i);
        renderer.setSeriesPaint(0, p);
        axis.setLabelPaint(p);
        axis.setTickLabelPaint(p);
        return renderer;
    }

    static JFreeChart chartMeds(String chartTitle, List<TaskSeries> drugs) {
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        JFreeChart chart = ChartFactory.createTimeSeriesChart(chartTitle, "Days", "MEDS", dataset, true, true, true);
        XYPlot plot = chart.getXYPlot();
        TaskSeriesCollection datasetDrug = new TaskSeriesCollection();
        drugs.forEach(datasetDrug::add);
        XYTaskDataset drugDataset = new XYTaskDataset(datasetDrug);
        drugDataset.setTransposed(true);
        drugDataset.setSeriesWidth(0.6);
        SymbolAxis yAxis = new SymbolAxis("Meds", drugs.stream().map(s -> symbol((String) s.getKey())).toArray(String[]::new));
        yAxis.setGridBandsVisible(false);
        XYBarRenderer xyrenderer = new XYBarRenderer();
        xyrenderer.setUseYInterval(true);
        xyrenderer.setBarPainter(new StandardXYBarPainter());
        XYPlot xyplot = new XYPlot(drugDataset, plot.getDomainAxis(), yAxis, xyrenderer);
        xyplot.getDomainAxis().setUpperMargin(0.9);
        xyplot.getDomainAxis().setLowerMargin(0.9);
        CombinedDomainXYPlot cplot = new CombinedDomainXYPlot(new DateAxis("Date/Time"));
        cplot.add(xyplot);
        chart = new JFreeChart(chartTitle, cplot);
        chart.setBackgroundPaint(Color.white);
        return chart;
    }

    static JFreeChart scatter(String chartTitle, XYSeries series) {
        JFreeChart chart = ChartFactory.createScatterPlot(chartTitle, "Day (note: only the last data on the same observation date is plotted)",
                "Test Results", new XYSeriesCollection(series), PlotOrientation.VERTICAL, true, false, false);
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(false, true);
        renderer.setSeriesPaint(0, Color.RED);
        plot.setRenderer(renderer);
        return chart;
    }

    static JFreeChart bloodPressure(String chartTitle, double[] sys, double[] dia) {
        XYSeries systolic = new XYSeries("Systolic"), diastolic = new XYSeries("Diastolic");
        for (int i = 0; i < sys.length; i++) { systolic.add(i + 1, sys[i]); diastolic.add(i + 1, dia[i]); }
        XYSeriesCollection dataset = new XYSeriesCollection(); dataset.addSeries(systolic); dataset.addSeries(diastolic);
        JFreeChart chart = ChartFactory.createXYLineChart(chartTitle, "Tests (note: only the last data on the same observation date is plotted)",
                "Hgmm", dataset, PlotOrientation.VERTICAL, true, false, false);
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, true);
        renderer.setSeriesPaint(0, Color.RED);
        renderer.setSeriesPaint(1, Color.BLUE);
        plot.setRenderer(renderer);
        return chart;
    }

    // --- recording ------------------------------------------------------------------------------

    static void record(String name, JFreeChart chart, int width, int height, String input) throws Exception {
        ChartRenderingInfo info = new ChartRenderingInfo();
        BufferedImage image = chart.createBufferedImage(width, height, info);
        ImageIO.write(image, "png", new File(out, name + ".png"));
        Graphics2D g2 = image.createGraphics();
        chart.draw(g2, new Rectangle2D.Double(0, 0, width, height), null, info);
        StringBuilder json = new StringBuilder("{\n");
        json.append("  \"name\": ").append(q(name)).append(",\n  \"width\": ").append(width).append(", \"height\": ").append(height).append(",\n");
        json.append("  \"input\": ").append(input).append(",\n");
        json.append("  \"metrics\": ").append(metrics(g2)).append(",\n");
        json.append("  \"title\": ").append(q(chart.getTitle().getText())).append(", \"titleFont\": ").append(q(font(chart.getTitle().getFont()))).append(",\n");
        json.append("  \"chartBackground\": ").append(q(paint(chart.getBackgroundPaint()))).append(",\n");
        Plot plot = chart.getPlot();
        PlotRenderingInfo pinfo = info.getPlotInfo();
        json.append("  \"dataArea\": ").append(rect(pinfo.getDataArea())).append(",\n");
        List<XYPlot> subplots = new ArrayList<>();
        if (plot instanceof CombinedDomainXYPlot combined) {
            for (Object sp : combined.getSubplots()) subplots.add((XYPlot) sp);
            json.append("  \"combined\": true, \"gap\": ").append(combined.getGap()).append(",\n");
            json.append("  \"domain\": ").append(axis(combined.getDomainAxis(), g2, pinfo.getDataArea(), RectangleEdge.BOTTOM)).append(",\n");
            json.append("  \"subplots\": [\n");
            for (int i = 0; i < subplots.size(); i++) {
                json.append("    ").append(xyplot(subplots.get(i), g2, pinfo.getSubplotInfo(i).getDataArea(), true)).append(i + 1 < subplots.size() ? ",\n" : "\n");
            }
            json.append("  ],\n");
        } else {
            XYPlot xy = (XYPlot) plot;
            json.append("  \"domain\": ").append(axis(xy.getDomainAxis(), g2, pinfo.getDataArea(), RectangleEdge.BOTTOM)).append(",\n");
            json.append("  \"subplots\": [").append(xyplot(xy, g2, pinfo.getDataArea(), false)).append("],\n");
        }
        json.append("  \"legend\": [");
        LegendItemCollection items = plot.getLegendItems();
        for (int i = 0; i < items.getItemCount(); i++) {
            LegendItem item = items.get(i);
            Rectangle2D sb = item.getShape() == null ? null : item.getShape().getBounds2D();
            json.append(i > 0 ? ", " : "").append("{\"label\": ").append(q(item.getLabel())).append(", \"fill\": ").append(q(paint(item.getFillPaint())))
                    .append(", \"line\": ").append(q(paint(item.getLinePaint()))).append(", \"lineVisible\": ").append(item.isLineVisible())
                    .append(", \"shapeVisible\": ").append(item.isShapeVisible()).append(", \"shape\": ").append(sb == null ? "null" : rect(sb)).append("}");
        }
        json.append("],\n  \"legendFont\": ").append(q(font(chart.getLegend().getItemFont()))).append(", \"legendPaint\": ").append(q(paint(chart.getLegend().getItemPaint()))).append("\n}\n");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(new File(out, name + ".json")), StandardCharsets.UTF_8)) { w.write(json.toString()); }
        g2.dispose();
    }

    static String xyplot(XYPlot xy, Graphics2D g2, Rectangle2D area, boolean subplot) {
        StringBuilder s = new StringBuilder("{");
        s.append("\"dataArea\": ").append(rect(area));
        s.append(", \"background\": ").append(q(paint(xy.getBackgroundPaint())));
        s.append(", \"outline\": ").append(q(paint(xy.getOutlinePaint()))).append(", \"outlineStroke\": ").append(stroke(xy.getOutlineStroke()));
        s.append(", \"domainGridlines\": ").append(xy.isDomainGridlinesVisible() ? q(paint(xy.getDomainGridlinePaint())) : "null");
        s.append(", \"rangeGridlines\": ").append(xy.isRangeGridlinesVisible() ? q(paint(xy.getRangeGridlinePaint())) : "null");
        s.append(", \"gridlineStroke\": ").append(stroke(xy.getDomainGridlineStroke()));
        s.append(", \"axisOffset\": ").append(xy.getAxisOffset().getTop());
        if (!subplot) s.append(", \"domain\": ").append(axis(xy.getDomainAxis(), g2, area, RectangleEdge.BOTTOM));
        s.append(", \"ranges\": [");
        for (int i = 0; i < xy.getRangeAxisCount(); i++) {
            ValueAxis axis = xy.getRangeAxis(i);
            if (axis == null) continue;
            s.append(i > 0 ? ", " : "").append(axis(axis, g2, area, xy.getRangeAxisEdge(i)));
        }
        s.append("], \"datasets\": [");
        boolean first = true;
        for (int d = 0; d < xy.getDatasetCount(); d++) {
            XYDataset dataset = xy.getDataset(d);
            if (dataset == null) continue;
            XYItemRenderer r = xy.getRendererForDataset(dataset);
            for (int i = 0; i < dataset.getSeriesCount(); i++) {
                s.append(first ? "" : ", ").append("{\"key\": ").append(q(String.valueOf(dataset.getSeriesKey(i))));
                s.append(", \"renderer\": ").append(q(r.getClass().getSimpleName()));
                s.append(", \"paint\": ").append(q(paint(((AbstractRenderer) r).lookupSeriesPaint(i))));
                s.append(", \"stroke\": ").append(stroke(((AbstractRenderer) r).lookupSeriesStroke(i)));
                if (r instanceof XYLineAndShapeRenderer ls) {
                    s.append(", \"lines\": ").append(ls.getItemLineVisible(i, 0)).append(", \"shapes\": ").append(ls.getItemShapeVisible(i, 0))
                            .append(", \"shapesFilled\": ").append(ls.getItemShapeFilled(i, 0)).append(", \"shape\": ").append(rect(ls.lookupSeriesShape(i).getBounds2D()));
                }
                if (r instanceof XYBarRenderer bar) {
                    s.append(", \"shadow\": ").append(bar.getShadowsVisible()).append(", \"shadowOffset\": [").append(bar.getShadowXOffset()).append(", ").append(bar.getShadowYOffset()).append("]")
                            .append(", \"barOutline\": ").append(bar.isDrawBarOutline()).append(", \"margin\": ").append(bar.getMargin());
                }
                if (r instanceof HighLowRenderer hl) {
                    s.append(", \"tickLength\": ").append(hl.getTickLength()).append(", \"openTicks\": ").append(hl.getDrawOpenTicks()).append(", \"closeTicks\": ").append(hl.getDrawCloseTicks());
                }
                s.append(", \"itemLabels\": ").append(((AbstractRenderer) r).isSeriesItemLabelsVisible(i)).append(", \"itemLabelFont\": ").append(q(font(((AbstractRenderer) r).getItemLabelFont(i, 0))))
                        .append(", \"itemLabelPaint\": ").append(q(paint(((AbstractRenderer) r).getItemLabelPaint(i, 0))));
                XYItemLabelGenerator gen = r.getItemLabelGenerator(i, 0);
                if (gen != null && dataset.getItemCount(i) > 0) s.append(", \"itemLabel0\": ").append(q(gen.generateLabel(dataset, i, 0)));
                s.append("}");
                first = false;
            }
        }
        return s.append("]}").toString();
    }

    static String axis(ValueAxis axis, Graphics2D g2, Rectangle2D area, RectangleEdge edge) {
        StringBuilder s = new StringBuilder("{");
        s.append("\"label\": ").append(q(axis.getLabel())).append(", \"type\": ").append(q(axis.getClass().getSimpleName()));
        s.append(", \"lower\": ").append(axis.getLowerBound()).append(", \"upper\": ").append(axis.getUpperBound());
        s.append(", \"lowerMargin\": ").append(axis.getLowerMargin()).append(", \"upperMargin\": ").append(axis.getUpperMargin());
        if (axis instanceof NumberAxis n) s.append(", \"includesZero\": ").append(n.getAutoRangeIncludesZero());
        s.append(", \"labelFont\": ").append(q(font(axis.getLabelFont()))).append(", \"labelPaint\": ").append(q(paint(axis.getLabelPaint())));
        s.append(", \"tickLabelFont\": ").append(q(font(axis.getTickLabelFont()))).append(", \"tickLabelPaint\": ").append(q(paint(axis.getTickLabelPaint())));
        s.append(", \"axisLinePaint\": ").append(q(paint(axis.getAxisLinePaint()))).append(", \"axisLineStroke\": ").append(stroke(axis.getAxisLineStroke()));
        s.append(", \"tickMarkOutside\": ").append(axis.getTickMarkOutsideLength()).append(", \"tickMarkInside\": ").append(axis.getTickMarkInsideLength());
        s.append(", \"tickLabelInsets\": [").append(axis.getTickLabelInsets().getTop()).append(", ").append(axis.getTickLabelInsets().getLeft()).append("]");
        s.append(", \"labelInsets\": [").append(axis.getLabelInsets().getTop()).append(", ").append(axis.getLabelInsets().getLeft()).append("]");
        if (axis instanceof SymbolAxis sym) s.append(", \"symbols\": ").append(list(sym.getSymbols()));
        s.append(", \"ticks\": [");
        List<?> ticks = axis.refreshTicks(g2, new AxisState(), area, edge);
        for (int i = 0; i < ticks.size(); i++) {
            Object t = ticks.get(i);
            double value = t instanceof DateTick dt ? dt.getDate().getTime() : ((NumberTick) t).getValue();
            String text = ((org.jfree.chart.axis.Tick) t).getText();
            s.append(i > 0 ? ", " : "").append("{\"value\": ").append(t instanceof DateTick ? String.valueOf((long) value) : String.valueOf(value)).append(", \"label\": ").append(q(text)).append("}");
        }
        s.append("]");
        if (axis instanceof DateAxis d) s.append(", \"tickUnit\": ").append(q(d.getTickUnit().getUnitType() + "x" + d.getTickUnit().getMultiple()));
        if (axis instanceof NumberAxis n) s.append(", \"tickUnit\": ").append(n.getTickUnit().getSize());
        return s.append("}").toString();
    }


    // --- renderer input, in the shape MeasurementChart.toConfig() produces ------------------------

    static String style(String theme, String plotBackground, String gridlines, double dl, double du, double rl, double ru, boolean rangeZero, boolean domainZero, String rangeAxisPaint, String itemLabels) {
        return String.format(Locale.ROOT, "{\"theme\": %s, \"plotBackground\": %s, \"gridlines\": %s, \"domainMargins\": [%s, %s], \"rangeMargins\": [%s, %s], \"rangeIncludesZero\": %s, \"domainIncludesZero\": %s, \"rangeAxisPaint\": %s, \"itemLabels\": %s}",
                q(theme), q(plotBackground), q(gridlines), dl, du, rl, ru, rangeZero, domainZero, q(rangeAxisPaint), itemLabels == null ? "null" : itemLabels);
    }
    static String itemLabels(String format, String decimals, int fontSize) {
        return "{\"format\": " + q(format) + ", \"decimals\": " + q(decimals) + ", \"fontSize\": " + fontSize + "}";
    }
    static String input(String title, String xLabel, String yLabel, boolean dateAxis, String style, String datasets, String medications, String secondAxis) {
        return "{\"title\": " + q(title) + ", \"xLabel\": " + q(xLabel) + ", \"yLabel\": " + q(yLabel) + ", \"dateAxis\": " + dateAxis
                + ", \"timeZone\": \"America/Toronto\", \"style\": " + style + ", \"datasets\": [" + datasets + "], \"medications\": " + medications
                + ", \"secondAxis\": " + q(secondAxis) + "}";
    }
    static String line(String label, String axis, String paint, boolean lines, boolean shapes, TimeSeriesCollection c) {
        return line(label, axis, paint, lines, shapes, c, false);
    }
    static String line(String label, String axis, String paint, boolean lines, boolean shapes, TimeSeriesCollection c, boolean highLow) {
        StringBuilder d = new StringBuilder();
        TimeSeries ts = c.getSeries(0);
        for (int i = 0; i < ts.getItemCount(); i++) d.append(i > 0 ? ", " : "").append("{\"x\": ").append(ts.getTimePeriod(i).getFirstMillisecond()).append(", \"y\": ").append(ts.getValue(i)).append("}");
        return "{\"label\": " + q(label) + ", \"kind\": \"line\", \"yAxisID\": " + q(axis) + ", \"paint\": " + q(paint) + ", \"lines\": " + lines + ", \"shapes\": " + shapes
                + (highLow ? ", \"highLow\": true" : "") + ", \"data\": [" + d + "]}";
    }
    static String xy(String label, String paint, boolean lines, boolean shapes, XYSeries s) {
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < s.getItemCount(); i++) d.append(i > 0 ? ", " : "").append("{\"x\": ").append(s.getX(i)).append(", \"y\": ").append(s.getY(i)).append("}");
        return "{\"label\": " + q(label) + ", \"kind\": \"line\", \"yAxisID\": \"y\", \"paint\": " + q(paint) + ", \"lines\": " + lines + ", \"shapes\": " + shapes + ", \"data\": [" + d + "]}";
    }
    static String range(String label, String paint, OHLCDataItem[] items, boolean widensAxis) {
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < items.length; i++) d.append(i > 0 ? ", " : "").append("{\"x\": ").append(items[i].getDate().getTime()).append(", \"low\": ").append(items[i].getLow()).append(", \"high\": ").append(items[i].getHigh()).append("}");
        return "{\"label\": " + q(label) + ", \"kind\": \"range\", \"yAxisID\": \"y\", \"paint\": " + q(paint) + ", \"widensAxis\": " + widensAxis + ", \"data\": [" + d + "]}";
    }
    static String meds(List<TaskSeries> drugs) {
        StringBuilder d = new StringBuilder("[");
        boolean first = true;
        for (TaskSeries ts : drugs) for (Object o : ts.getTasks()) {
            Task t = (Task) o;
            d.append(first ? "" : ", ").append("{\"name\": ").append(q(t.getDescription())).append(", \"start\": ").append(t.getDuration().getStart().getTime()).append(", \"end\": ").append(t.getDuration().getEnd().getTime()).append("}");
            first = false;
        }
        return d.append("]").toString();
    }


    /** Java's line heights and sample widths for the theme fonts, which JFreeChart's tick unit choice depends on. */
    static String metrics(Graphics2D g2) {
        StringBuilder m = new StringBuilder("{");
        boolean first = true;
        for (String family : new String[]{"Tahoma", "SansSerif", "helvetica"}) {
            for (int size : new int[]{8, 10, 12, 14}) {
                Font f = new Font(family, Font.PLAIN, size);
                double height = f.getLineMetrics("123", g2.getFontRenderContext()).getHeight();
                FontMetrics fm = g2.getFontMetrics(f);
                m.append(first ? "" : ", ").append(q(family + " " + size)).append(": {\"lineHeight\": ").append(String.format(Locale.ROOT, "%.3f", height))
                        .append(", \"ascent\": ").append(fm.getAscent()).append(", \"descent\": ").append(fm.getDescent())
                        .append(", \"widths\": {\"Apr-2022\": ").append(fm.stringWidth("Apr-2022")).append(", \"14-Nov\": ").append(fm.stringWidth("14-Nov"))
                        .append(", \"1,000\": ").append(fm.stringWidth("1,000")).append(", \"2024-01-15\": ").append(fm.stringWidth("2024-01-15")).append("}}");
                first = false;
            }
        }
        return m.append("}").toString();
    }

    // --- helpers --------------------------------------------------------------------------------

    static TimeSeriesCollection series(String key, Object[] values) {
        TimeSeries s = new TimeSeries(key);
        for (int i = 0; i < values.length; i += 2) s.addOrUpdate(new Day(day((String) values[i])), ((Number) values[i + 1]).doubleValue());
        TimeSeriesCollection c = new TimeSeriesCollection(); c.addSeries(s); return c;
    }
    static Object[] pts(Object... v) { return v; }
    static Date day(String iso) throws RuntimeException {
        try { SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd"); f.setTimeZone(TZ); return f.parse(iso); } catch (Exception e) { throw new RuntimeException(e); }
    }
    static String plus(String iso, int days) { Calendar c = Calendar.getInstance(TZ); c.setTime(day(iso)); c.add(Calendar.DAY_OF_MONTH, days); SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd"); f.setTimeZone(TZ); return f.format(c.getTime()); }
    static TaskSeries task(String name, String start, String end) { TaskSeries ts = new TaskSeries(name); ts.add(new Task(name, day(start), day(end))); return ts; }
    static String symbol(String name) { return name.length() > 30 ? name.substring(0, 30) + "..." : name; }
    static String q(String s) { return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""; }
    static String list(String[] v) { StringBuilder s = new StringBuilder("["); for (int i = 0; i < v.length; i++) s.append(i > 0 ? ", " : "").append(q(v[i])); return s.append("]").toString(); }
    static String rect(Rectangle2D r) { return String.format(Locale.ROOT, "[%.1f, %.1f, %.1f, %.1f]", r.getX(), r.getY(), r.getWidth(), r.getHeight()); }
    static String font(Font f) { return f == null ? null : f.getName() + " " + (f.isBold() ? "bold " : "") + (f.isItalic() ? "italic " : "") + f.getSize(); }
    static String paint(Paint p) { return p instanceof Color c ? String.format("#%02x%02x%02x%s", c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha() == 255 ? "" : String.format("%02x", c.getAlpha())) : String.valueOf(p); }
    static String stroke(Stroke s) {
        if (!(s instanceof BasicStroke b)) return "null";
        return String.format(Locale.ROOT, "{\"width\": %.2f, \"dash\": %s}", b.getLineWidth(), b.getDashArray() == null ? "null" : Arrays.toString(b.getDashArray()));
    }
}
