/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SMART_NULLS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Prints a TRUENORTH report's HTML OBR comment through {@link LabPDFCreator} (issue #5046).
 *
 * <p>The creator cast the elements {@code HTMLWorker.parseToList} returns to {@code String}, so a
 * TRUENORTH report with an OBR comment failed with a ClassCastException instead of printing, and
 * its text was in OpenPDF's default, non-embedded Helvetica. The comment is synthetic.
 */
@Tag("unit")
@Tag("report")
class LabPDFCreatorObrCommentUnitTest {
    @Test
    @DisplayName("should print a TRUENORTH HTML comment's text in embedded fonts, in any script")
    void shouldPrintTrueNorthComment_withEmbeddedFonts() throws Exception {
        MessageHandler handler = mock(MessageHandler.class, RETURNS_SMART_NULLS);
        when(handler.getMsgType()).thenReturn("TRUENORTH");
        when(handler.getHeaders()).thenReturn(new ArrayList<>(List.of("CHEM")));
        when(handler.getOBRCount()).thenReturn(1);
        when(handler.getOBRName(0)).thenReturn("FAKE Chemistry");
        when(handler.getObservationHeader(0, 0)).thenReturn("CHEM");
        when(handler.getOBRCommentCount(0)).thenReturn(1);
        when(handler.getOBRComment(0, 0)).thenReturn("<p>FAKE-备注 &lt;5 mmol/L</p><p>さくら <b>repeat</b></p>");
        when(handler.getPatientName()).thenReturn("FAKE-陈, 小明");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LabPDFCreator creator = new LabPDFCreator();
        creator.setOs(output);
        creator.setHandler(handler);
        creator.printPdf();

        try (var pdf = Loader.loadPDF(output.toByteArray())) {
            for (var page : pdf.getPages()) {
                for (var name : page.getResources().getFontNames()) {
                    assertThat(page.getResources().getFont(name).isEmbedded()).as("font %s", name).isTrue();
                }
            }
            assertThat(new PDFTextStripper().getText(pdf).replaceAll("\\s+", " "))
                    .contains("FAKE-备注 <5 mmol/L", "さくら repeat", "FAKE-陈, 小明");
        }
    }
}
