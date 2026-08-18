/**
 * Copyright (c) 2024-2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.printing;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Document;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfPageEvent;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfWriter;
import org.openpdf.text.pdf.events.PdfPageEventForwarder;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PdfWriterFactory} — the central factory for PDF writers
 * in CARLOS EMR.
 *
 * <p>Extends {@link CarlosUnitTestBase} because {@code PdfWriterFactory} has static
 * fields initialized from {@code CarlosProperties} at class load time, which calls
 * {@code SpringUtils.getBean()}. The mocked SpringUtils ensures class loading succeeds
 * in the test environment.</p>
 *
 * <p>When CarlosProperties is not configured (test env), the static fields
 * {@code confidentialityStatement} and {@code promoText} will be null, exercising
 * the null-check branches in {@link PdfWriterFactory#newInstance}.</p>
 *
 * @since 2026-03-04
 */
@Tag("unit")
@Tag("pdf")
@DisplayName("PdfWriterFactory Unit Tests")
class PdfWriterFactoryUnitTest extends CarlosUnitTestBase {

    /** Tests for the newInstance factory method. */
    @Nested
    @DisplayName("newInstance")
    class NewInstanceTests {

        @Test
        @DisplayName("should return PdfWriter when document and stream provided")
        void shouldReturnPdfWriter_whenDocumentAndStreamProvided() throws Exception {
            Document doc = new Document();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            PdfWriter writer = PdfWriterFactory.newInstance(doc, baos, FontSettings.HELVETICA_10PT);

            assertThat(writer).isInstanceOf(PdfWriter.class);
            assertThat(writer.getPageEvent()).isNotNull();
            doc.open();
            doc.add(new Paragraph("test"));
            doc.close();
            byte[] pdfBytes = baos.toByteArray();
            assertThat(pdfBytes).startsWith(new byte[]{'%', 'P', 'D', 'F'});
        }

        @Test
        @DisplayName("should attach page event forwarder to writer")
        void shouldAttachPageEventForwarder_toWriter() throws Exception {
            Document doc = new Document();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            PdfWriter writer = PdfWriterFactory.newInstance(doc, baos, FontSettings.HELVETICA_10PT);

            assertThat(writer.getPageEvent()).isInstanceOf(PdfPageEventForwarder.class);
            doc.open();
            doc.add(new Paragraph("test"));
            doc.close();
        }

        @Test
        @DisplayName("should throw NullPointerException when document is null")
        void shouldThrowNpe_whenDocumentIsNull() {
            assertThatThrownBy(() -> PdfWriterFactory.newInstance(null, new ByteArrayOutputStream(), FontSettings.HELVETICA_10PT))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("document");
        }

        @Test
        @DisplayName("should throw NullPointerException when stream is null")
        void shouldThrowNpe_whenStreamIsNull() {
            assertThatThrownBy(() -> PdfWriterFactory.newInstance(new Document(), null, FontSettings.HELVETICA_10PT))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("stream");
        }

        @Test
        @DisplayName("should throw NullPointerException when settings is null")
        void shouldThrowNpe_whenSettingsIsNull() {
            assertThatThrownBy(() -> PdfWriterFactory.newInstance(new Document(), new ByteArrayOutputStream(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("settings");
        }

        @Test
        @DisplayName("should produce valid PDF when document opened and closed")
        void shouldProduceValidPdf_whenDocumentOpenedAndClosed() throws Exception {
            Document doc = new Document();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            PdfWriterFactory.newInstance(doc, baos, FontSettings.HELVETICA_10PT);
            doc.open();
            doc.add(new Paragraph("Clinical note content"));
            doc.newPage();
            doc.add(new Paragraph("Second page content"));
            doc.close();

            byte[] pdfBytes = baos.toByteArray();
            assertThat(pdfBytes).startsWith(new byte[]{'%', 'P', 'D', 'F'});

            PdfReader reader = new PdfReader(pdfBytes);
            assertThat(reader.getNumberOfPages()).isEqualTo(2);
            reader.close();
        }

        @Test
        @DisplayName("should allow additional page events to be chained via PdfPageEventForwarder")
        void shouldAllowAdditionalPageEvents_toBeChainedViaForwarder() throws Exception {
            Document doc = new Document();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriterFactory.newInstance(doc, baos, FontSettings.HELVETICA_10PT);

            // Simulate the chaining pattern used by LabPDFCreator
            PdfPageEvent existingEvent = writer.getPageEvent();
            assertThat(existingEvent).isInstanceOf(PdfPageEventForwarder.class);

            PdfPageEventForwarder forwarder = (PdfPageEventForwarder) existingEvent;
            // Add a custom event handler (like LabPDFCreator does with itself)
            PdfPageEventForwarder additionalHandler = new PdfPageEventForwarder();
            forwarder.addPageEvent(additionalHandler);

            // Verify the document still produces valid output with chained events
            doc.setPageSize(PageSize.LETTER);
            doc.open();
            doc.add(new Paragraph("Page 1 with chained events"));
            doc.newPage();
            doc.add(new Paragraph("Page 2 with chained events"));
            doc.close();

            byte[] pdfBytes = baos.toByteArray();
            assertThat(pdfBytes).startsWith(new byte[]{'%', 'P', 'D', 'F'});
            PdfReader reader = new PdfReader(pdfBytes);
            assertThat(reader.getNumberOfPages()).isEqualTo(2);
            reader.close();
        }
    }
}
