/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Prints Java's integer glyph advances for the fonts the former measurement charts drew with
 * ("SansSerif", which "Tahoma" and "helvetica" resolve to as well: DejaVu Sans in the renderer's
 * container): plain 8, 10 and 12px tick, legend and item labels, and bold 12, 14, 18 and 20px axis
 * labels and titles, for U+0020 to U+00FF, under JFreeChart's rendering hints. The output
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
        String[] keys = {"8", "10", "12", "bold12", "bold14", "bold18", "bold20"};
        for (String key : keys) {
            boolean bold = key.startsWith("bold");
            int size = Integer.parseInt(bold ? key.substring(4) : key);
            FontMetrics metrics = g2.getFontMetrics(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
            out.append(key.equals(keys[0]) ? "" : ",").append('"').append(key).append("\":[");
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
