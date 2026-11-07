/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Prints Java's integer glyph advances for the plain fonts the former measurement charts measured
 * ("SansSerif", which "Tahoma" and "helvetica" resolve to as well: DejaVu Sans in the renderer's
 * container) at 8, 10 and 12px, for U+0020 to U+00FF, under JFreeChart's rendering hints. The output
 * is the JAVA_ADVANCES table in src/main/webapp/library/chartjs/carlos-chart-draw.js. Run it like
 * JfcReference.java (see README.md): javac -cp jfreechart.jar JavaAdvances.java, then
 * java -Djava.awt.headless=true -cp jfreechart.jar:. JavaAdvances
 */
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.TimeSeriesCollection;

public class JavaAdvances {
    public static void main(String[] args) {
        JFreeChart chart = ChartFactory.createTimeSeriesChart("t", "x", "y", new TimeSeriesCollection(), true, true, true);
        Graphics2D g2 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
        g2.setRenderingHints(chart.getRenderingHints());
        StringBuilder out = new StringBuilder("{");
        for (int size : new int[]{8, 10, 12}) {
            FontMetrics metrics = g2.getFontMetrics(new Font("SansSerif", Font.PLAIN, size));
            out.append(size == 8 ? "" : ",").append('"').append(size).append("\":[");
            for (int c = 32; c <= 255; c++) {
                // C1 controls have no glyph; the draw script falls back to Chromium for them.
                out.append(c > 32 ? "," : "").append(c >= 127 && c < 160 ? 0 : metrics.charWidth((char) c));
            }
            out.append(']');
        }
        System.out.println(out.append('}'));
        g2.dispose();
    }
}
