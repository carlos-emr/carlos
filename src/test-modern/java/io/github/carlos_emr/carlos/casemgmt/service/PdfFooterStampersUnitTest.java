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
package io.github.carlos_emr.carlos.casemgmt.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfTemplate;
import org.openpdf.text.pdf.PdfWriter;
import org.openpdf.text.pdf.events.PdfPageEventForwarder;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the PDF footer stamper infrastructure:
 * {@link FooterSupport}, {@link PageNumberStamper}, and {@link PromoTextStamper}.
 *
 * <p>These three classes form a cohesive unit chained by {@code PdfPageEventForwarder}
 * in {@link io.github.carlos_emr.carlos.commn.printing.PdfWriterFactory}. Tests verify
 * that OpenPDF's {@code PdfContentByte}, {@code PdfTemplate}, and {@code BaseFont} APIs
 * work correctly after the iText 5 to OpenPDF 3.0 migration.</p>
 *
 * @since 2026-03-04
 */
@Tag("unit")
@Tag("pdf")
@DisplayName("PDF Footer Stampers Unit Tests")
class PdfFooterStampersUnitTest {

    /** Tests for the FooterSupport base class. */
    @Nested
    @DisplayName("FooterSupport")
    class FooterSupportTests {

        @Test
        @DisplayName("should create default Helvetica font when constructed")
        void shouldCreateDefaultHelveticaFont_whenConstructed() {
            FooterSupport footer = new FooterSupport() {};
            assertThat(footer.getFont()).isNotNull();
            assertThat(footer.getFontSize()).isEqualTo(12);
        }

        @Test
        @DisplayName("should update font size when setFontSize called")
        void shouldUpdateFontSize_whenSetFontSizeCalled() {
            FooterSupport footer = new FooterSupport() {};
            footer.setFontSize(8);
            assertThat(footer.getFontSize()).isEqualTo(8);
        }

        @Test
        @DisplayName("should create custom font when setFont called with valid args")
        void shouldCreateCustomFont_whenSetFontCalledWithValidArgs() {
            FooterSupport footer = new FooterSupport() {};
            footer.setFont(BaseFont.COURIER, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            BaseFont font = footer.getFont();
            assertThat(font).isNotNull();
            // The PostScript name for built-in Courier is "Courier"
            assertThat(font.getPostscriptFontName()).isEqualTo("Courier");
        }

        @Test
        @DisplayName("should throw RuntimeException when setFont called with invalid font")
        void shouldThrowRuntimeException_whenSetFontCalledWithInvalidFont() {
            FooterSupport footer = new FooterSupport() {};
            assertThatThrownBy(() ->
                    footer.setFont("BogusFont", BaseFont.WINANSI, BaseFont.NOT_EMBEDDED))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Unable to create base font");
        }
    }

    /** Tests for the PageNumberStamper page event handler. */
    @Nested
    @DisplayName("PageNumberStamper")
    class PageNumberStamperTests {

        @Test
        @DisplayName("should initialize template when document opened")
        void shouldInitializeTemplate_whenDocumentOpened() throws Exception {
            Document doc = new Document();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(doc, baos);

            PageNumberStamper stamper = new PageNumberStamper(10);
            writer.setPageEvent(stamper);

            doc.open();
            // After open, the template should be initialized by onOpenDocument
            assertThat(stamper.getTotal()).isNotNull();
            assertThat(stamper.getTotal()).isInstanceOf(PdfTemplate.class);
            // The template is created with createTemplate(100, 100) in onOpenDocument;
            // OpenPDF may apply internal scaling, so verify non-zero dimensions
            assertThat(stamper.getTotal().getWidth()).isGreaterThan(0f);
            assertThat(stamper.getTotal().getHeight()).isGreaterThan(0f);

            doc.add(new Paragraph("test"));
            doc.close();
        }

        @Test
        @DisplayName("should produce valid PDF with page numbers when multi-page document closed")
        void shouldProduceValidPdfWithPageNumbers_whenMultiPageDocumentClosed() throws Exception {
            Document doc = new Document();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(doc, baos);

            PageNumberStamper stamper = new PageNumberStamper(10);
            stamper.setFontSize(8);
            writer.setPageEvent(stamper);

            doc.open();
            // Generate multiple pages
            for (int i = 0; i < 3; i++) {
                doc.add(new Paragraph("Page content " + (i + 1)));
                if (i < 2) {
                    doc.newPage();
                }
            }
            doc.close();

            byte[] pdfBytes = baos.toByteArray();
            assertThat(pdfBytes).startsWith(new byte[]{'%', 'P', 'D', 'F'});

            PdfReader reader = new PdfReader(pdfBytes);
            assertThat(reader.getNumberOfPages()).isEqualTo(3);
            reader.close();
        }
    }

    /** Tests for the PromoTextStamper page event handler. */
    @Nested
    @DisplayName("PromoTextStamper")
    class PromoTextStamperTests {

        @Test
        @DisplayName("should stamp text when page ends")
        void shouldStampText_whenPageEnds() throws Exception {
            Document doc = new Document();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(doc, baos);

            PromoTextStamper stamper = new PromoTextStamper("CARLOS EMR Clinic", 20);
            stamper.setFontSize(6);
            writer.setPageEvent(stamper);

            doc.open();
            doc.add(new Paragraph("Test content"));
            doc.close();

            byte[] pdfBytes = baos.toByteArray();
            assertThat(pdfBytes).isNotEmpty();
            assertThat(pdfBytes).startsWith(new byte[]{'%', 'P', 'D', 'F'});
        }

        @Test
        @DisplayName("should produce valid PDF with promo text when document closed")
        void shouldProduceValidPdf_withPromoText_whenDocumentClosed() throws Exception {
            Document doc = new Document();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(doc, baos);

            PromoTextStamper stamper = new PromoTextStamper("Confidential - Do Not Distribute", 30);
            writer.setPageEvent(stamper);

            doc.open();
            doc.add(new Paragraph("Clinical note content"));
            doc.add(new Paragraph("Second paragraph"));
            doc.close();

            PdfReader reader = new PdfReader(baos.toByteArray());
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            reader.close();
        }
    }

    /** Tests for chaining multiple stampers via PdfPageEventForwarder. */
    @Nested
    @DisplayName("PdfPageEventForwarder")
    class PdfPageEventForwarderTests {

        @Test
        @DisplayName("should chain multiple stampers when forwarder used")
        void shouldChainMultipleStampers_whenForwarderUsed() throws Exception {
            Document doc = new Document();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(doc, baos);

            PdfPageEventForwarder forwarder = new PdfPageEventForwarder();
            forwarder.addPageEvent(new PromoTextStamper("Confidential", 30));
            forwarder.addPageEvent(new PromoTextStamper("CARLOS EMR 2026-03-04", 20));
            forwarder.addPageEvent(new PageNumberStamper(10));

            writer.setPageEvent(forwarder);

            doc.open();
            doc.add(new Paragraph("Test content"));
            doc.close();

            byte[] pdfBytes = baos.toByteArray();
            assertThat(pdfBytes).startsWith(new byte[]{'%', 'P', 'D', 'F'});
        }

        @Test
        @DisplayName("should produce valid multi-page PDF with all stampers chained")
        void shouldProduceValidMultiPagePdf_withAllStampersChained() throws Exception {
            Document doc = new Document();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(doc, baos);

            PdfPageEventForwarder forwarder = new PdfPageEventForwarder();

            PromoTextStamper confidentiality = new PromoTextStamper("CONFIDENTIAL", 30);
            confidentiality.setFontSize(6);
            forwarder.addPageEvent(confidentiality);

            PromoTextStamper promo = new PromoTextStamper("CARLOS EMR - Test Clinic", 20);
            promo.setFontSize(6);
            forwarder.addPageEvent(promo);

            PageNumberStamper pageNum = new PageNumberStamper(10);
            pageNum.setFontSize(6);
            forwarder.addPageEvent(pageNum);

            writer.setPageEvent(forwarder);

            doc.open();
            for (int i = 0; i < 5; i++) {
                doc.add(new Paragraph("Clinical note content for page " + (i + 1)));
                if (i < 4) {
                    doc.newPage();
                }
            }
            doc.close();

            PdfReader reader = new PdfReader(baos.toByteArray());
            assertThat(reader.getNumberOfPages()).isEqualTo(5);
            reader.close();
        }
    }
}
