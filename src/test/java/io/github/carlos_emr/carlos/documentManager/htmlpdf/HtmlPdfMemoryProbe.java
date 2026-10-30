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
package io.github.carlos_emr.carlos.documentManager.htmlpdf;

import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderRequest;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderResult;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderSource;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfRenderService;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfTestServices;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The program {@link HtmlPdfMemoryUnitTest} runs in a child JVM with a 256 MB heap that ends at the first
 * {@code OutOfMemoryError}. The images are already in the directory it is given (the parent has the heap to
 * make a 36-megapixel picture); this program renders documents full of them on four threads and exits 0
 * only if every document came out as a PDF with at least one page.
 *
 * <p>The documents are the spec's: six near-cap PNGs in one document, four such documents at once, and a
 * document with a large JPEG and a large streamed PNG scan. "Near the cap" is relative to the small heap:
 * with 256 MB a render slot's share of image memory is about 31 MB, so a picture is capped at about 1.9
 * megapixels and six of them cannot all be held at full size.</p>
 *
 * <p>Two more are SVGs that Flying Saucer cannot draw as vectors (they hold a gradient): it makes a bitmap of
 * one at the size of its box in device dots, so a chart of 1000 x 1000 CSS pixels would be 400 million pixels,
 * 1.6 GB. The documents hold three of those as {@code <img>}, three as inline {@code <svg>} and the tiles
 * of a repeating background; they print only because the bitmaps are bounded and made once.</p>
 */
public final class HtmlPdfMemoryProbe {

    private HtmlPdfMemoryProbe() {
    }

    /** The file names the test writes into the directory. */
    static final List<String> NEAR_CAP_PNGS = List.of("near-1.png", "near-2.png", "near-3.png", "near-4.png", "near-5.png", "near-6.png");
    static final String LARGE_JPEG = "large.jpg";
    static final String LARGE_SCAN = "scan.png";
    /** Copies of the scan under other names: the planner counts a source once, so three scans cost three times. */
    static final List<String> MORE_SCANS = List.of("scan-2.png", "scan-3.png");
    /** SVG files with a gradient, so that Flying Saucer draws them as bitmaps: 100 x 100 and a 4 x 4 tile. */
    static final String GRADIENT_SVG = "gradient.svg";
    static final String TILE_SVG = "tile.svg";

    /** A document to render and what to call it in the output. */
    private record Document(String label, String html) {
    }

    private static Document document(List<String> images) {
        StringBuilder html = new StringBuilder("<html><head><title>FAKE-Memory probe</title></head><body><h1>FAKE-Memory probe</h1>");
        for (String image : images) {
            html.append("<p><img src=\"").append(image).append("\" width=\"300\"></p>");
        }
        return new Document(images.toString(), html.append("<p>End of the document.</p></body></html>").toString());
    }

    private static Document page(String label, String body) {
        return new Document(label, "<html><head><title>FAKE-Memory probe</title></head><body><h1>FAKE-Memory probe</h1>" + body
                + "<p>End of the document.</p></body></html>");
    }

    /** Three SVG files of 1000 x 1000 CSS pixels, and one very wide: 20,000 x 20,000 dots each as a bitmap. */
    private static Document hugeSvgImages() {
        return page("[huge gradient SVG images]", "<p><img src=\"" + GRADIENT_SVG + "\" width=\"1000\" height=\"1000\"></p>"
                + "<p><img src=\"" + GRADIENT_SVG + "\" width=\"1000\" height=\"1000\"></p>"
                + "<p><img src=\"" + GRADIENT_SVG + "\" style=\"width: 3000px; height: 40px\"></p>"
                + "<p><img src=\"" + GRADIENT_SVG + "\" width=\"700\"></p>");
    }

    /** Inline SVGs with a gradient of 900 x 900 CSS pixels, and a background of 40,000 tiles. */
    private static Document hugeInlineSvgsAndTiles() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"900\" height=\"900\" style=\"display: block\"><defs>"
                + "<linearGradient id=\"g\"><stop offset=\"0\" stop-color=\"#000\"/><stop offset=\"1\" stop-color=\"#fff\"/></linearGradient></defs>"
                + "<rect width=\"900\" height=\"900\" fill=\"url(#g)\"/></svg>";
        return page("[huge inline SVGs and a tiled SVG background]", "<p>" + svg + "</p><p>" + svg + "</p><p>" + svg + "</p>"
                + "<div style=\"width: 800px; height: 800px; background-image: url(" + TILE_SVG + ")\"></div>");
    }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]).toAbsolutePath().normalize();
        HtmlPdfRenderService service = HtmlPdfTestServices.newService();
        long maxMemoryMb = Runtime.getRuntime().maxMemory() >> 20;
        System.out.println("child JVM maximum heap " + maxMemoryMb + " MB");

        List<Document> documents = new ArrayList<>();
        documents.add(document(NEAR_CAP_PNGS));
        documents.add(document(NEAR_CAP_PNGS));
        documents.add(document(NEAR_CAP_PNGS.subList(0, 3)));
        documents.add(document(List.of(LARGE_JPEG, LARGE_SCAN, MORE_SCANS.get(0), MORE_SCANS.get(1), NEAR_CAP_PNGS.get(0))));
        documents.add(document(List.of(LARGE_JPEG, LARGE_SCAN)));
        documents.add(document(NEAR_CAP_PNGS));
        documents.add(document(NEAR_CAP_PNGS));
        documents.add(document(List.of(MORE_SCANS.get(1), LARGE_SCAN, MORE_SCANS.get(0), LARGE_JPEG)));
        documents.add(hugeSvgImages());
        documents.add(hugeInlineSvgsAndTiles());

        ExecutorService executor = Executors.newFixedThreadPool(4);
        int failures = 0;
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < documents.size(); i++) {
                int number = i;
                Callable<String> render = () -> {
                    HtmlPdfRenderRequest request = new HtmlPdfRenderRequest(documents.get(number).html(), directory,
                            List.of(directory), HtmlPdfRenderSource.FORM);
                    HtmlPdfRenderResult result = service.render(request);
                    byte[] pdf = result.pdf();
                    if (pdf.length < 5 || !new String(pdf, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
                        throw new IllegalStateException("not a PDF");
                    }
                    try (PDDocument loaded = Loader.loadPDF(pdf)) {
                        if (loaded.getNumberOfPages() < 1) {
                            throw new IllegalStateException("no pages");
                        }
                    }
                    return "document " + number + " " + documents.get(number).label() + ": " + result.size() / 1024 + " KB, "
                            + result.degradations();
                };
                results.add(executor.submit(render));
            }
            for (Future<String> result : results) {
                try {
                    System.out.println(result.get(4, TimeUnit.MINUTES));
                } catch (Exception failure) {
                    failures++;
                    System.out.println("FAILED: " + failure);
                }
            }
        } finally {
            executor.shutdownNow();
        }
        System.out.println(failures == 0 ? "all " + documents.size() + " documents were produced" : failures + " document(s) failed");
        System.exit(failures == 0 ? 0 : 1);
    }
}
