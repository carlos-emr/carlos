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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto;

import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bounded media sanity only: no PDF/image object tree, conversion, arbitrary charset guess, or byte
 * rewriting.
 *
 * <p>The recognised report types are those CARLOS stores and its CDS export declares
 * ({@code CdsExportReportFileFormats}): PDF, plain text, JPEG, PNG, TIFF, GIF, BMP, Word
 * ({@code doc}, {@code docx}) and RTF. Each declaration is kept only when the file's first sixteen
 * bytes bear it out, as the exporter checks them. PDF, PNG, JPEG, TIFF, GIF and RTF headers also
 * name a format on their own; a BMP, OLE2 ({@code doc}) or ZIP ({@code docx}) header is common to
 * other formats or to text, so it only confirms a declaration.
 *
 * <p>HTML is recognised but never published as HTML. The document viewer
 * ({@code ManageDocument2Action.display}) renders a {@code text/html} document as a live page in
 * the CARLOS origin, which is how a clinician's own upload is shown. Bytes from another system
 * must not run script there, so an HTML file stays an opaque document requiring review
 * ({@code CDS_REPORT_HTML_RETAINED_OPAQUE}), served as {@code application/octet-stream}, and HTML
 * report text stays plain text. Any other type stays opaque and reviewed.
 */
@Service
public class CdsReportMediaService {
    private record Media(String extension, String mime) {}

    public record Result(
            String extension,
            String mime,
            boolean reviewRequired,
            List<CdsClinicalDiagnosticDto> diagnostics) {
        public Result {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private static final Media OPAQUE = new Media("bin", "application/octet-stream");
    private static final Media TEXT = new Media("txt", "text/plain;charset=UTF-8");
    private static final Media PDF = new Media("pdf", "application/pdf");
    private static final Media JPEG = new Media("jpg", "image/jpeg");
    private static final Media PNG = new Media("png", "image/png");
    private static final Media TIFF = new Media("tif", "image/tiff");
    private static final Media GIF = new Media("gif", "image/gif");
    private static final Media BMP = new Media("bmp", "image/bmp");
    private static final Media DOC = new Media("doc", "application/msword");
    private static final Media DOCX =
            new Media(
                    "docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final Media RTF = new Media("rtf", "application/rtf");

    /**
     * A recognised declaration that is never published as itself: HTML would render as a live page
     * in the CARLOS origin (see the class comment).
     */
    private static final Media HTML = new Media("html", "text/html");

    /** XML parser/corrected logical strings already establish this representation's UTF8 encoding. */
    public Result xmlText(long length) {
        return xmlText(length, null);
    }

    public Result xmlText(long length, String declaration) {
        List<CdsClinicalDiagnosticDto> diagnostics = new ArrayList<>(1);
        if (length == 0)
            notice(diagnostics, "CDS_REPORT_EMPTY_REPRESENTATION", "Content/TextContent");
        Media source = declared(declaration);
        boolean inconsistent =
                declaration != null
                        && !declaration.isBlank()
                        && (source == null || !source.extension().equals("txt"));
        // HTML text (CARLOS exports an Add HTML or Add Link document this way) is kept as the
        // plain text it is, never rendered: its markup is shown, not run.
        if (source == HTML)
            notice(diagnostics, "CDS_REPORT_HTML_RETAINED_AS_TEXT", "FileExtensionAndVersion");
        else if (inconsistent)
            notice(
                    diagnostics,
                    "CDS_REPORT_TEXT_MEDIA_DECLARATION_RETAINED",
                    "FileExtensionAndVersion");
        return result(TEXT, length == 0 || inconsistent, diagnostics);
    }

    public Result binaryUnderTextDeclaration(Result inspected) {
        List<CdsClinicalDiagnosticDto> diagnostics = new ArrayList<>(inspected.diagnostics());
        notice(diagnostics, "CDS_REPORT_BINARY_FORMAT_DECLARATION_RETAINED", "Format");
        return new Result(inspected.extension(), inspected.mime(), true, diagnostics);
    }

    public Result inspect(
            String declaration,
            long length,
            String sourcePath,
            CdsDocumentPublicationService.Source source)
            throws IOException {
        if (length < 0) throw new IllegalArgumentException("Invalid report representation length");
        List<CdsClinicalDiagnosticDto> diagnostics = new ArrayList<>(2);
        if (length == 0) notice(diagnostics, "CDS_REPORT_EMPTY_REPRESENTATION", sourcePath);
        Media declared = declared(declaration);
        try (InputStream input = new ExpectedLengthInputStream(source.open(), length)) {
            byte[] prefix = input.readNBytes(16);
            Media actual = signature(prefix);
            if (declared != null && declared.extension().equals("txt")) {
                if (actual != null) {
                    notice(diagnostics, "CDS_REPORT_MEDIA_DECLARATION_MISMATCH", sourcePath);
                    return result(OPAQUE, true, diagnostics);
                }
                try (var text =
                        new InputStreamReader(
                                new SequenceInputStream(new ByteArrayInputStream(prefix), input),
                                StandardCharsets.UTF_8
                                        .newDecoder()
                                        .onMalformedInput(CodingErrorAction.REPORT)
                                        .onUnmappableCharacter(CodingErrorAction.REPORT))) {
                    char[] buffer = new char[8192];
                    while (text.read(buffer) != -1) cancelled();
                } catch (CharacterCodingException unknownEncoding) {
                    if (unknownEncoding.getSuppressed().length != 0)
                        throw new IOException(
                                "REPORT_MEDIA_RESOURCE_CLOSE_FAILED", unknownEncoding);
                    notice(diagnostics, "CDS_REPORT_TEXT_ENCODING_REQUIRES_REVIEW", sourcePath);
                    return result(OPAQUE, true, diagnostics);
                }
                boolean specified = hasUtf8Bom(prefix) || explicitlyUtf8(declaration);
                if (!specified)
                    notice(diagnostics, "CDS_REPORT_TEXT_ENCODING_INFERRED_UTF8", sourcePath);
                return result(TEXT, length == 0 || !specified, diagnostics);
            }
            if (declared == HTML) {
                // Recognised, but published opaque: the viewer would run it in the CARLOS origin.
                notice(diagnostics, "CDS_REPORT_HTML_RETAINED_OPAQUE", sourcePath);
                return result(OPAQUE, true, diagnostics);
            }
            if (declared != null) {
                if (!matches(declared, prefix)) {
                    notice(diagnostics, "CDS_REPORT_MEDIA_DECLARATION_MISMATCH", sourcePath);
                    return result(OPAQUE, true, diagnostics);
                }
                return result(declared, length == 0, diagnostics);
            }
            if (actual != null) {
                notice(
                        diagnostics,
                        "CDS_REPORT_MEDIA_FROM_BOUNDED_HEADER",
                        "FileExtensionAndVersion");
                return result(actual, length == 0, diagnostics);
            }
            notice(
                    diagnostics,
                    "CDS_REPORT_MEDIA_UNRECOGNIZED_SOURCE_RETAINED",
                    "FileExtensionAndVersion");
            return result(OPAQUE, true, diagnostics);
        }
    }

    private static Result result(
            Media media, boolean review, List<CdsClinicalDiagnosticDto> diagnostics) {
        return new Result(media.extension(), media.mime(), review, diagnostics);
    }

    private static void notice(
            List<CdsClinicalDiagnosticDto> diagnostics, String code, String path) {
        diagnostics.add(new CdsClinicalDiagnosticDto(code, path));
    }

    private static Media declared(String source) {
        String value = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "pdf", ".pdf", "application/pdf" -> PDF;
            case "txt",
                    ".txt",
                    "text/plain",
                    "text/plain;charset=utf-8",
                    "text/plain; charset=utf-8" ->
                    TEXT;
            case "jpg", "jpeg", ".jpg", ".jpeg", "image/jpeg" -> JPEG;
            case "png", ".png", "image/png" -> PNG;
            case "tif", "tiff", ".tif", ".tiff", "image/tiff" -> TIFF;
            case "gif", ".gif", "image/gif" -> GIF;
            case "bmp", ".bmp", "image/bmp", "image/x-bmp", "image/x-ms-bmp" -> BMP;
            case "doc", ".doc", "application/msword" -> DOC;
            case "docx",
                    ".docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    DOCX;
            case "rtf", ".rtf", "application/rtf", "text/rtf" -> RTF;
            case "html", "htm", ".html", ".htm", "text/html" -> HTML;
            default -> value.startsWith("text/html;") ? HTML : null;
        };
    }

    /** The formats whose leading bytes name them on their own, in the order they are tried. */
    private static final List<Media> SIGNATURES = List.of(PDF, PNG, JPEG, TIFF, GIF, RTF);

    private static Media signature(byte[] bytes) {
        for (Media media : SIGNATURES) if (matches(media, bytes)) return media;
        return null;
    }

    /** Whether the bounded header is the declared binary format's own (the exporter's checks). */
    private static boolean matches(Media media, byte[] bytes) {
        if (media == PDF) {
            int start = hasUtf8Bom(bytes) ? 3 : 0;
            return bytes.length >= start + 9
                    && bytes[start] == '%'
                    && bytes[start + 1] == 'P'
                    && bytes[start + 2] == 'D'
                    && bytes[start + 3] == 'F'
                    && bytes[start + 4] == '-'
                    && bytes[start + 5] >= '0'
                    && bytes[start + 5] <= '9'
                    && bytes[start + 6] == '.'
                    && bytes[start + 7] >= '0'
                    && bytes[start + 7] <= '9'
                    && (bytes[start + 8] == '\r' || bytes[start + 8] == '\n');
        }
        if (media == PNG) return starts(bytes, 0, 137, 'P', 'N', 'G', 13, 10, 26, 10);
        if (media == JPEG) return starts(bytes, 0, 255, 216, 255);
        if (media == TIFF)
            return starts(bytes, 0, 'I', 'I', 42, 0)
                    || starts(bytes, 0, 'I', 'I', 43, 0)
                    || starts(bytes, 0, 'M', 'M', 0, 42)
                    || starts(bytes, 0, 'M', 'M', 0, 43);
        if (media == GIF)
            return starts(bytes, 0, 'G', 'I', 'F', '8', '7', 'a')
                    || starts(bytes, 0, 'G', 'I', 'F', '8', '9', 'a');
        if (media == RTF) return starts(bytes, 0, '{', '\\', 'r', 't', 'f');
        // "BM" alone is ordinary text; the bitmap header's reserved bytes must also be zero.
        if (media == BMP) return starts(bytes, 0, 'B', 'M') && starts(bytes, 6, 0, 0, 0, 0);
        if (media == DOC) return starts(bytes, 0, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
        if (media == DOCX) return starts(bytes, 0, 'P', 'K', 3, 4);
        return false;
    }

    private static boolean starts(byte[] bytes, int offset, int... expected) {
        if (bytes.length < offset + expected.length) return false;
        for (int i = 0; i < expected.length; i++)
            if (u(bytes[offset + i]) != expected[i]) return false;
        return true;
    }

    private static int u(byte value) {
        return value & 255;
    }

    private static boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3
                && u(bytes[0]) == 0xEF
                && u(bytes[1]) == 0xBB
                && u(bytes[2]) == 0xBF;
    }

    private static boolean explicitlyUtf8(String declaration) {
        String value = declaration == null ? "" : declaration.trim().toLowerCase(Locale.ROOT);
        return value.equals("text/plain;charset=utf-8")
                || value.equals("text/plain; charset=utf-8");
    }

    private static void cancelled() {
        if (Thread.currentThread().isInterrupted())
            throw new CdsImportException("IMPORT_CANCELLED");
    }

    private static final class ExpectedLengthInputStream extends FilterInputStream {
        private final long expected;
        private long count;

        ExpectedLengthInputStream(InputStream input, long expected) {
            super(input);
            this.expected = expected;
        }

        @Override
        public int read() throws IOException {
            cancelled();
            int value = in.read();
            checked(value == -1 ? -1 : 1);
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            cancelled();
            int read = in.read(bytes, offset, length);
            checked(read);
            return read;
        }

        private void checked(int read) {
            if (read == -1) {
                if (count != expected)
                    throw new CdsImportException("REPORT_REPRESENTATION_LENGTH_CHANGED");
            } else {
                count = Math.addExact(count, read);
                if (count > expected)
                    throw new CdsImportException("REPORT_REPRESENTATION_LENGTH_CHANGED");
            }
        }
    }
}
