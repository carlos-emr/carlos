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

/** Bounded media sanity only: no PDF/image object tree, conversion, arbitrary charset guess, or byte rewriting. */
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

    /** XML parser/corrected logical strings already establish this representation's UTF8 encoding. */
    public Result xmlText(long length) {
        List<CdsClinicalDiagnosticDto> diagnostics = new ArrayList<>(1);
        if (length == 0)
            notice(diagnostics, "CDS_REPORT_EMPTY_REPRESENTATION", "Content/TextContent");
        return result(TEXT, length == 0, diagnostics);
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
                return result(TEXT, length == 0, diagnostics);
            }
            if (declared != null) {
                if (!declared.equals(actual)) {
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
            case "pdf", ".pdf", "application/pdf" -> new Media("pdf", "application/pdf");
            case "txt", ".txt", "text/plain" -> TEXT;
            case "jpg", "jpeg", ".jpg", ".jpeg", "image/jpeg" -> new Media("jpg", "image/jpeg");
            case "png", ".png", "image/png" -> new Media("png", "image/png");
            case "tif", "tiff", ".tif", ".tiff", "image/tiff" -> new Media("tif", "image/tiff");
            default -> null;
        };
    }

    private static Media signature(byte[] bytes) {
        int start =
                bytes.length >= 3
                                && u(bytes[0]) == 0xEF
                                && u(bytes[1]) == 0xBB
                                && u(bytes[2]) == 0xBF
                        ? 3
                        : 0;
        if (bytes.length >= start + 9
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
                && (bytes[start + 8] == '\r' || bytes[start + 8] == '\n'))
            return new Media("pdf", "application/pdf");
        if (bytes.length >= 8
                && u(bytes[0]) == 137
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G'
                && bytes[4] == 13
                && bytes[5] == 10
                && bytes[6] == 26
                && bytes[7] == 10) return new Media("png", "image/png");
        if (bytes.length >= 3 && u(bytes[0]) == 255 && u(bytes[1]) == 216 && u(bytes[2]) == 255)
            return new Media("jpg", "image/jpeg");
        if (bytes.length >= 4
                && ((bytes[0] == 'I'
                                && bytes[1] == 'I'
                                && (bytes[2] == 42 || bytes[2] == 43)
                                && bytes[3] == 0)
                        || (bytes[0] == 'M'
                                && bytes[1] == 'M'
                                && bytes[2] == 0
                                && (bytes[3] == 42 || bytes[3] == 43))))
            return new Media("tif", "image/tiff");
        return null;
    }

    private static int u(byte value) {
        return value & 255;
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
