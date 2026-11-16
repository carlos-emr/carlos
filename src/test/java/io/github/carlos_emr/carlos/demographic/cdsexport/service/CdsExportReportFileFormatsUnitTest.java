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

import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportReportFileFormats.extension;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** File names and content types as the document manager, scanners and fax intake store them. */
@Tag("unit")
@Tag("cds-export")
class CdsExportReportFileFormatsUnitTest {
    private static final byte[] PDF = ascii("%PDF-1.7\n%âã");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13};
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 16};
    private static final byte[] TIFF = {'I', 'I', 42, 0, 8, 0, 0, 0};
    private static final byte[] DOC = {
        (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1
    };
    private static final byte[] ZIP = {'P', 'K', 3, 4, 20, 0, 6, 0};
    private static final byte[] TEXT = ascii("Referral letter\r\nDear colleague");

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void shouldKeepTheStoredExtension_whenTheBytesBearItOut() {
        assertThat(extension("20260305101400consult-letter.pdf", "application/pdf", PDF))
                .isEqualTo("pdf");
        assertThat(extension("20260305101400Photo.JPEG", "image/jpeg", JPEG)).isEqualTo("jpeg");
        assertThat(extension("20260305101400scan.tiff", "image/tiff", TIFF)).isEqualTo("tiff");
        assertThat(extension("20260305101400letter.doc", "application/msword", DOC))
                .isEqualTo("doc");
        assertThat(extension("20260305101400letter.docx", "", ZIP)).isEqualTo("docx");
        assertThat(extension("20260305101400note.txt", "text/plain", TEXT)).isEqualTo("txt");
    }

    @ParameterizedTest
    @CsvSource({
        "20260305101600scan0001, application/pdf",
        "20260305101600scan0001, application/pdf; name=scan0001",
        "20260305101600fax.2026, APPLICATION/PDF"
    })
    void shouldUseTheStoredContentType_whenTheFileNameHasNoRecognisedExtension(
            String filename, String contentType) {
        assertThat(extension(filename, contentType, PDF)).isEqualTo("pdf");
    }

    @Test
    void shouldDeclareWhatTheBytesAre_whenTheStoredNameOrTypeClaimsAnotherFormat() {
        assertThat(extension("20260305101400scan.pdf", "application/pdf", PNG)).isEqualTo("png");
        assertThat(extension("20260305101400note.txt", "text/plain", PDF)).isEqualTo("pdf");
        // The stored name is wrong but the stored content type is right.
        assertThat(extension("20260305101400letter.pdf", "application/msword", DOC))
                .isEqualTo("doc");
    }

    @Test
    void shouldDeclareAnOpaqueFile_whenNothingStoredIsBorneOutByTheBytes() {
        // A ".pdf" whose bytes are text (an error page saved under the name) is not a PDF.
        assertThat(extension("20260305101400report.pdf", "application/pdf", TEXT))
                .isEqualTo(CdsExportReportFileFormats.OPAQUE);
        byte[] heic = {0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'};
        assertThat(extension("20260305101700photo.heic", "image/heic", heic))
                .isEqualTo(CdsExportReportFileFormats.OPAQUE);
        assertThat(extension("20260305101600scan0001", null, TEXT))
                .isEqualTo(CdsExportReportFileFormats.OPAQUE);
        // "BM" alone is ordinary text, not a bitmap header.
        assertThat(extension("20260305101400image.bmp", "image/bmp", ascii("BMI was 31.2 kg/m2")))
                .isEqualTo(CdsExportReportFileFormats.OPAQUE);
        // A non-ASCII extension is not a recognised declaration.
        assertThat(extension("20260305101400rapport.pdé", "", PDF)).isEqualTo("pdf");
    }

    @Test
    void shouldNameRecognisableBytes_whenNothingWasStoredAboutTheFormat() {
        assertThat(extension("20260305101600scan0001", "", TIFF)).isEqualTo("tif");
        assertThat(extension("20260305101600scan0001", "application/octet-stream", JPEG))
                .isEqualTo("jpg");
        assertThat(extension("20260305101600scan0001", "", new byte[0]))
                .isEqualTo(CdsExportReportFileFormats.OPAQUE);
    }
}
