/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.carlos.commn.printing.FontSettings;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Document;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;
import org.openpdf.text.pdf.events.PdfPageEventForwarder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("pdf")
class WrappedFooterStamperUnitTest {
    private static final String NOTICE = "LEGAL NOTICE: The information transmitted is intended only for the person "
            + "to whom it is addressed and may contain confidential, proprietary and privileged material. "
            + "Any unauthorized review, distribution or use of this information is prohibited. "
            + "If you receive this in error, contact the sender and destroy the message and every copy. ";

    @Test
    void shouldKeepFullNoticeAndBrandingInsideEveryPageWithoutOverlappingResults() throws Exception {
        Document document = new Document(PageSize.LETTER);
        String notice = NOTICE.repeat(3).trim();
        WrappedFooterStamper footer = new WrappedFooterStamper(document, FontSettings.HELVETICA_10PT,
                notice, "BRANDING: Example Clinic 2026-09-17");
        float bodyBottom = document.bottomMargin();
        assertThat(bodyBottom).isGreaterThan(36);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, output);
        PdfPageEventForwarder events = new PdfPageEventForwarder();
        events.addPageEvent(footer);
        PageNumberStamper numbers = new PageNumberStamper(10);
        numbers.applyFont(FontSettings.HELVETICA_10PT);
        events.addPageEvent(numbers);
        writer.setPageEvent(events);
        document.open();
        for (int n = 0; n < 100; n++) document.add(new Paragraph("RESULT " + n + ": normal laboratory result"));
        document.close();
        try (var pdf = Loader.loadPDF(output.toByteArray())) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                class Positions extends PDFTextStripper {
                    final List<TextPosition> glyphs = new ArrayList<>();
                    Positions() throws IOException { super(); }
                    @Override protected void processTextPosition(TextPosition position) {
                        glyphs.add(position);
                        super.processTextPosition(position);
                    }
                }
                Positions extractor = new Positions();
                extractor.setStartPage(page);
                extractor.setEndPage(page);
                extractor.setSortByPosition(true);
                String text = extractor.getText(pdf).replaceAll("\\s+", " ");
                assertThat(text).contains(notice.replaceAll("\\s+", " "));
                assertThat(text).contains("BRANDING: Example Clinic 2026-09-17");
                assertThat(text).contains("Page " + page + " of " + pdf.getNumberOfPages());
                for (TextPosition glyph : extractor.glyphs) {
                    assertThat(glyph.getXDirAdj()).isGreaterThanOrEqualTo(35.9f);
                    assertThat(glyph.getXDirAdj() + glyph.getWidthDirAdj()).isLessThanOrEqualTo(576.1f);
                    assertThat(glyph.getYDirAdj()).isLessThanOrEqualTo(774.1f);
                    // The footer starts below the body boundary; no glyph should
                    // occupy the reserved gap between the body and page number.
                    float distanceBelowBody = glyph.getYDirAdj() - (792 - bodyBottom);
                    assertThat(distanceBelowBody <= 0.1f || distanceBelowBody >= 9.9f).isTrue();
                }
            }
        }
    }

    @Test
    void shouldRejectFooterThatLeavesNoRoomForBody() {
        assertThatThrownBy(() -> new WrappedFooterStamper(new Document(PageSize.LETTER),
                FontSettings.HELVETICA_10PT, NOTICE.repeat(100), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no room");
    }
}
