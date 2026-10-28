/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form.pdfservlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Prints a form through {@link FrmPDFServlet#generatePDFDocumentBytes} with a synthetic print
 * configuration over a shipped template, and checks each kind of overlay keeps text in any script
 * (issue #5046).
 *
 * <p>Fields, static {@code __} text and "fit" fields were drawn with
 * {@code PdfContentByte.showTextAligned} in one base font, and multi-line fields as a DejaVu-only
 * Phrase, so Chinese, Japanese and Korean characters were left out. "Fit" sized the font with
 * {@code BaseFont.getWidthPoint}, which counts those characters as zero width. Values are synthetic.
 */
@Tag("unit")
@Tag("report")
class FrmPDFServletUnicodeUnitTest {
    private static final float FIT_RIGHT_EDGE = 150f;

    private static final class ConfiguredServlet extends FrmPDFServlet {
        private final Properties config = new Properties();

        @Override
        protected Properties getCfgProp(String cfgFilename) {
            return config;
        }

        byte[] render(HttpServletRequest request) throws Exception {
            return generatePDFDocumentBytes(request, null, new ByteArrayOutputStream(), 0).toByteArray();
        }
    }

    @Test
    @DisplayName("should keep CJK text in fields, static text, fit fields and boxes, and check marks as symbols")
    void shouldPrintEveryOverlayKind_inChineseJapaneseAndKorean() throws Exception {
        ConfiguredServlet servlet = new ConfiguredServlet();
        servlet.config.setProperty("patientName", "left, 50, 100, 0, BaseFont.HELVETICA, 12");
        servlet.config.setProperty("__clinic", "left, 50, 130, 0, BaseFont.HELVETICA, 10, FAKE-诊所 Łukasz");
        servlet.config.setProperty("physician", "left, 50, 160, 0, BaseFont.HELVETICA, 12, fit, " + (int) FIT_RIGHT_EDGE);
        servlet.config.setProperty("notes", "left, 50, 200, 0, BaseFont.HELVETICA, 10, _, 400, 260, 12");
        servlet.config.setProperty("consent", "left, 300, 100, 0, BaseFont.ZAPFDINGBATS, 12, \u2714");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("__template", "CounselingNoteForm");
        request.addParameter("__cfgfile", "synthetic5046");
        request.addParameter("demographic_no", "1");
        request.addParameter("patientName", "FAKE-陈 小明");
        request.addParameter("physician", "FAKE-김민준 Łukasz Nguyễn");
        request.addParameter("notes", "FAKE-さくら follow-up ≥5");
        request.addParameter("consent", "X");
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        Provider provider = new Provider();
        provider.setLastName("FAKE");
        provider.setFirstName("Printer");
        when(loggedInInfo.getLoggedInProvider()).thenReturn(provider);

        byte[] bytes;
        try (MockedStatic<LoggedInInfo> session = mockStatic(LoggedInInfo.class)) {
            session.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(loggedInInfo);
            bytes = servlet.render(request);
        }

        try (var pdf = Loader.loadPDF(bytes)) {
            List<TextPosition> physician = new ArrayList<>();
            String text = new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition position) {
                    // The fit field's baseline is 160pt below the top of the page.
                    if (Math.abs(position.getYDirAdj() - 160f) < 1f) {
                        physician.add(position);
                    }
                    super.processTextPosition(position);
                }
            }.getText(pdf);
            assertThat(text).contains("FAKE-陈 小明", "FAKE-诊所 Łukasz", "FAKE-김민준 Łukasz Nguyễn",
                    "FAKE-さくら follow-up ≥5", "✔");
            assertThat(physician).isNotEmpty();
            for (TextPosition glyph : physician) {
                assertThat(glyph.getXDirAdj() + glyph.getWidthDirAdj()).as("glyph %s inside the fit blank", glyph.getUnicode())
                        .isLessThanOrEqualTo(FIT_RIGHT_EDGE + 0.5f);
            }
            for (var name : pdf.getPage(0).getResources().getFontNames()) {
                var font = pdf.getPage(0).getResources().getFont(name);
                // ZapfDingbats is one of the 14 standard PDF fonts and keeps its check-mark glyphs.
                assertThat(font.isEmbedded() || font.getName().contains("ZapfDingbats")).as("%s embedded", font.getName()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("should size a fit field by the width it prints, including fallback characters")
    void shouldMeasureFallbackCharacters_whenFittingField() throws IOException, org.openpdf.text.DocumentException {
        var font = io.github.carlos_emr.carlos.commn.printing.PdfFonts.createFont(
                org.openpdf.text.pdf.BaseFont.HELVETICA, org.openpdf.text.pdf.BaseFont.CP1252, false);
        // Ten Hangul characters DejaVu lacks: getWidthPoint called them zero wide, so they "fit" anything.
        assertThat(FrmPDFServlet.fittedFontSize(font, "김민준김민준김민준김", 12f, 60f)).isLessThan(12f);
    }
}
