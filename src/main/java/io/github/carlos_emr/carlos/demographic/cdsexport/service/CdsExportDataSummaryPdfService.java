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
 *       Sans, and carries the same attachments. When that page cannot be made, or will not take
 *       the attachments, it is made once more.
 *   <li><b>Not even the notice page.</b> The summary is exported as HTML instead ({@link
 *       #html}): the composer's HTML, which shows every value, and the exact record
 *       ({@value #RECORD_FILE}) as its own text, each written as a Reports entry of Format Text
 *       whose Notes say why ({@link #HTML_NOTICE}).
 * </ul>
 *
 * <p>The attachments are {@value #RECORD_FILE}, every source row of the record with each stored
 * text value exactly (JSON escapes for what a file of text cannot hold) and the size and SHA-256
 * of each binary value, and one file per image the summary shows, as its own bytes. A credential
 * the summary withholds is withheld there too. A text-only or safe-mode render of a record
 * without an image still prints every character and is accepted as it is. Only an interrupted
 * wait is not answered this way: it is a cancellation. No outcome of a render holds the patient
 * (the hold {@code CDS_EXPORT_DATA_SUMMARY_RENDER_FAILED} is gone).
 *
 * @since 2026-11-13
 */
@Service
public class CdsExportDataSummaryPdfService {
    /** Why {@link #requirePdf} refuses bytes; a check of this exporter's own output, never a hold. */
    static final String FAILED = "CDS_EXPORT_DATA_SUMMARY_PDF_INVALID";

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
     * Shown in the HTML data summary and written in its Notes when no PDF could be made of the
     * record at all, not even a page holding a notice.
     */
    static final String HTML_NOTICE =
            "CARLOS EMR could not make this record's data summary into a PDF, so it is exported"
                    + " as HTML: this report's text is the data summary, which shows every stored"
                    + " value, and the exact record ("
                    + RECORD_FILE
                    + ") is exported as a separate text report beside this one, subclass \""
                    + CdsExportDataSummaryPdfService.RECORD_SUBCLASS
                    + "\".";

    /** SubClass of the text report that holds the exact record of an HTML data summary. */
    static final String RECORD_SUBCLASS = "CARLOS data summary record";

    /** Notes of the text report that holds the exact record of an HTML data summary. */
    static final String RECORD_NOTES =
            "CARLOS data summary record: CARLOS EMR could not make the data summary of this record"
                    + " into a PDF, so the summary is exported as HTML in the report beside this one"
                    + " and this report's text is the exact record ("
                    + RECORD_FILE
                    + "): every stored text value exactly, and the size and SHA-256 of each binary"
                    + " value, whose images the HTML summary shows.";

    /**
     * How many times the notice page is made, with the record attached, before the summary is
     * exported as HTML: the page once, and once more if that fails.
     */
    static final int NOTICE_ATTEMPTS = 2;

    /**
     * How many times a record with an image is rendered while the module reports that no render
     * slot came free, before its images are sent as attachments instead. It decides how the
     * images travel, never whether.
     */
    static final int SLOT_ATTEMPTS = 6;

    private static final String NOTICE_FONT = "net/sf/jasperreports/fonts/dejavu/DejaVuSans.ttf";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HtmlPdfRenderService renderer;
    private final Pages pages;
    private final CdsExportDataSummaryHtmlComposer composer = new CdsExportDataSummaryHtmlComposer();

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportDataSummaryPdfService(HtmlPdfRenderService renderer) {
        this(renderer, PDFBOX);
    }

    CdsExportDataSummaryPdfService(HtmlPdfRenderService renderer, Pages pages) {
        this.renderer = Objects.requireNonNull(renderer);
        this.pages = Objects.requireNonNull(pages);
    }

    /** The validated PDF of one data summary; see {@link #document}. */
    byte[] render(
            CdsExportDataSummaryPlanner.Group group, CdsExportDataSummaryHtmlComposer.Patient patient)
            throws IOException {
        return document(group, patient).pdf();
    }

    /**
     * The validated PDF of one data summary and, when the page cannot show all of the record,
     * the notice that says where the exact record is (also printed on the page); or, when no PDF
     * can be made of it at all, the summary as HTML and the exact record ({@link #html}). Never
     * holds the patient, whatever the render does.
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
        if (page != null) {
            byte[] pdf = attached(page, group);
            if (pdf != null)
                return new CdsExportDataSummaryOccurrenceService.Renderer.Rendered(pdf, ATTACHED_NOTICE);
        }
        // No PDF from the module, or its pages would not take the record: the notice page, made
        // once more when it fails.
        for (int attempt = 1; attempt <= NOTICE_ATTEMPTS; attempt++) {
            byte[] notice;
            try {
                notice = pages.notice();
            } catch (CancellationException cancelled) {
                throw cancelled;
            } catch (IOException | RuntimeException failed) {
                interrupted();
                continue;
            }
            byte[] pdf = attached(notice, group);
            if (pdf != null)
                return new CdsExportDataSummaryOccurrenceService.Renderer.Rendered(pdf, UNPRINTED_NOTICE);
        }
        return html(group, patient);
    }

    /**
     * The record's data summary when no PDF can be made of it: the composer's HTML, which shows
     * every value and says why on the page, and the exact record ({@value #RECORD_FILE}, without
     * image files: the HTML shows each image from its own bytes). The occurrence service writes
     * each as a Reports entry of Format Text.
     */
    static CdsExportDataSummaryOccurrenceService.Renderer.Rendered html(
            CdsExportDataSummaryPlanner.Group group, CdsExportDataSummaryHtmlComposer.Patient patient)
            throws IOException {
        var noticed =
                group.withNotice(
                        group.notice() == null ? HTML_NOTICE : group.notice() + " " + HTML_NOTICE);
        return CdsExportDataSummaryOccurrenceService.Renderer.Rendered.html(
                CdsExportXmlText.element(new CdsExportDataSummaryHtmlComposer().compose(noticed, patient)),
                record(group, null),
                HTML_NOTICE);
    }

    /**
     * The PDF with the exact record attached, checked loadable, or null when it could not be made
     * (the next rung is tried). A cancellation is never taken for a failed render.
     */
    private byte[] attached(byte[] page, CdsExportDataSummaryPlanner.Group group) {
        if (page == null) return null;
        try {
            byte[] pdf = pages.attach(page, group);
            requirePdf(pdf);
            return pdf;
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (IOException | RuntimeException failed) {
            interrupted();
            return null;
        }
    }

    /**
     * The two PDFBox steps after the module: the notice page and the attaching of the exact record.
     * A test supplies steps that fail, to force each rung of {@link #document}.
     */
    interface Pages {
        byte[] notice() throws IOException;

        byte[] attach(byte[] pdf, CdsExportDataSummaryPlanner.Group group) throws IOException;
    }

    /** The production steps. */
    static final Pages PDFBOX =
            new Pages() {
                @Override
                public byte[] notice() throws IOException {
                    return noticePage();
                }

                @Override
                public byte[] attach(byte[] pdf, CdsExportDataSummaryPlanner.Group group)
                        throws IOException {
                    return CdsExportDataSummaryPdfService.attach(pdf, group);
                }
            };

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
                    // The fonts are asked only about text that is not plain ASCII.
                    if (text != null
                            && !(drawn(text) && (plain(text) || renderer.drawsEveryCharacter(text))))
                        return false;
                }
            }
            return true;
        } catch (PDFGenerationException | RuntimeException unavailable) {
            return false;
        }
    }

    /** Printable ASCII, tab and the line breaks: every bundled face draws these. */
    private static boolean plain(String text) {
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (!(c >= 0x20 && c <= 0x7e || c == '\t' || c == '\n' || c == '\r')) return false;
        }
        return true;
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
            byte[] json =
                    record(
                                    group,
                                    (name, type, bytes) ->
                                            files.put(name, file(document, name, type, bytes)))
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

    /** Receives each image of the record as its own file. */
    @FunctionalInterface
    interface ImageFiles {
        void add(String name, String type, byte[] bytes) throws IOException;
    }

    /**
     * The exact record ({@code CARLOS.DataSummary.Record.v1}): the record's table and identifier,
     * its part numbers, and every source row with each stored text value exactly, SQL NULL and
     * empty kept apart, and the size and SHA-256 of each binary value (with its decoded text when
     * it is registered as Base64 text). A credential the summary withholds is withheld here too.
     *
     * @param images receives each image the summary shows as its own file, named in the record;
     *     null when no image file travels (the HTML summary shows each image from its bytes)
     */
    static String record(CdsExportDataSummaryPlanner.Group group, ImageFiles images)
            throws IOException {
        ObjectNode record = JSON.createObjectNode();
        record.put("carrier", "CARLOS.DataSummary.Record.v1");
        record.put("table", group.table());
        record.put("id", group.id());
        record.put("part", group.part());
        record.put("parts", group.parts());
        var rows = record.putArray("rows");
        int count = 0;
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
                if (CdsExportDataSummaryRegistry.withheld(row.table(), entry.getKey()) && !absent) {
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
                            CdsExportDataSummaryHtmlComposer.shownText(value.content(), presentation);
                    if (text != null) field.put("decodedText", text);
                    var image =
                            images == null
                                    ? null
                                    : CdsExportDataSummaryHtmlComposer.shownImage(
                                            value.content(), presentation);
                    if (image != null) {
                        String name =
                                "carlos-data-summary-image-"
                                        + ++count
                                        + switch (image.type()) {
                                            case "image/png" -> ".png";
                                            case "image/jpeg" -> ".jpg";
                                            default -> ".gif";
                                        };
                        field.put("file", name);
                        images.add(name, image.type(), image.bytes());
                    }
                }
            }
        }
        // Jackson escapes control characters; the rest of what a file of text cannot hold (a lone
        // surrogate, U+FFFE, U+FFFF) becomes a JSON escape here, so the text is valid UTF-8 and
        // XML and decodes to the exact stored text.
        return CdsExportResidualJson.text(JSON.writeValueAsString(record));
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

    static boolean isPdf(byte[] pdf) {
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

    /**
     * {@code %PDF-} header, loadable by PDFBox, at least one page. Every caller answers a refusal
     * with the next rung of {@link #document} (a printed form, with its reason), so it never
     * holds a patient.
     */
    static void requirePdf(byte[] pdf) throws IOException {
        if (pdf == null
                || pdf.length < 5
                || !Arrays.equals(
                        Arrays.copyOf(pdf, 5), "%PDF-".getBytes(StandardCharsets.US_ASCII)))
            throw new IOException(FAILED);
        int pages;
        try (var document = Loader.loadPDF(pdf)) {
            pages = document.getNumberOfPages();
        } catch (IOException unreadable) {
            throw new IOException(FAILED);
        }
        if (pages < 1) throw new IOException(FAILED);
    }
}
