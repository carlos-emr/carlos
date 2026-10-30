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

    private static String document(List<String> images) {
        StringBuilder html = new StringBuilder("<html><head><title>FAKE-Memory probe</title></head><body><h1>FAKE-Memory probe</h1>");
        for (String image : images) {
            html.append("<p><img src=\"").append(image).append("\" width=\"300\"></p>");
        }
        return html.append("<p>End of the document.</p></body></html>").toString();
    }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]).toAbsolutePath().normalize();
        HtmlPdfRenderService service = HtmlPdfTestServices.newService();
        long maxMemoryMb = Runtime.getRuntime().maxMemory() >> 20;
        System.out.println("child JVM maximum heap " + maxMemoryMb + " MB");

        List<List<String>> documents = new ArrayList<>();
        documents.add(NEAR_CAP_PNGS);
        documents.add(NEAR_CAP_PNGS);
        documents.add(NEAR_CAP_PNGS.subList(0, 3));
        documents.add(List.of(LARGE_JPEG, LARGE_SCAN, MORE_SCANS.get(0), MORE_SCANS.get(1), NEAR_CAP_PNGS.get(0)));
        documents.add(List.of(LARGE_JPEG, LARGE_SCAN));
        documents.add(NEAR_CAP_PNGS);
        documents.add(NEAR_CAP_PNGS);
        documents.add(List.of(MORE_SCANS.get(1), LARGE_SCAN, MORE_SCANS.get(0), LARGE_JPEG));

        ExecutorService executor = Executors.newFixedThreadPool(4);
        int failures = 0;
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < documents.size(); i++) {
                int number = i;
                Callable<String> render = () -> {
                    HtmlPdfRenderRequest request = new HtmlPdfRenderRequest(document(documents.get(number)), directory,
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
                    return "document " + number + " " + documents.get(number) + ": " + result.size() / 1024 + " KB, "
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
