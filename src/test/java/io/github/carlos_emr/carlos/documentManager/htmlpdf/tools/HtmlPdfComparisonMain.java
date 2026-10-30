/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager.htmlpdf.tools;

import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc;
import io.github.carlos_emr.carlos.documentManager.InternalEDocConverter;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.HtmlPdfFixtures;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderRequest;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderResult;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderSource;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfRenderService;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfTestServices;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * One-off comparison of the old HTML-to-PDF engine (wkhtmltopdf, as {@code InternalEDocConverter} runs it)
 * with {@link HtmlPdfRenderService}, and the measurement that sets the render service's text-only size
 * limit. A tool, not a test: it is run by {@code scripts/htmlpdf/compare-with-wkhtmltopdf.sh}, which
 * builds the class path and gives the JVM the environment wkhtmltopdf needs
 * ({@code LANG=C.UTF-8}, a fontconfig file that lists only DejaVu), and it is deleted together with
 * wkhtmltopdf.
 *
 * <p><b>{@code compare <outputDirectory>}</b> renders every document under {@code src/test/resources/htmlpdf}
 * (and two generated tables) with both engines, from the same input: the output of
 * {@code ConvertToEdoc.tidyDocument}, which is what {@code ConvertToEdoc} hands to either. For each it
 * writes both PDFs, a PNG of the first two pages side by side, and a row of {@code comparison.csv}: pages,
 * page sizes, where the content starts on the first page, and render times (wkhtmltopdf: median of 3 after
 * one warm-up; Flying Saucer: median of 5 after the whole run was warmed up). The checks of the design's
 * "One-off comparison" are computed and printed; the ones that need eyes (nothing drawn over anything else,
 * no hidden value visible) are done on the PNGs.</p>
 *
 * <p><b>{@code size-limit <outputDirectory> [rows,rows,...]}</b> renders generated tables of growing
 * length in {@code NORMAL} mode and prints the layout time against the length of the HTML.</p>
 */
public final class HtmlPdfComparisonMain {

    private static final Path FIXTURES = Path.of("src/test/resources/htmlpdf");
    private static final float LETTER_WIDTH = 612f;
    private static final float LETTER_HEIGHT = 792f;
    /** 10 mm in points: the margin the render service places content at. */
    private static final float MARGIN = 28.35f;
    private static final int WKHTMLTOPDF_TIMED_RENDERS = 3;
    private static final int FLYING_SAUCER_TIMED_RENDERS = 5;
    private static final float MEASURE_DPI = 200f;
    private static final float PICTURE_DPI = 100f;
    /** The folders of documents that stand for what the application prints; the rest are probes. */
    private static final List<String> REAL_DOCUMENTS = List.of("annual/", "hrm/", "email/", "messenger/", "forms/");
    private static final List<Integer> DEFAULT_ROWS = List.of(500, 1000, 2000, 5000, 10_000, 20_000, 40_000, 80_000);

    private HtmlPdfComparisonMain() {
    }

    /**
     * Runs a mode.
     *
     * @param args the mode ({@code compare} or {@code size-limit}), the output directory and, for
     *         {@code size-limit}, a comma-separated list of row counts
     * @throws Exception if a document cannot be prepared or a result cannot be written
     */
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        if (args.length < 2) {
            throw new IllegalArgumentException("usage: compare <outputDirectory> | size-limit <outputDirectory> [rows,rows,...]");
        }
        Path out = Files.createDirectories(Path.of(args[1]).toAbsolutePath().normalize());
        switch (args[0]) {
            case "compare" -> compare(out);
            case "size-limit" -> sizeLimit(out, args.length > 2 ? rowCounts(args[2]) : DEFAULT_ROWS);
            default -> throw new IllegalArgumentException("unknown mode: " + args[0]);
        }
    }

    // ---- compare ----------------------------------------------------------------------------------

    /** A document to render, and whether it stands for what the application prints. */
    private record Case(String name, String html, boolean realDocument) {
    }

    /** One engine's output for a case: the first PDF, the median render time, or the reason it failed. */
    private record Rendered(byte[] pdf, long medianMillis, long[] samples, String failure, String degradations) {
        boolean ok() {
            return pdf != null;
        }
    }

    /**
     * What is measured on a PDF: the page count, the page sizes, where the content starts on page 1 (at
     * 200 dpi) and how far out it reaches on any page (at 100 dpi).
     */
    private record Geometry(int pages, boolean allLetter, String sizes, float left, float top, float minLeft, float maxRight,
            float maxBottom) {

        /** Nothing is drawn outside the 10 mm margins, give or take a pixel of anti-aliasing. */
        boolean fits() {
            return !(minLeft < MARGIN - 1.5f || maxRight > LETTER_WIDTH - MARGIN + 1.5f || maxBottom > LETTER_HEIGHT - MARGIN + 1.5f);
        }
    }

    private static void compare(Path out) throws Exception {
        Path webRoot = freshWebRoot(out.resolve("webroot"));
        List<Case> cases = cases(webRoot);
        HtmlPdfRenderService service = HtmlPdfTestServices.newService();
        warmUp(service, webRoot);

        List<String> csv = new ArrayList<>();
        csv.add("fixture,real_document,wk_pages,fs_pages,wk_sizes,fs_sizes,wk_left_pt,wk_top_pt,fs_left_pt,fs_top_pt,"
                + "wk_ms,fs_warm_ms,size_ok,pages_ok,edges_ok,wk_fits,fs_fits,fs_degradations,wk_failure,fs_failure");
        List<String> verdicts = new ArrayList<>();
        List<Long> realWarmSamples = new ArrayList<>();
        List<Long> realWkSamples = new ArrayList<>();

        for (Case current : cases) {
            String tidied;
            try {
                tidied = tidy(current.html(), webRoot);
            } catch (ReflectiveOperationException | RuntimeException e) {
                // Both engines are handed ConvertToEdoc's output, so there is nothing to compare.
                csv.add(current.name() + "," + current.realDocument() + ",,,,,,,,,,,,,,,,,tidy:" + e.getClass().getSimpleName() + ",");
                verdicts.add(String.format(Locale.ROOT, "%-32s ConvertToEdoc.tidyDocument failed: %s", current.name(), e.getClass().getSimpleName()));
                continue;
            }
            Rendered wk = renderWithWkhtmltopdf(tidied);
            Rendered fs = renderWithFlyingSaucer(service, tidied, webRoot);
            String base = current.name().replace('/', '_');
            if (wk.ok()) {
                Files.write(out.resolve(base + ".wkhtmltopdf.pdf"), wk.pdf());
            }
            if (fs.ok()) {
                Files.write(out.resolve(base + ".flying-saucer.pdf"), fs.pdf());
            }
            Geometry wkGeometry = wk.ok() ? measure(wk.pdf()) : null;
            Geometry fsGeometry = fs.ok() ? measure(fs.pdf()) : null;
            sideBySide(out.resolve(base + ".png"), wk.pdf(), fs.pdf());

            boolean sizeOk = fsGeometry != null && fsGeometry.allLetter();
            boolean pagesOk = fsGeometry != null && wkGeometry != null && pagesAgree(wkGeometry.pages(), fsGeometry.pages());
            boolean edgesOk = fsGeometry != null && edgesAgree(fsGeometry, wkGeometry);
            csv.add(String.join(",", current.name(), String.valueOf(current.realDocument()),
                    pages(wkGeometry), pages(fsGeometry), sizes(wkGeometry), sizes(fsGeometry),
                    edge(wkGeometry, true), edge(wkGeometry, false), edge(fsGeometry, true), edge(fsGeometry, false),
                    wk.ok() ? String.valueOf(wk.medianMillis()) : "", fs.ok() ? String.valueOf(fs.medianMillis()) : "",
                    String.valueOf(sizeOk), String.valueOf(pagesOk), String.valueOf(edgesOk),
                    wkGeometry == null ? "" : String.valueOf(wkGeometry.fits()), fsGeometry == null ? "" : String.valueOf(fsGeometry.fits()),
                    fs.degradations(), clean(wk.failure()), clean(fs.failure())));
            verdicts.add(String.format(Locale.ROOT, "%-32s wk %s  fs %s  size=%s pages=%s edges=%s fits=%s  wk=%s ms  fs=%s ms  %s",
                    current.name(), summary(wkGeometry), summary(fsGeometry), mark(sizeOk), mark(pagesOk), mark(edgesOk),
                    mark(fsGeometry != null && fsGeometry.fits()),
                    wk.ok() ? wk.medianMillis() : "-", fs.ok() ? fs.medianMillis() : "-", fs.degradations()));
            if (current.realDocument()) {
                for (long sample : fs.samples()) {
                    realWarmSamples.add(sample);
                }
                for (long sample : wk.samples()) {
                    realWkSamples.add(sample);
                }
            }
        }

        Files.write(out.resolve("comparison.csv"), csv);
        List<String> summary = new ArrayList<>(verdicts);
        summary.add("");
        summary.add("Render times over the real documents (" + REAL_DOCUMENTS + "):");
        summary.add("  Flying Saucer warm  " + distribution(realWarmSamples));
        summary.add("  wkhtmltopdf         " + distribution(realWkSamples));
        Files.write(out.resolve("summary.txt"), summary);
        summary.forEach(System.out::println);
        System.out.println("Wrote " + out);
    }

    private static List<Case> cases(Path webRoot) throws IOException {
        List<Case> cases = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(FIXTURES)) {
            files = walk.filter(path -> path.toString().endsWith(".html")).sorted().toList();
        }
        for (Path file : files) {
            String name = FIXTURES.relativize(file).toString().replace('\\', '/');
            name = name.substring(0, name.length() - ".html".length());
            // The images report names image files the fixtures helper writes into the web root.
            String html = name.equals("hrm/images") ? HtmlPdfFixtures.imagesReport(webRoot) : Files.readString(file);
            cases.add(new Case(name, html, REAL_DOCUMENTS.stream().anyMatch(name::startsWith)));
        }
        cases.add(new Case("generated/table-500", table(500), true));
        cases.add(new Case("generated/table-2000", table(2000), false));
        return cases;
    }

    private static Path freshWebRoot(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> walk = Files.walk(directory)) {
                for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        return HtmlPdfFixtures.webRoot(Files.createDirectories(directory));
    }

    /** The document as {@code ConvertToEdoc} hands it to either engine (the method is package-private). */
    private static String tidy(String html, Path webRoot) throws Exception {
        Method method = ConvertToEdoc.class.getDeclaredMethod("tidyDocument", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, html, webRoot.toString());
    }

    private static void warmUp(HtmlPdfRenderService service, Path webRoot) throws Exception {
        String page = "<html><body><h1>FAKE-warm-up</h1><table border=\"1\"><tr><td>a</td><td>b</td></tr></table></body></html>";
        for (int i = 0; i < 3; i++) {
            service.render(HtmlPdfFixtures.request(page, webRoot));
        }
    }

    private static Rendered renderWithWkhtmltopdf(String tidied) {
        InternalEDocConverter converter = new InternalEDocConverter();
        try {
            ByteArrayOutputStream first = new ByteArrayOutputStream();
            converter.convert(tidied, first);
            long[] samples = new long[WKHTMLTOPDF_TIMED_RENDERS];
            for (int i = 0; i < samples.length; i++) {
                ByteArrayOutputStream scratch = new ByteArrayOutputStream();
                long started = System.nanoTime();
                converter.convert(tidied, scratch);
                samples[i] = (System.nanoTime() - started) / 1_000_000;
            }
            return new Rendered(first.toByteArray(), median(samples), samples, null, "");
        } catch (IOException | RuntimeException e) {
            return new Rendered(null, 0, new long[0], e.getClass().getSimpleName(), "");
        }
    }

    private static Rendered renderWithFlyingSaucer(HtmlPdfRenderService service, String tidied, Path webRoot) {
        HtmlPdfRenderRequest request = new HtmlPdfRenderRequest(tidied, webRoot, List.of(webRoot), HtmlPdfRenderSource.FORM);
        try {
            HtmlPdfRenderResult first = service.render(request);
            long[] samples = new long[FLYING_SAUCER_TIMED_RENDERS];
            for (int i = 0; i < samples.length; i++) {
                long started = System.nanoTime();
                service.render(request);
                samples[i] = (System.nanoTime() - started) / 1_000_000;
            }
            return new Rendered(first.pdf(), median(samples), samples, null, first.degradations().toString().replace(',', ';'));
        } catch (Exception e) {
            return new Rendered(null, 0, new long[0], e.getClass().getSimpleName(), "");
        }
    }

    private static Geometry measure(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            boolean allLetter = true;
            List<String> sizes = new ArrayList<>();
            for (PDPage page : document.getPages()) {
                float width = page.getMediaBox().getWidth();
                float height = page.getMediaBox().getHeight();
                allLetter &= Math.abs(width - LETTER_WIDTH) < 0.5f && Math.abs(height - LETTER_HEIGHT) < 0.5f;
                String size = Math.round(width) + "x" + Math.round(height);
                if (!sizes.contains(size)) {
                    sizes.add(size);
                }
            }
            PDFRenderer renderer = new PDFRenderer(document);
            float[] edges = inkEdges(renderer.renderImageWithDPI(0, MEASURE_DPI, ImageType.RGB));
            float minLeft = Float.MAX_VALUE;
            float maxRight = 0;
            float maxBottom = 0;
            float pointsPerPixel = 72f / PICTURE_DPI;
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, PICTURE_DPI, ImageType.RGB);
                int[] box = inkBox(image);
                if (box != null) {
                    minLeft = Math.min(minLeft, box[0] * pointsPerPixel);
                    maxRight = Math.max(maxRight, (box[2] + 1) * pointsPerPixel);
                    maxBottom = Math.max(maxBottom, (box[3] + 1) * pointsPerPixel);
                }
            }
            return new Geometry(document.getNumberOfPages(), allLetter, String.join(";", sizes), edges[0], edges[1],
                    minLeft == Float.MAX_VALUE ? Float.NaN : minLeft, maxRight, maxBottom);
        }
    }

    /** {minX, minY, maxX, maxY} in pixels of everything that is not (nearly) white; {@code null} if blank. */
    private static int[] inkBox(BufferedImage image) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int darkest = Math.min(rgb >> 16 & 0xFF, Math.min(rgb >> 8 & 0xFF, rgb & 0xFF));
                if (darkest < 235) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return maxX < 0 ? null : new int[]{minX, minY, maxX, maxY};
    }

    /** The left and top edge, in points, of everything on the page that is not (nearly) white; NaN if blank. */
    private static float[] inkEdges(BufferedImage image) {
        int[] box = inkBox(image);
        float pointsPerPixel = 72f / MEASURE_DPI;
        return box == null ? new float[]{Float.NaN, Float.NaN} : new float[]{box[0] * pointsPerPixel, box[1] * pointsPerPixel};
    }

    /**
     * The design's page-count rule: equal up to three pages; beyond that at most wkhtmltopdf's count and
     * at least 90 % of it.
     */
    static boolean pagesAgree(int wkhtmltopdfPages, int flyingSaucerPages) {
        if (wkhtmltopdfPages <= 3) {
            return flyingSaucerPages == wkhtmltopdfPages;
        }
        return flyingSaucerPages <= wkhtmltopdfPages && flyingSaucerPages >= 0.9 * wkhtmltopdfPages;
    }

    /** The design's edge rule: at the 10 mm margin, or within 4 pt of where wkhtmltopdf starts. */
    static boolean edgesAgree(Geometry flyingSaucer, Geometry wkhtmltopdf) {
        boolean atMargin = near(flyingSaucer.left(), MARGIN, 1f) && near(flyingSaucer.top(), MARGIN, 1f);
        boolean likeWkhtmltopdf = wkhtmltopdf != null && near(flyingSaucer.left(), wkhtmltopdf.left(), 4f)
                && near(flyingSaucer.top(), wkhtmltopdf.top(), 4f);
        return atMargin || likeWkhtmltopdf;
    }

    private static boolean near(float value, float expected, float tolerance) {
        return !Float.isNaN(value) && !Float.isNaN(expected) && Math.abs(value - expected) <= tolerance;
    }

    /** The first two pages of each engine's PDF, wkhtmltopdf on the left, Flying Saucer on the right. */
    private static void sideBySide(Path png, byte[] wkhtmltopdf, byte[] flyingSaucer) throws IOException {
        List<BufferedImage> left = pictures(wkhtmltopdf);
        List<BufferedImage> right = pictures(flyingSaucer);
        int rows = Math.max(1, Math.max(left.size(), right.size()));
        int pageWidth = Math.round(LETTER_WIDTH * PICTURE_DPI / 72f);
        int pageHeight = Math.round(LETTER_HEIGHT * PICTURE_DPI / 72f);
        int gutter = 24;
        int caption = 22;
        BufferedImage sheet = new BufferedImage(2 * pageWidth + 3 * gutter, rows * (pageHeight + caption + gutter) + gutter,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0x808080));
            g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            for (int row = 0; row < rows; row++) {
                int y = gutter + row * (pageHeight + caption + gutter);
                draw(g, "wkhtmltopdf, page " + (row + 1), row < left.size() ? left.get(row) : null, gutter, y, caption);
                draw(g, "Flying Saucer, page " + (row + 1), row < right.size() ? right.get(row) : null,
                        2 * gutter + pageWidth, y, caption);
            }
        } finally {
            g.dispose();
        }
        ImageIO.write(sheet, "png", png.toFile());
    }

    private static void draw(Graphics2D g, String caption, BufferedImage page, int x, int y, int captionHeight) {
        g.setColor(Color.WHITE);
        g.drawString(caption + (page == null ? " (none)" : ""), x, y + captionHeight - 8);
        if (page != null) {
            g.drawImage(page, x, y + captionHeight, null);
            g.setColor(new Color(0xC00000));
            g.drawRect(x, y + captionHeight, page.getWidth() - 1, page.getHeight() - 1);
        }
    }

    private static List<BufferedImage> pictures(byte[] pdf) throws IOException {
        List<BufferedImage> pictures = new ArrayList<>();
        if (pdf == null) {
            return pictures;
        }
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int page = 0; page < Math.min(2, document.getNumberOfPages()); page++) {
                pictures.add(renderer.renderImageWithDPI(page, PICTURE_DPI, ImageType.RGB));
            }
        }
        return pictures;
    }

    // ---- size limit -------------------------------------------------------------------------------

    private static void sizeLimit(Path out, List<Integer> rowCounts) throws Exception {
        Path webRoot = freshWebRoot(out.resolve("webroot"));
        // No limit: the point is to see how long a normal-mode layout takes at each length. A limit of zero
        // sends every document straight to text-only mode, the last rung of the ladder.
        HtmlPdfRenderService unlimited = HtmlPdfTestServices.newService(Integer.MAX_VALUE);
        HtmlPdfRenderService textOnly = HtmlPdfTestServices.newService(0);
        warmUp(unlimited, webRoot);
        warmUp(textOnly, webRoot);
        Runtime runtime = Runtime.getRuntime();
        List<String> lines = new ArrayList<>();
        lines.add("# " + System.getProperty("java.vm.name") + " " + System.getProperty("java.version") + ", "
                + runtime.availableProcessors() + " processors, max heap " + runtime.maxMemory() / (1024 * 1024) + " MB");
        lines.add("rows,html_chars,normal_pages,normal_ms,normal_degradations,text_only_pages,text_only_ms");
        System.out.println(lines.get(0));
        System.out.println(lines.get(1));
        for (int rows : rowCounts) {
            String html = table(rows);
            HtmlPdfRenderRequest request = HtmlPdfFixtures.request(html, webRoot);
            String line = rows + "," + html.length() + ",";
            try {
                System.gc();
                long started = System.nanoTime();
                HtmlPdfRenderResult normal = unlimited.render(request);
                long normalMillis = (System.nanoTime() - started) / 1_000_000;
                line += pageCount(normal.pdf()) + "," + normalMillis + "," + normal.degradations().toString().replace(',', ';') + ",";
            } catch (Exception | OutOfMemoryError e) {
                line += ",," + e.getClass().getSimpleName() + ",";
            }
            try {
                System.gc();
                long started = System.nanoTime();
                HtmlPdfRenderResult plain = textOnly.render(request);
                long plainMillis = (System.nanoTime() - started) / 1_000_000;
                line += pageCount(plain.pdf()) + "," + plainMillis;
            } catch (Exception | OutOfMemoryError e) {
                line += "," + e.getClass().getSimpleName();
            }
            lines.add(line);
            System.out.println(line);
        }
        Files.write(out.resolve("size-limit.csv"), lines);
    }

    private static int pageCount(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    private static List<Integer> rowCounts(String list) {
        return Arrays.stream(list.split(",")).map(String::trim).filter(text -> !text.isEmpty()).map(Integer::valueOf).toList();
    }

    /** A laboratory-style table of {@code rows} rows, the shape of the long HRM and results reports. */
    static String table(int rows) {
        StringBuilder html = new StringBuilder(rows * 110 + 600);
        html.append("<html><head><title>FAKE-Laboratory results</title></head><body><h2>FAKE-Laboratory results</h2>")
                .append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"3\" width=\"100%\"><thead><tr>")
                .append("<th>Test</th><th>Result</th><th>Units</th><th>Reference</th></tr></thead><tbody>");
        for (int i = 0; i < rows; i++) {
            html.append("<tr><td>FAKE-test-").append(i).append("</td><td>").append(100 + i % 97)
                    .append("</td><td>mmol/L</td><td>3.5 - 5.1</td></tr>");
        }
        return html.append("</tbody></table></body></html>").toString();
    }

    // ---- formatting -------------------------------------------------------------------------------

    private static long median(long[] samples) {
        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static String distribution(List<Long> samples) {
        if (samples.isEmpty()) {
            return "no samples";
        }
        long[] sorted = samples.stream().mapToLong(Long::longValue).sorted().toArray();
        return String.format(Locale.ROOT, "n=%d  median %d ms  p95 %d ms  max %d ms", sorted.length, sorted[sorted.length / 2],
                sorted[(int) Math.min(sorted.length - 1, Math.ceil(0.95 * sorted.length) - 1)], sorted[sorted.length - 1]);
    }

    private static String pages(Geometry geometry) {
        return geometry == null ? "" : String.valueOf(geometry.pages());
    }

    private static String sizes(Geometry geometry) {
        return geometry == null ? "" : geometry.sizes();
    }

    private static String edge(Geometry geometry, boolean left) {
        return geometry == null ? "" : String.format(Locale.ROOT, "%.1f", left ? geometry.left() : geometry.top());
    }

    private static String summary(Geometry geometry) {
        return geometry == null ? "[failed]                       "
                : String.format(Locale.ROOT, "[%3dp %-7s %5.1f,%5.1f]", geometry.pages(), geometry.sizes(), geometry.left(), geometry.top());
    }

    private static String mark(boolean ok) {
        return ok ? "ok " : "NO ";
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace(',', ';');
    }
}
