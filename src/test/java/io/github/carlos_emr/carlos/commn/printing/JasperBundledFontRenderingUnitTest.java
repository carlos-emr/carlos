/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.printing;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JRTextElement;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.util.JRFontNotFoundException;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.pdf.JRPdfExporter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fills and exports report text through the real default JasperReports context and checks the
 * PDF, so the CARLOS report font policy (issue #5046) is verified end to end rather than only by
 * reading templates.
 *
 * <p>The boxes reproduce the year-end statement's layout: 12pt text in 16pt and 17pt boxes. Under
 * the old default a host whose sans-serif face is taller than that (Noto Sans, WenQuanYi Zen Hei,
 * Thai faces) fitted zero lines, and Jasper left the field out of a valid PDF. Run in a fresh JVM
 * by {@code scripts/check-report-fonts.sh} with such host fonts, this class shows the output no
 * longer depends on them.
 */
@Tag("unit")
@Tag("report")
class JasperBundledFontRenderingUnitTest {
    /** Synthetic text beyond Cp1252: Vietnamese, Polish, Greek, Cyrillic and clinical symbols. */
    private static final String TEXT = "FAKE-Nguyễn Łucja Ωμέγα Жанна ≥5 µg";
    private static final Path OUTPUT = Path.of(System.getProperty("report.font.output", "target/report-font-rendering"));
    private static final List<String> FONT_NAMES = List.of("", "SansSerif", "Serif", "Monospaced", "Dialog", "DialogInput");

    private static String template(List<String> fontNames) {
        StringBuilder fields = new StringBuilder();
        int y = 0;
        for (String fontName : fontNames) {
            for (int height : new int[]{16, 17}) {
                fields.append(String.format(
                        "<element kind=\"textField\" x=\"0\" y=\"%d\" width=\"360\" height=\"%d\" fontSize=\"12.0\"%s>"
                                + "<expression><![CDATA[$P{value}]]></expression></element>%n",
                        y, height, fontName.isEmpty() ? "" : " fontName=\"" + fontName + "\""));
                y += 24;
            }
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <jasperReport name="carlosFontPolicy" pageWidth="400" pageHeight="%d" columnWidth="400" leftMargin="0" \
                rightMargin="0" topMargin="0" bottomMargin="0" uuid="5046f0a1-0000-4000-8000-000000005046">
                	<parameter name="value" class="java.lang.String"/>
                	<title height="%d">
                %s	</title>
                </jasperReport>
                """.formatted(y + 10, y, fields);
    }

    private static JasperPrint fill(String jrxml) throws Exception {
        JasperReport report = JasperCompileManager.compileReport(
                new ByteArrayInputStream(jrxml.getBytes(StandardCharsets.UTF_8)));
        // Keep the original text and cut index so a silently dropped field is visible here.
        report.getPropertiesMap().setProperty(JRTextElement.PROPERTY_PRINT_KEEP_FULL_TEXT, "true");
        return JasperFillManager.fillReport(report, new java.util.HashMap<>(Map.of("value", TEXT)), new JREmptyDataSource());
    }

    private static byte[] export(JasperPrint print) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JRPdfExporter exporter = new JRPdfExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(bytes));
        exporter.exportReport();
        Files.createDirectories(OUTPUT);
        Files.write(OUTPUT.resolve("bundled-font-policy.pdf"), bytes.toByteArray());
        return bytes.toByteArray();
    }

    @Test
    @DisplayName("should measure and print unnamed and logical-font text with bundled faces, whatever the host provides")
    void shouldPrintEveryField_withBundledFontsWhateverTheHostFonts() throws Exception {
        JasperPrint print = fill(template(FONT_NAMES));
        List<JRPrintElement> elements = print.getPages().get(0).getElements();
        assertThat(elements).hasSize(FONT_NAMES.size() * 2);
        for (JRPrintElement element : elements) {
            JRPrintText text = (JRPrintText) element;
            assertThat(text.getTextHeight()).as("measured height of %s at y=%d", text.getFontName(), text.getY())
                    .isPositive();
            assertThat(text.getTextTruncateIndex()).as("cut index of %s at y=%d", text.getFontName(), text.getY())
                    .isNull();
        }

        try (PDDocument pdf = Loader.loadPDF(export(print))) {
            for (JRPrintElement element : elements) {
                PDFTextStripperByArea area = new PDFTextStripperByArea();
                area.addRegion("field", new Rectangle2D.Double(element.getX(), element.getY(),
                        element.getWidth(), element.getHeight()));
                area.extractRegions(pdf.getPage(0));
                assertThat(area.getTextForRegion("field").strip())
                        .as("printed text at y=%d (%s)", element.getY(), ((JRPrintText) element).getFontName())
                        .isEqualTo(TEXT);
            }
            var resources = pdf.getPage(0).getResources();
            for (COSName name : resources.getFontNames()) {
                PDFont font = resources.getFont(name);
                assertThat(font.getName()).as("PDF font").containsAnyOf("DejaVuSans", "DejaVuSerif", "LiberationMono");
                assertThat(font.isEmbedded()).as("%s embedded", font.getName()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("should refuse a template naming a face nobody provides, instead of substituting a host font")
    void shouldRefuseUnknownFont_insteadOfSubstitutingHostFont() {
        // Pinned in jasperreports.properties: a silent substitution would bring back host-dependent output.
        String unknownFace = template(List.of("CARLOS Missing Face 5046"));
        assertThatThrownBy(() -> fill(unknownFace)).isInstanceOf(JRFontNotFoundException.class);
    }
}
