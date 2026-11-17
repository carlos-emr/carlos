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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RenderedPdfValidator}.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("RenderedPdfValidator Unit Tests")
class RenderedPdfValidatorUnitTest {

    private final RenderedPdfValidator validator = new RenderedPdfValidator();

    private static byte[] pdfWith(PDRectangle... pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (PDRectangle size : pages) {
                document.addPage(new PDPage(size));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("should accept a PDF of Letter pages")
    void shouldAcceptValidPdf_ofLetterPages() throws IOException {
        RenderedPdfValidator.Result result = validator.validate(pdfWith(PDRectangle.LETTER, PDRectangle.LETTER));

        assertThat(result.valid()).isTrue();
        assertThat(result.messages()).isEmpty();
    }

    @Test
    @DisplayName("should reject bytes that are not a PDF, empty bytes and null")
    void shouldRejectNotAPdf_inValidator() {
        for (byte[] bytes : new byte[][]{"<html>error page</html>".getBytes(StandardCharsets.UTF_8), new byte[0], null,
                "%PD".getBytes(StandardCharsets.UTF_8)}) {
            RenderedPdfValidator.Result result = validator.validate(bytes);

            assertThat(result.valid()).isFalse();
            assertThat(result.messages()).singleElement().asString().contains("not a PDF");
        }
    }

    @Test
    @DisplayName("should reject a PDF that PDFBox cannot read")
    void shouldRejectUnreadablePdf_inValidator() {
        RenderedPdfValidator.Result result = validator.validate("%PDF-1.4\nthis is not really a document".getBytes(StandardCharsets.UTF_8));

        assertThat(result.valid()).isFalse();
        assertThat(result.messages()).singleElement().asString().contains("cannot be read");
    }

    @Test
    @DisplayName("should reject a PDF with no pages")
    void shouldRejectZeroPages_inValidator() throws IOException {
        RenderedPdfValidator.Result result = validator.validate(pdfWith());

        assertThat(result.valid()).isFalse();
        assertThat(result.messages()).containsExactly("The PDF has no pages.");
    }

    @Test
    @DisplayName("should reject a page that is not US Letter and name it")
    void shouldRejectNonLetterPage_inValidator() throws IOException {
        RenderedPdfValidator.Result result = validator.validate(pdfWith(PDRectangle.LETTER, PDRectangle.A4));

        assertThat(result.valid()).isFalse();
        assertThat(result.messages()).singleElement().asString().startsWith("Page 2 ").contains("not US Letter");
    }

    @Test
    @DisplayName("should name only the first few wrong pages")
    void shouldLimitPageMessages_whenManyPagesAreWrong() throws IOException {
        PDRectangle[] pages = new PDRectangle[12];
        java.util.Arrays.fill(pages, PDRectangle.A4);

        RenderedPdfValidator.Result result = validator.validate(pdfWith(pages));

        assertThat(result.valid()).isFalse();
        assertThat(result.messages()).hasSize(5);
    }

    @Test
    @DisplayName("should accept a page a hundredth of a point off Letter and reject one a tenth off")
    void shouldAllowTinyTolerance_forPageSize() throws IOException {
        assertThat(validator.validate(pdfWith(new PDRectangle(612.005f, 791.995f))).valid()).isTrue();
        assertThat(validator.validate(pdfWith(new PDRectangle(612.1f, 792f))).valid()).isFalse();
    }

    @Test
    @DisplayName("should reject a PDF that contains an AcroForm")
    void shouldRejectAcroForm_inValidator() throws IOException {
        byte[] pdf;
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.LETTER));
            document.getDocumentCatalog().setAcroForm(new PDAcroForm(document));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            pdf = out.toByteArray();
        }

        RenderedPdfValidator.Result result = validator.validate(pdf);

        assertThat(result.valid()).isFalse();
        assertThat(result.messages()).containsExactly("The PDF contains an AcroForm.");
    }

    @Test
    @DisplayName("should report every failed check at once")
    void shouldReportAllFailures_inOneResult() throws IOException {
        byte[] pdf;
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            document.getDocumentCatalog().setAcroForm(new PDAcroForm(document));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            pdf = out.toByteArray();
        }

        assertThat(validator.validate(pdf).messages()).hasSize(2);
    }

    @Test
    @DisplayName("should keep its result immutable")
    void shouldCopyMessages_inResult() {
        java.util.List<String> messages = new java.util.ArrayList<>(java.util.List.of("one"));
        RenderedPdfValidator.Result result = new RenderedPdfValidator.Result(messages, false);
        messages.add("two");

        assertThat(result.messages()).containsExactly("one");
    }
}
