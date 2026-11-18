/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JRTextField;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.util.JRElementsVisitor;
import net.sf.jasperreports.engine.util.JRVisitorSupport;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.pdf.JRPdfExporter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders the Rourke 2017 and BCAR 2020 templates the way {@link FrmRourke2017Record#PrintJasperPdf}
 * and {@link FrmBCAR20202Action} do (default JasperReports context, record values as parameters,
 * an empty data source, the plain PDF exporter) and checks that patient identifiers print in
 * their own boxes with embedded bundled fonts.
 *
 * <p>Both form families named no font, so before the CARLOS report font policy (issue #5046) they
 * measured text with the host's sans-serif face: on a host whose face has a tall line, Rourke 2017's
 * 16pt fields fitted nothing in their 22pt boxes, and on every host the PDF dropped characters
 * outside Cp1252. {@code scripts/check-report-fonts.sh} runs this class under such hosts. All values
 * are synthetic.
 */
@Tag("unit")
@Tag("report")
class ClinicalFormJasperRenderingUnitTest {
    private static final Path OUTPUT = Path.of(System.getProperty("report.font.output", "target/report-font-rendering"));
    private static final String NAME = "FAKE-Nguyễn Łucja";

    static Stream<Arguments> forms() {
        Map<String, String> rourke = Map.of("c_pName", NAME, "c_birthDate", "01/01/2026");
        Map<String, String> rourkePage1 = new HashMap<>(rourke);
        rourkePage1.putAll(Map.of("p1_date1w", "08/01/2026", "p1_ht1w", "51.2", "p1_wt1w", "3.5"));
        return Stream.of(
                Arguments.of("/oscar/form/rourke2017/", "page1", rourkePage1),
                Arguments.of("/oscar/form/rourke2017/", "page2", rourke),
                Arguments.of("/oscar/form/rourke2017/", "page3", rourke),
                Arguments.of("/oscar/form/rourke2017/", "page4", rourke),
                Arguments.of("/oscar/form/bcar2020/", "BCAR2020_pg1", Map.of(
                        "t_patientSurname", "FAKE-Nguyễn", "t_patientGivenName", "Łucja",
                        "t_patientDOB", "2000/01/01", "t_patientHIN", "9876543210",
                        "t_patientAddress", "12 Rue Élodie, Montréal")));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("forms")
    @DisplayName("should print patient identifiers in their boxes, with embedded bundled fonts")
    void shouldPrintPatientIdentifiers_withEmbeddedBundledFonts(String directory, String page, Map<String, String> values)
            throws Exception {
        JasperReport report;
        try (InputStream template = getClass().getResourceAsStream(directory + page + ".jrxml")) {
            report = JasperCompileManager.compileReport(template);
        }
        Map<String, Object> parameters = new HashMap<>();
        // The form records supply every field, blank when unset; the templates compare them with equals().
        for (JRParameter parameter : report.getParameters()) {
            if (!parameter.isSystemDefined()) {
                parameters.put(parameter.getName(), "");
            }
        }
        for (String image : List.of("background_image", "check_image", "cross_image")) {
            if (parameters.containsKey(image)) {
                String name = image.equals("background_image") ? page + ".png" : image.replace("_image", ".png");
                parameters.put(image, getClass().getResource(directory + name).toString());
            }
        }
        parameters.putAll(values);

        JasperPrint print = JasperFillManager.fillReport(report, parameters, new JREmptyDataSource());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JRPdfExporter exporter = new JRPdfExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(bytes));
        exporter.exportReport();
        Files.createDirectories(OUTPUT);
        Files.write(OUTPUT.resolve(page + ".pdf"), bytes.toByteArray());

        try (PDDocument pdf = Loader.loadPDF(bytes.toByteArray())) {
            for (Map.Entry<String, String> value : values.entrySet()) {
                List<JRTextField> boxes = fields(report, value.getKey());
                assertThat(boxes).as("%s boxes for %s", page, value.getKey()).isNotEmpty();
                for (JRTextField field : boxes) {
                    PDFTextStripperByArea area = new PDFTextStripperByArea();
                    area.addRegion("field", new Rectangle2D.Double(field.getX(), field.getY(),
                            field.getWidth(), field.getHeight()));
                    area.extractRegions(pdf.getPage(0));
                    assertThat(area.getTextForRegion("field").strip()).as("%s %s", page, value.getKey())
                            .isEqualTo(value.getValue());
                }
            }
            var resources = pdf.getPage(0).getResources();
            for (COSName name : resources.getFontNames()) {
                var font = resources.getFont(name);
                assertThat(font.getName()).as("PDF font on %s", page).contains("DejaVuSans");
                assertThat(font.isEmbedded()).as("%s embedded on %s", font.getName(), page).isTrue();
            }
        }
    }

    private static List<JRTextField> fields(JasperReport report, String parameter) {
        List<JRTextField> fields = new ArrayList<>();
        JRElementsVisitor.visitReport(report, new JRVisitorSupport() {
            @Override
            public void visitTextField(JRTextField field) {
                if (field.getExpression() != null && field.getExpression().getText().strip().equals("$P{" + parameter + "}")) {
                    fields.add(field);
                }
            }
        });
        return fields;
    }
}
