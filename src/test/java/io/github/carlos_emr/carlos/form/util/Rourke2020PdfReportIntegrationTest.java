/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form.util;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import net.sf.jasperreports.engine.JRElement;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRTextField;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.export.OutputStreamExporterOutput;
import net.sf.jasperreports.pdf.JRPdfExporter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockConstruction;
import io.github.carlos_emr.carlos.form.model.FormRourke2020;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

@Tag("integration")
@Tag("report")
class Rourke2020PdfReportIntegrationTest extends CarlosTestBase {
    private static final AtomicInteger PDF_SEQUENCE = new AtomicInteger();
    private static final Path OUTPUT = Path.of(System.getProperty("rourke.pdf.output", "target/rourke-pdf"));

    private static Properties recordProperties(String patient) {
        Properties values = new Properties();
        values.setProperty("provider_no", "999998");
        values.setProperty("demographic_no", "1");
        values.setProperty("formCreated", "14/10/2026");
        values.setProperty("c_pName", patient);
        values.setProperty("c_birthDate", "01/01/2026");
        values.setProperty("c_male", "x");
        values.setProperty("p1_date1w", "08/01/2026");
        Properties result = new FormRourke2020(values).toProperties();
        result.setProperty("c_gestationalAge", "40 weeks");
        return result;
    }

    private static byte[] print(Properties values, List<Integer> pages) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Rourke2020PdfReport.write(output, values, pages);
        // Save before assertions, including for failed renders/extraction checks.
        Files.createDirectories(OUTPUT);
        Files.write(OUTPUT.resolve("generated-" + PDF_SEQUENCE.incrementAndGet() + ".pdf"), output.toByteArray());
        return output.toByteArray();
    }

    @Test
    void shouldRenderIdentifiersDatesAndMeasurements_onEveryMainPage() throws Exception {
        Properties values = recordProperties("FAKE-Łucja-Élodie");
        Properties blank = recordProperties("");
        blank.setProperty("c_birthDate", "");
        blank.setProperty("p1_date1w", "");
        blank.setProperty("c_male", "");
        blank.setProperty("c_gestationalAge", "");
        String[][] visits = {{"1w", "2w", "1m"}, {"2m", "4m", "6m"},
                {"9m", "12m", "15m"}, {"18m", "24m", "48m"}};
        for (int page = 1; page <= 4; page++) {
            for (String visit : visits[page - 1]) {
                String prefix = "p" + page + "_";
                values.setProperty(prefix + "date" + visit, "08/01/2026");
                values.setProperty(prefix + "ht" + visit, "51.2");
                values.setProperty(prefix + "wt" + visit, "3.5");
                values.setProperty(prefix + "pConcern" + visit, "≥ ≤ ° Δ Na⁺");
                if (!visit.equals("48m")) values.setProperty(prefix + "hc" + visit, "35.0");
            }
        }
        byte[] bytes = print(values, List.of(1, 2, 3, 4));
        Files.write(OUTPUT.resolve("all-fields.pdf"), bytes);
        try (PDDocument pdf = Loader.loadPDF(bytes);
             PDDocument empty = Loader.loadPDF(print(blank, List.of(1, 2, 3, 4)))) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            PDFRenderer blankRenderer = new PDFRenderer(empty);
            for (int page = 1; page <= 4; page++) {
                BufferedImage rendered = renderer.renderImageWithDPI(page - 1, 36);
                BufferedImage background = blankRenderer.renderImageWithDPI(page - 1, 36);
                ImageIO.write(rendered, "png", OUTPUT.resolve("all-fields-page-" + page + ".png").toFile());
                int checkedFields = 0;
                try (var input = getClass().getResourceAsStream("/oscar/form/rourke2020/page" + page + ".jrxml")) {
                    for (var band : JRXmlLoader.load(input).getDetailSection().getBands()) {
                        for (JRElement element : band.getElements()) {
                            if (!(element instanceof JRTextField field)) continue;
                            String expression = field.getExpression().getText();
                            if (!expression.matches("\\$P\\{(?:c_pName|c_birthDate|p[1-4]_(?:date|ht|wt|hc|pConcern).*)}")) continue;
                            String parameter = expression.substring(3, expression.length() - 1);
                            PDFTextStripperByArea text = new PDFTextStripperByArea();
                            text.addRegion("field", new Rectangle2D.Double(field.getX(), field.getY(), field.getWidth(), field.getHeight()));
                            text.extractRegions(pdf.getPage(page - 1));
                            assertThat(text.getTextForRegion("field").strip()).as("page %s, %s", page, parameter)
                                    .isEqualTo(values.getProperty(parameter));
                            assertThat(changedPixels(rendered, background, field)).as("visible page %s, %s", page, parameter)
                                    .isGreaterThan(5);
                            checkedFields++;
                        }
                    }
                }
                assertThat(checkedFields).as("populated fields on page %s", page).isEqualTo(page == 4 ? 16 : 17);
                for (var name : pdf.getPage(page - 1).getResources().getFontNames()) {
                    var font = pdf.getPage(page - 1).getResources().getFont(name);
                    assertThat(font.getName()).contains("DejaVuSans");
                    assertThat(font.isEmbedded()).isTrue();
                    assertThat(font.getCOSObject().getDictionaryObject(org.apache.pdfbox.cos.COSName.TO_UNICODE)).isNotNull();
                }
            }
        }
    }

    private static int changedPixels(BufferedImage rendered, BufferedImage background, JRElement field) {
        int changes = 0;
        for (int y = field.getY() / 2; y < (field.getY() + field.getHeight()) / 2; y++) {
            for (int x = field.getX() / 2; x < (field.getX() + field.getWidth()) / 2; x++) {
                if (rendered.getRGB(x, y) != background.getRGB(x, y)) changes++;
            }
        }
        return changes;
    }

    @Test
    void shouldRefuseUnsupportedCharacters_withoutWritingAnyPdfBytes() {
        Properties values = recordProperties("FAKE-患者");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThatThrownBy(() -> Rourke2020PdfReport.write(output, values, List.of(1)))
                .isInstanceOf(Rourke2020PdfReport.RenderingException.class)
                .hasMessageContaining("characters").hasMessageNotContaining("FAKE").hasMessageNotContaining("患者");
        assertThat(output.size()).isZero();
    }

    @Test
    void shouldRefuseClippedText_withoutWritingAnyPdfBytes() {
        Properties values = recordProperties("FAKE-" + "LONG-NAME-".repeat(200));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThatThrownBy(() -> Rourke2020PdfReport.write(output, values, List.of(1)))
                .isInstanceOf(Rourke2020PdfReport.RenderingException.class)
                .hasMessageContaining("does not fit").hasMessageNotContaining("FAKE");
        assertThat(output.size()).isZero();
    }

    @Test
    void shouldDiscardPartialExport_whenExporterFails() throws Exception {
        AtomicReference<OutputStreamExporterOutput> exporterOutput = new AtomicReference<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (var ignored = mockConstruction(JRPdfExporter.class, (exporter, context) -> {
            doAnswer(invocation -> { exporterOutput.set(invocation.getArgument(0)); return null; })
                    .when(exporter).setExporterOutput(any());
            doAnswer(invocation -> {
                exporterOutput.get().getOutputStream().write("%PDF-partial".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                throw new JRException("Synthetic exporter failure");
            }).when(exporter).exportReport();
        })) {
            assertThatThrownBy(() -> Rourke2020PdfReport.write(output, recordProperties("FAKE-EXPORT"), List.of(1)))
                    .isInstanceOf(JRException.class).hasMessage("Synthetic exporter failure");
        }
        assertThat(output.size()).isZero();
    }

    @Test
    void shouldRefuseMissingFontExtension_evenWhenMissingFontWarningsAreDisabled() throws Exception {
        String classpath = Arrays.stream(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"))
                        .split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .filter(path -> !Path.of(path).getFileName().toString().startsWith("jasperreports-fonts-"))
                .collect(Collectors.joining(File.pathSeparator));
        Files.createDirectories(OUTPUT);
        Path log = OUTPUT.resolve("missing-font.log");
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true", "-Dnet.sf.jasperreports.awt.ignore.missing.font=true", "-cp", classpath,
                MissingFontProbe.class.getName()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean finished = child.waitFor(45, TimeUnit.SECONDS);
        if (!finished) child.destroyForcibly();
        assertThat(finished).as("missing-font child JVM completed").isTrue();
        assertThat(child.exitValue()).as(Files.readString(log)).isZero();
    }

    /** Runs without Spring or the font extension in a fresh classloader/JVM. */
    public static class MissingFontProbe {
        public static void main(String[] args) throws Exception {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try {
                Rourke2020PdfReport.write(output, new Properties(), List.of(1));
                throw new AssertionError("Missing font extension was accepted");
            } catch (Rourke2020PdfReport.RenderingException failure) {
                if (!failure.getMessage().contains("bundled DejaVu") || output.size() != 0) {
                    throw new AssertionError("Missing font did not fail safely");
                }
            }
        }
    }

    @Test
    void shouldPrintBlankDatesAndCompactBackgrounds_withoutReusingPatientData() throws Exception {
        long coldStart = System.nanoTime();
        byte[] first = print(recordProperties("FAKE-FIRST"), List.of(1, 2, 3, 4));
        long coldMillis = (System.nanoTime() - coldStart) / 1_000_000;
        long warmStart = System.nanoTime();
        byte[] second = print(recordProperties("FAKE-SECOND"), List.of(1, 2, 3, 4));
        long warmMillis = (System.nanoTime() - warmStart) / 1_000_000;
        System.out.printf("Rourke PDF: %d bytes; cold %d ms; warm %d ms%n", second.length, coldMillis, warmMillis);
        // A document-size assertion is deterministic; wall-clock timings are diagnostics only.
        assertThat(first).hasSizeLessThan(6_000_000);
        try (PDDocument pdf = Loader.loadPDF(second)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(4);
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("FAKE-SECOND", "08/01/2026").doesNotContain("FAKE-FIRST", "null");
            // Inspect each visit's actual printed date box. Whole-document searches
            // cannot distinguish a blank field from a default date or placeholder.
            int[] dateRows = {340, 335, 335, 320};
            for (int page = 0; page < dateRows.length; page++) {
                PDFTextStripperByArea dates = new PDFTextStripperByArea();
                for (int visit = 0; visit < 3; visit++) {
                    dates.addRegion("visit" + visit,
                            new Rectangle2D.Double(210 + 530 * visit, dateRows[page], 165, 22));
                }
                dates.extractRegions(pdf.getPage(page));
                for (int visit = 0; visit < 3; visit++) {
                    assertThat(dates.getTextForRegion("visit" + visit).strip())
                            .as("date on page %s, visit %s", page + 1, visit + 1)
                            .isEqualTo(page == 0 && visit == 0 ? "08/01/2026" : "");
                }
            }
            assertThat(pdf.getPage(0).getMediaBox().getWidth()).isEqualTo(1700);
            assertThat(pdf.getPage(0).getMediaBox().getHeight()).isEqualTo(2200);
        }
        Files.write(OUTPUT.resolve("record.pdf"), second);
    }

    @Test
    void shouldPrintAllSevenNotes_onLabelledContinuationPages() throws Exception {
        Properties values = recordProperties("FAKE-NOTES");
        List<String> fields = List.of("p2_development2m", "p2_development4m", "p2_development6m",
                "p2_physical2m", "p2_physical4m", "p2_physical6m", "p2_immunization6m");
        for (int i = 0; i < fields.size(); i++) values.setProperty(fields.get(i), "NOTE-" + i + "-PRESERVED");
        byte[] bytes = print(values, List.of(1, 2, 3, 4));
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(5);
            String text = new PDFTextStripper().getText(pdf);
            for (int i = 0; i < fields.size(); i++) assertThat(text).contains("NOTE-" + i + "-PRESERVED");
            assertThat(text).contains("Page II notes", "2 months", "4 months", "6 months");
        }
        try (PDDocument pdf = Loader.loadPDF(print(values, List.of(1, 3, 4)))) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(3);
            assertThat(new PDFTextStripper().getText(pdf)).doesNotContain("NOTE-0-PRESERVED");
        }
        Files.write(OUTPUT.resolve("notes.pdf"), bytes);
    }

    @Test
    void shouldPreserveClinicalSymbolsAndAccentedNames_whenPrintingNotes() throws Exception {
        Properties values = recordProperties("FAKE-Łucja");
        String note = "weight ≥ 5 kg; temperature ≤ 38°C; Δ weight; Na⁺";
        values.setProperty("p2_physical2m", note);
        try (PDDocument pdf = Loader.loadPDF(print(values, List.of(2)))) {
            PDFTextStripper text = new PDFTextStripper();
            text.setStartPage(2);
            assertThat(text.getText(pdf)).contains("FAKE-Łucja", note);
        }
    }

    @Test
    void shouldKeepLongNotes_whenContinuationOverflowsOnePage() throws Exception {
        Properties values = recordProperties("FAKE-LONG-NOTES");
        values.setProperty("p2_development2m", "Clinical note line\n".repeat(120) + "END-OF-LONG-NOTE");
        try (PDDocument pdf = Loader.loadPDF(print(values, List.of(2)))) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(2);
            assertThat(new PDFTextStripper().getText(pdf)).contains("END-OF-LONG-NOTE");
        }
    }

    @Test
    void shouldRejectUnknownPages_beforeLoadingResources() {
        Properties values = recordProperties("FAKE-INVALID");
        List<Integer> invalidPages = List.of(5);
        assertThatThrownBy(() -> print(values, invalidPages))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
