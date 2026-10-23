/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form.util;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Fills every field on the four main pages to capacity and compares the result with the blank
     * form at the background scan's own resolution (issue #5046). On the blank form a field's box
     * may hold only paper and the thin ruled lines it is written on; on the filled form every
     * printed pixel must stay inside its own box and clear of printed text outside it. Together
     * these keep record text off the form's labels, guidance and section headers. Before this
     * layout the 12- and 24-month head circumferences overprinted "(avg 47 cm)" and "if prior
     * abN", growth values touched their labels, full notes ran over the next section header,
     * visit dates were crossed by the hand-fill slashes and long names ran into "Birth Day".
     * Text extraction and the changed-pixel count above cannot see any of that.
     */
    @Test
    void shouldKeepFullFields_clearOfPrintedForm() throws Exception {
        java.awt.Font dejaVu;
        try (var input = getClass().getResourceAsStream("/net/sf/jasperreports/fonts/dejavu/DejaVuSans.ttf")) {
            dejaVu = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, input);
        }
        List<String> problems = new ArrayList<>();
        for (int page = 1; page <= 4; page++) {
            Properties blank = recordProperties("");
            for (String key : List.of("c_birthDate", "p1_date1w", "c_male", "c_gestationalAge")) blank.setProperty(key, "");
            Properties filled = (Properties) blank.clone();
            Map<String, JRTextField> fields = new LinkedHashMap<>();
            try (var input = getClass().getResourceAsStream("/oscar/form/rourke2020/page" + page + ".jrxml")) {
                for (var band : JRXmlLoader.load(input).getDetailSection().getBands()) {
                    for (JRElement element : band.getElements()) {
                        if (!(element instanceof JRTextField field)) continue;
                        String expression = field.getExpression().getText();
                        if (!expression.matches("\\$P\\{\\w+}") || expression.matches(".*c_(?:male|female).*")) continue;
                        String parameter = expression.substring(3, expression.length() - 1);
                        fields.put(parameter, field);
                        filled.setProperty(parameter, capacityText(parameter, field, dejaVu));
                    }
                }
            }
            BufferedImage form = renderPage(blank, page);
            BufferedImage printed = renderPage(filled, page);
            problems.addAll(layoutProblems(page, fields, form, printed));
        }
        assertThat(problems).isEmpty();
    }

    /** The longest value a field takes: five-character growth values, dates, or full lines of text. */
    private static String capacityText(String parameter, JRTextField field, java.awt.Font dejaVu) {
        if (parameter.matches("p\\d_(?:ht|wt|hc|bmi)\\w+")) return "888.8"; // the form's five-character limit; DejaVu digits are its widest
        if (parameter.contains("date") || parameter.equals("c_birthDate")) return "28/12/2026";
        float size = field.getFontSize();
        var context = new java.awt.font.FontRenderContext(null, true, true);
        java.awt.Font font = dejaVu.deriveFont(size);
        String sample = "Hgy8|".repeat(200);
        int length = 1;
        while (font.getStringBounds(sample, 0, length + 1, context).getWidth() < field.getWidth() - 2) length++;
        int lines = Math.max(1, (int) (field.getHeight() / (size * 1.17f)));
        return String.join("\n", java.util.Collections.nCopies(lines, sample.substring(0, length)));
    }

    /** Renders one page at 108 dpi, the background scan's resolution: 1.5 pixels per template point. */
    private static BufferedImage renderPage(Properties values, int page) throws Exception {
        try (PDDocument pdf = Loader.loadPDF(print(values, List.of(page)))) {
            return new PDFRenderer(pdf).renderImageWithDPI(0, 108);
        }
    }

    private static List<String> layoutProblems(int page, Map<String, JRTextField> fields, BufferedImage form,
                                               BufferedImage printed) {
        int width = form.getWidth();
        int height = form.getHeight();
        int[] formPixels = form.getRGB(0, 0, width, height, null, 0, width);
        int[] printedPixels = printed.getRGB(0, 0, width, height, null, 0, width);
        boolean[] marked = new boolean[formPixels.length];
        for (int i = 0; i < formPixels.length; i++) {
            int rgb = formPixels[i];
            // Darker than the faint speckle in the scan; the grey cell rules are about 227.
            marked[i] = (((rgb >> 16) & 0xff) * 299 + ((rgb >> 8) & 0xff) * 587 + (rgb & 0xff) * 114) / 1000 < 240;
        }
        List<String> problems = new ArrayList<>();
        List<Map.Entry<String, int[]>> boxes = new ArrayList<>();
        fields.forEach((name, field) -> boxes.add(Map.entry(name, new int[]{Math.round(field.getX() * 1.5f),
                Math.round(field.getY() * 1.5f), Math.round((field.getX() + field.getWidth()) * 1.5f),
                Math.round((field.getY() + field.getHeight()) * 1.5f)})));
        int[] owner = new int[formPixels.length];
        Arrays.fill(owner, -1);
        for (int b = 0; b < boxes.size(); b++) {
            int[] r = boxes.get(b).getValue();
            for (int other = 0; other < b; other++) {
                int[] o = boxes.get(other).getValue();
                if (r[0] < o[2] && o[0] < r[2] && r[1] < o[3] && o[1] < r[3]) {
                    problems.add("page " + page + ": " + boxes.get(other).getKey() + " and " + boxes.get(b).getKey() + " overlap");
                }
            }
            for (int y = r[1]; y < r[3]; y++) Arrays.fill(owner, y * width + r[0], y * width + r[2], b);
        }
        for (int b = 0; b < boxes.size(); b++) {
            String name = boxes.get(b).getKey();
            int[] r = boxes.get(b).getValue();
            boolean exempt = name.equals("c_birthDate"); // printed across its d/m/yy guide; there is no blank space for it
            // Thin rules the field is written on, including ones just past its edges, and their antialiased fringes.
            int top = Math.max(0, r[1] - 4);
            int bottom = Math.min(height, r[3] + 4);
            boolean[] rule = new boolean[bottom - top];
            int run = 0;
            for (int y = top; y < bottom; y++) {
                int count = 0;
                for (int x = r[0]; x < r[2]; x++) if (marked[y * width + x]) count++;
                rule[y - top] = count * 2 >= r[2] - r[0];
                run = rule[y - top] && y >= r[1] && y < r[3] ? run + 1 : 0;
                if (run == 6 && !exempt) problems.add("page " + page + ": " + name + " box covers a printed band");
            }
            boolean[] onRule = new boolean[rule.length];
            for (int y = 0; y < rule.length; y++) {
                for (int d = -3; d <= 3; d++) onRule[y] |= y + d >= 0 && y + d < rule.length && rule[y + d];
            }
            if (!exempt) {
                // On the blank form the box may hold those rules, nothing else.
                int stray = 0;
                String first = "";
                for (int y = r[1]; y < r[3]; y++) {
                    if (onRule[y - top]) continue;
                    for (int x = r[0]; x < r[2]; x++) {
                        if (marked[y * width + x] && stray++ == 0) first = " near point " + x * 2 / 3 + "," + y * 2 / 3;
                    }
                }
                if (stray > 0) problems.add("page " + page + ": " + name + " box covers " + stray + " pixels of printed text" + first);
            }
            // Printed pixels must stay in their own box, two pixels clear of print outside it.
            int touching = 0;
            for (int y = top; y < bottom && !exempt; y++) {
                for (int x = Math.max(0, r[0] - 3); x < Math.min(width, r[2] + 3); x++) {
                    int i = y * width + x;
                    if (printedPixels[i] == formPixels[i] || owner[i] != b) continue;
                    for (int dy = -2; dy <= 2; dy++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            int ny = y + dy;
                            int nx = x + dx;
                            if (ny < 0 || nx < 0 || ny >= height || nx >= width) continue;
                            boolean ruled = ny >= top && ny < bottom && onRule[ny - top];
                            int n = ny * width + nx;
                            if (marked[n] && owner[n] != b && !ruled) touching++;
                        }
                    }
                }
            }
            if (touching > 0) problems.add("page " + page + ": " + name + " text touches the printed form next to its box");
        }
        int outside = 0;
        for (int i = 0; i < formPixels.length; i++) if (printedPixels[i] != formPixels[i] && owner[i] < 0) outside++;
        if (outside > 0) problems.add("page " + page + ": " + outside + " printed pixels fall outside every field box");
        return problems;
    }

    /** Fields that would overprint the next section shrink a long entry instead (issue #5046). */
    @Test
    void shouldShrinkLongEntries_insteadOfOverprintingTheForm() throws Exception {
        Properties values = recordProperties("FAKE-SHRINK");
        List<String> concern = new ArrayList<>();
        for (int line = 1; line <= 6; line++) concern.add("FAKE-CONCERN-" + line + " feeding and sleep reviewed");
        values.setProperty("p4_pConcern18m", String.join("\n", concern));
        values.setProperty("p4_problems24m", String.join("\n", concern).replace("CONCERN", "PLAN"));
        values.setProperty("p4_hc24m", "47.25 cm");
        try (PDDocument pdf = Loader.loadPDF(print(values, List.of(4)))) {
            String text = new PDFTextStripper().getText(pdf);
            for (int line = 1; line <= 6; line++) assertThat(text).contains("FAKE-CONCERN-" + line, "FAKE-PLAN-" + line);
            PDFTextStripperByArea headCircumference = new PDFTextStripperByArea();
            headCircumference.addRegion("value", new Rectangle2D.Double(956, 432, 60, 22));
            headCircumference.extractRegions(pdf.getPage(0));
            assertThat(headCircumference.getTextForRegion("value").strip()).isEqualTo("47.25 cm");
        }
    }

    @Test
    void shouldRefuseEntries_tooLongToReadAtTheSmallestSize() {
        Properties values = recordProperties("FAKE-TOO-LONG");
        values.setProperty("p4_problems18m", "FAKE plan line\n".repeat(60));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThatThrownBy(() -> Rourke2020PdfReport.write(output, values, List.of(4)))
                .isInstanceOf(Rourke2020PdfReport.RenderingException.class)
                .hasMessageContaining("does not fit").hasMessageNotContaining("FAKE");
        assertThat(output.size()).isZero();
    }

    @Test
    void shouldLeaveFieldsBlank_whenRecordHasNoValue() throws Exception {
        Properties values = recordProperties("FAKE-NO-GESTATION");
        values.remove("c_gestationalAge");
        try (PDDocument pdf = Loader.loadPDF(print(values, List.of(1, 2, 3, 4)))) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("FAKE-NO-GESTATION").doesNotContain("null");
        }
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
            int[] dateRows = {327, 324, 324, 317};
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
