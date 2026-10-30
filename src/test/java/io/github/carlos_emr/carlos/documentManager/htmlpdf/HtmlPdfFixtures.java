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
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Test helper: the documents under {@code src/test/resources/htmlpdf} and the web root they are printed
 * against. A fixture is the HTML a caller hands to the render service; the web root is a temporary
 * directory that holds what the document links to ({@code form/annualStyle.css} and {@code form/print.css},
 * copied from the application so that a change to them is tested too, and any images a test generates).
 */
public final class HtmlPdfFixtures {

    private static final Path APPLICATION_FORM_DIRECTORY = Path.of("src/main/webapp/form");

    private HtmlPdfFixtures() {
    }

    /**
     * Reads a fixture.
     *
     * @param name the path under {@code htmlpdf/}, for example {@code annual/female.html}
     * @return its text
     */
    public static String html(String name) {
        try (InputStream in = HtmlPdfFixtures.class.getClassLoader().getResourceAsStream("htmlpdf/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("no such fixture: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Prepares a web root: the application's form style sheets under {@code form/}.
     *
     * @param directory an empty temporary directory
     * @return the directory
     */
    public static Path webRoot(Path directory) {
        try {
            Path form = Files.createDirectories(directory.resolve("form"));
            for (String name : List.of("annualStyle.css", "print.css")) {
                Files.copy(APPLICATION_FORM_DIRECTORY.resolve(name), form.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
            return directory.toAbsolutePath().normalize();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The HRM report with every kind of image ({@code hrm/images.html}): writes the image files it names
     * into the web root and returns its HTML with the inline data URI filled in.
     *
     * @param webRoot the web root the images are written to
     * @return the report's HTML
     */
    public static String imagesReport(Path webRoot) {
        TestImages.write(webRoot, "photo.jpg", TestImages.jpeg(320, 180, false));
        TestImages.write(webRoot, "scan.png", TestImages.png(800, 600));
        TestImages.write(webRoot, "logo.png", TestImages.png(40, 40));
        TestImages.write(webRoot, "picture.bmp", TestImages.bmp(40, 40));
        TestImages.write(webRoot, "pixel.wbmp", TestImages.wbmp(16, 16));
        TestImages.write(webRoot, "spinner.gif", TestImages.gif(24, 24));
        byte[] png = TestImages.png(30, 30);
        byte[] corruptHeader = png.clone();
        Arrays.fill(corruptHeader, 0, 16, (byte) 0x7F);
        TestImages.write(webRoot, "corrupt-header.png", corruptHeader);
        TestImages.write(webRoot, "corrupt-body.png", Arrays.copyOf(png, png.length / 2));
        byte[] jpeg = TestImages.jpeg(120, 80, false);
        TestImages.write(webRoot, "truncated.jpg", TestImages.withOrientation(Arrays.copyOf(jpeg, jpeg.length - 80), 6));
        try (InputStream webp = HtmlPdfFixtures.class.getClassLoader().getResourceAsStream("htmlpdf/images/opaque-4x4.webp");
                InputStream phone = HtmlPdfFixtures.class.getClassLoader().getResourceAsStream("htmlpdf/images/exif-orientation-6.jpg")) {
            TestImages.write(webRoot, "opaque.webp", webp.readAllBytes());
            TestImages.write(webRoot, "phone.jpg", phone.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        TestImages.write(webRoot, "photo.heic", TestImages.heic());
        TestImages.write(webRoot, "attachment.pdf", TestImages.pdf());
        TestImages.write(webRoot, "outside.svg", TestImages.svgWithOutsideReference());
        String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(TestImages.png(20, 20));
        return html("hrm/images.html").replace("@DATA_URI_PNG@", dataUri);
    }

    /**
     * A request for a fixture, rendered against a web root and allowed to read only from it.
     *
     * @param fixture the fixture's name
     * @param webRoot the web root
     * @return the request
     */
    public static HtmlPdfRenderRequest requestFor(String fixture, Path webRoot) {
        return request(html(fixture), webRoot);
    }

    /**
     * A request for HTML, rendered against a web root and allowed to read only from it.
     *
     * @param html the document
     * @param webRoot the web root
     * @return the request
     */
    public static HtmlPdfRenderRequest request(String html, Path webRoot) {
        return new HtmlPdfRenderRequest(html, webRoot, List.of(webRoot), HtmlPdfRenderSource.FORM);
    }

    /**
     * Renders a fixture.
     *
     * @param service the service
     * @param fixture the fixture's name
     * @param webRoot the web root
     * @return the result
     * @throws PDFGenerationException if the service gives up
     */
    public static HtmlPdfRenderResult render(HtmlPdfRenderService service, String fixture, Path webRoot) throws PDFGenerationException {
        return service.render(requestFor(fixture, webRoot));
    }
}
