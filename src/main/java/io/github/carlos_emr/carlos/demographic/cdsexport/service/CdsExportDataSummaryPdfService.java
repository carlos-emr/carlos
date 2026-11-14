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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderRequest;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderSource;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfRenderService;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

/**
 * Renders one data summary to PDF through the one HTML-to-PDF module ({@link
 * HtmlPdfRenderService}: bundled DejaVu fonts only, no network, no host font), then proves the
 * bytes are a loadable PDF with at least one page before anything is staged.
 *
 * <p>The PDF is the only carrier of a record that has no CDS element, so a record the PDF cannot
 * show completely is never held and never shortened. It is exported with the exact record
 * attached inside the same PDF (PDF embedded files, which any PDF reader lists as attachments),
 * and the page and the Reports entry say so:
 *
 * <ul>
 *   <li><b>A character the page cannot show as itself.</b> The module prints a character no
 *       bundled face has a glyph for (Hindi, Punjabi, Tamil and Thai among them) as U+FFFD, and
 *       the summary's HTML carries a control character, a lone surrogate or a non-character as
 *       a space; a format character (a zero-width joiner, a soft hyphen) draws nothing. This is
 *       known before rendering ({@link #exact}).
 *   <li><b>An image that did not print.</b> No render slot came free (the module prints raster
 *       images as boxes, {@code IMAGES_OMITTED_FOR_MEMORY}; the render is first repeated {@value
 *       #SLOT_ATTEMPTS} times, each waiting for a slot), the image could not be decoded ({@code
 *       IMAGE_UNAVAILABLE}), or the document went down to text only ({@code TEXT_ONLY}).
 *   <li><b>No PDF at all.</b> Every rung of the module's ladder failed or its bytes are not a
 *       PDF. A one-page PDF holding only a notice is written with PDFBox and the bundled DejaVu
 *       Sans, and carries the same attachments.
 * </ul>
 *
 * <p>The attachments are {@value #RECORD_FILE}, every source row of the record with each stored
 * text value exactly (JSON escapes for what a file of text cannot hold) and the size and SHA-256
 * of each binary value, and one file per image the summary shows, as its own bytes. A credential
 * the summary withholds is withheld there too. A text-only or safe-mode render of a record
 * without an image still prints every character and is accepted as it is. Only an interrupted
 * wait is not answered this way: it is a cancellation.
 *
 * @since 2026-11-13
 */
@Service
public class CdsExportDataSummaryPdfService {
    static final String FAILED = "CDS_EXPORT_DATA_SUMMARY_RENDER_FAILED";

    /** The attached file that holds the record's exact values. */
    static final String RECORD_FILE = "carlos-data-summary-record.json";

    /** Printed on the page and written in Notes when the page cannot show all of the record. */
    static final String ATTACHED_NOTICE =
            "This PDF cannot show every character or image of this record exactly: a character"
                    + " that the bundled fonts cannot draw is printed as the replacement"
                    + " character or as a space, and an image that could not be drawn is printed"
                    + " as a box. The exact record is attached inside this PDF. The attached file "
                    + RECORD_FILE
                    + " holds every stored text value exactly, and each image of the record is"
                    + " attached as its own file.";

    /** Printed on the page and written in Notes when the record could not be printed at all. */
    static final String UNPRINTED_NOTICE =
            "CARLOS EMR could not print this record as a PDF, so this PDF holds only this"
                    + " notice. The exact record is attached inside this PDF. The attached file "
                    + RECORD_FILE
                    + " holds every stored text value exactly, and each image of the record is"
                    + " attached as its own file.";

    /**
     * How many times a record with an image is rendered while the module reports that no render
     * slot came free, before its images are sent as attachments instead. It decides how the
     * images travel, never whether.
     */
    static final int SLOT_ATTEMPTS = 6;

    private static final String NOTICE_FONT = "net/sf/jasperreports/fonts/dejavu/DejaVuSans.ttf";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HtmlPdfRenderService renderer;
    private final CdsExportDataSummaryHtmlComposer composer = new CdsExportDataSummaryHtmlComposer();

    public CdsExportDataSummaryPdfService(HtmlPdfRenderService renderer) {
        this.renderer = Objects.requireNonNull(renderer);
    }

    /** The validated PDF of one data summary; see {@link #document}. */
    byte[] render(
            CdsExportDataSummaryPlanner.Group group, CdsExportDataSummaryHtmlComposer.Patient patient)
            throws IOException {
        return document(group, patient).pdf();
    }

    /**
     * The validated PDF of one data summary and, when the page cannot show all of the record,
     * the notice that says where the exact record is (also printed on the page). Never holds the
     * patient for what the page cannot draw.
     */
    CdsExportDataSummaryOccurrenceService.Renderer.Rendered document(
            CdsExportDataSummaryPlanner.Group group, CdsExportDataSummaryHtmlComposer.Patient patient)
            throws IOException {
        boolean waited = exact(group);
        if (waited) {
            byte[] pdf = print(group, patient, true, SLOT_ATTEMPTS);
            if (pdf != null) return new CdsExportDataSummaryOccurrenceService.Renderer.Rendered(pdf, null);
        }
        var noticed =
                group.withNotice(
                        group.notice() == null
                                ? ATTACHED_NOTICE
                                : group.notice() + " " + ATTACHED_NOTICE);
        // A record already rendered the full number of times is not made to wait again.
        byte[] page = print(noticed, patient, false, waited ? 1 : SLOT_ATTEMPTS);
        String notice = page == null ? UNPRINTED_NOTICE : ATTACHED_NOTICE;
        byte[] pdf = attach(page == null ? noticePage() : page, group);
        requirePdf(pdf);
        return new CdsExportDataSummaryOccurrenceService.Renderer.Rendered(pdf, notice);
    }

    /**
     * One summary through the module.
     *
     * @param complete true to accept only a PDF that shows every image of the record
     * @param attempts how many renders to make while the module reports no free slot
     * @return the PDF, or null when none could be made or, if asked, it is not complete
     */
    private byte[] print(
            CdsExportDataSummaryPlanner.Group group,
            CdsExportDataSummaryHtmlComposer.Patient patient,
            boolean complete,
            int attempts)
            throws IOException {
        String html = composer.compose(group, patient);
        // Owner-only, so nobody else can place a resource in the directory the renderer allows.
        Path empty =
                Files.createTempDirectory(
                        "cds-data-summary",
                        java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                                java.nio.file.attribute.PosixFilePermissions.fromString(
                                        "rwx------")));
        try {
            byte[] last = null;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderResult result;
                try {
                    // The summary references nothing outside itself; the only allowed directory
                    // is empty.
                    result =
                            renderer.render(
                                    new HtmlPdfRenderRequest(
                                            html,
                                            null,
                                            List.of(empty.toAbsolutePath().normalize()),
                                            HtmlPdfRenderSource.CDS_DATA_SUMMARY));
                } catch (PDFGenerationException | RuntimeException failed) {
                    interrupted();
                    return null;
                }
                byte[] pdf = result.pdf();
                if (!isPdf(pdf)) return null;
                Set<HtmlPdfRenderDegradation> lost = result.degradations();
                boolean waiting =
                        group.hasImage()
                                && lost.contains(HtmlPdfRenderDegradation.IMAGES_OMITTED_FOR_MEMORY);
                boolean shown =
                        !waiting
                                && !lost.contains(HtmlPdfRenderDegradation.FALLBACK_FONT_UNAVAILABLE)
                                && !(group.hasImage()
                                        && (lost.contains(HtmlPdfRenderDegradation.IMAGE_UNAVAILABLE)
                                                || lost.contains(
                                                        HtmlPdfRenderDegradation.TEXT_ONLY)));
                if (shown) return pdf;
                last = pdf;
                // Only a busy renderer is worth another try; the module waited for a slot itself.
                if (!waiting) break;
                interrupted();
            }
            return complete ? null : last;
        } finally {
            Files.deleteIfExists(empty);
        }
    }

    /** An interrupted wait is the job being stopped, not a record that cannot be printed. */
    private static void interrupted() {
        if (Thread.currentThread().isInterrupted())
            throw new CancellationException("CDS_EXPORT_WAIT_INTERRUPTED");
    }

    /**
     * Whether the page shows every text value of the record as itself. False when a value holds
     * a character the module would print as U+FFFD, one the summary's HTML carries as a space
     * (a control character other than tab and the line breaks, a lone surrogate, a
     * non-character) or one that draws nothing (a format character, a variation selector, a
     * private-use or unassigned code point). False also when the fonts cannot be asked.
     */
    boolean exact(CdsExportDataSummaryPlanner.Group group) {
        try {
            for (var row : group.rows()) {
                var binaries = CdsExportDataSummaryRegistry.rule(row.table()).binaries();
                for (var entry : row.values().entrySet()) {
                    var value = entry.getValue();
                    if (CdsExportDataSummaryRegistry.withheld(row.table(), entry.getKey())) continue;
                    String text =
                            value.text() != null
                                    ? value.text()
                                    : CdsExportDataSummaryHtmlComposer.shownText(
                                            value.content(), binaries.get(entry.getKey()));
                    if (text != null && !(drawn(text) && renderer.drawsEveryCharacter(text)))
                        return false;
                }
            }
            return true;
        } catch (PDFGenerationException | RuntimeException unavailable) {
            return false;
        }
    }

    private static boolean drawn(String text) {
        for (int index = 0; index < text.length(); ) {
            int point = text.codePointAt(index);
            index += Character.charCount(point);
            if (point >= 0x20 && point <= 0x7e || point == '\t' || point == '\n' || point == '\r')
                continue;
            switch (Character.getType(point)) {
                case Character.CONTROL,
                        Character.FORMAT,
                        Character.SURROGATE,
                        Character.PRIVATE_USE,
                        Character.UNASSIGNED -> {
                    return false;
                }
                default -> {
                    if (point >= 0xfe00 && point <= 0xfe0f || point >= 0xe0100 && point <= 0xe01ef)
                        return false;
                }
            }
        }
        return true;
    }

    /**
     * The PDF with the exact record attached inside it: {@value #RECORD_FILE} and one file for
     * each image the summary shows.
     */
    private static byte[] attach(byte[] pdf, CdsExportDataSummaryPlanner.Group group)
            throws IOException {
        try (var document = Loader.loadPDF(pdf)) {
            var files = new LinkedHashMap<String, PDComplexFileSpecification>();
            ObjectNode record = JSON.createObjectNode();
            record.put("carrier", "CARLOS.DataSummary.Record.v1");
            record.put("table", group.table());
            record.put("id", group.id());
            record.put("part", group.part());
            record.put("parts", group.parts());
            var rows = record.putArray("rows");
            int images = 0;
            for (var row : group.rows()) {
                var binaries = CdsExportDataSummaryRegistry.rule(row.table()).binaries();
                var written = rows.addObject();
                written.put("table", row.table());
                var values = written.putObject("values");
                for (var entry : row.values().entrySet()) {
                    var value = entry.getValue();
                    var field = values.putObject(entry.getKey());
                    boolean absent =
                            value.text() == null && value.sha256() == null
                                    || value.text() != null && value.text().isEmpty()
                                    || value.sha256() != null && value.bytes() == 0;
                    if (CdsExportDataSummaryRegistry.withheld(row.table(), entry.getKey())
                            && !absent) {
                        // The same credential the page withholds: stated, never carried.
                        field.put("state", "withheld");
                    } else if (value.text() != null) {
                        field.put("text", value.text());
                    } else if (value.sha256() == null) {
                        field.put("state", "null");
                    } else {
                        field.put("bytes", value.bytes());
                        field.put("sha256", value.sha256());
                        var presentation = binaries.get(entry.getKey());
                        String text =
                                CdsExportDataSummaryHtmlComposer.shownText(
                                        value.content(), presentation);
                        if (text != null) field.put("decodedText", text);
                        var image =
                                CdsExportDataSummaryHtmlComposer.shownImage(
                                        value.content(), presentation);
                        if (image != null) {
                            String name =
                                    "carlos-data-summary-image-"
                                            + ++images
                                            + switch (image.type()) {
                                                case "image/png" -> ".png";
                                                case "image/jpeg" -> ".jpg";
                                                default -> ".gif";
                                            };
                            field.put("file", name);
                            files.put(name, file(document, name, image.type(), image.bytes()));
                        }
                    }
                }
            }
            // Jackson escapes control characters; the rest of what a file of text cannot hold
            // (a lone surrogate, U+FFFE, U+FFFF) becomes a JSON escape here, so the bytes are
            // valid UTF-8 and decode to the exact stored text.
            byte[] json =
                    CdsExportResidualJson.text(JSON.writeValueAsString(record))
                            .getBytes(StandardCharsets.UTF_8);
            var all = new java.util.TreeMap<String, PDComplexFileSpecification>(files);
            all.put(RECORD_FILE, file(document, RECORD_FILE, "application/json", json));
            var tree = new PDEmbeddedFilesNameTreeNode();
            tree.setNames(all);
            var catalog = document.getDocumentCatalog();
            var names = catalog.getNames();
            if (names == null) names = new PDDocumentNameDictionary(catalog);
            names.setEmbeddedFiles(tree);
            catalog.setNames(names);
            var output = new ByteArrayOutputStream(pdf.length + json.length + 1024);
            document.save(output);
            return output.toByteArray();
        }
    }

    private static PDComplexFileSpecification file(
            PDDocument document, String name, String type, byte[] bytes) throws IOException {
        var embedded = new PDEmbeddedFile(document, new ByteArrayInputStream(bytes), COSName.FLATE_DECODE);
        embedded.setSubtype(type);
        embedded.setSize(bytes.length);
        var specification = new PDComplexFileSpecification();
        specification.setFile(name);
        specification.setFileUnicode(name);
        specification.setEmbeddedFile(embedded);
        specification.setEmbeddedFileUnicode(embedded);
        specification.setFileDescription("Exact CARLOS EMR record of this data summary");
        return specification;
    }

    /**
     * One Letter page holding {@link #UNPRINTED_NOTICE}, drawn with the bundled DejaVu Sans and
     * without the HTML module, which has just failed. If even the bundled face cannot be read
     * the page is left empty: the Reports entry's Notes carry the same notice.
     */
    private static byte[] noticePage() throws IOException {
        // The face stays open until the document is saved: its subset is written then.
        try (var document = new PDDocument();
                var face =
                        CdsExportDataSummaryPdfService.class
                                .getClassLoader()
                                .getResourceAsStream(NOTICE_FONT)) {
            var page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            if (face != null) write(document, page, face);
            var output = new ByteArrayOutputStream(8192);
            document.save(output);
            return output.toByteArray();
        }
    }

    private static void write(PDDocument document, PDPage page, java.io.InputStream face) {
        try {
            var font = PDType0Font.load(document, face, true);
            try (var content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 11);
                content.setLeading(15);
                content.newLineAtOffset(54, PDRectangle.LETTER.getHeight() - 72);
                for (String line : lines("CARLOS data summary. " + UNPRINTED_NOTICE, 84)) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
        } catch (IOException | RuntimeException unreadable) {
            // The page stays as far as it was drawn; the notice is also in the entry's Notes.
        }
    }

    private static List<String> lines(String text, int width) {
        var lines = new ArrayList<String>();
        var line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private static boolean isPdf(byte[] pdf) {
        try {
            requirePdf(pdf);
            return true;
        } catch (IOException invalid) {
            return false;
        }
    }

    /** The names of the files attached inside a PDF, for tests and checks. */
    static Map<String, byte[]> attachments(byte[] pdf) throws IOException {
        var result = new LinkedHashMap<String, byte[]>();
        try (var document = Loader.loadPDF(pdf)) {
            var names = document.getDocumentCatalog().getNames();
            var tree = names == null ? null : names.getEmbeddedFiles();
            if (tree == null || tree.getNames() == null) return result;
            for (var entry : tree.getNames().entrySet())
                result.put(entry.getKey(), entry.getValue().getEmbeddedFile().toByteArray());
        }
        return result;
    }

    /** {@code %PDF-} header, loadable by PDFBox, at least one page. */
    static void requirePdf(byte[] pdf) throws IOException {
        if (pdf == null
                || pdf.length < 5
                || !Arrays.equals(
                        Arrays.copyOf(pdf, 5), "%PDF-".getBytes(StandardCharsets.US_ASCII)))
            throw new CdsExportMappingReviewException(FAILED);
        int pages;
        try (var document = Loader.loadPDF(pdf)) {
            pages = document.getNumberOfPages();
        } catch (IOException unreadable) {
            throw new CdsExportMappingReviewException(FAILED);
        }
        if (pages < 1) throw new CdsExportMappingReviewException(FAILED);
    }
}
