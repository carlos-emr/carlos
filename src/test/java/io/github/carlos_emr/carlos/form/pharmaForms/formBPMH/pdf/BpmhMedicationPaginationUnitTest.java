/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhDrug;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhForm2Bean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;

@Tag("unit")
class BpmhMedicationPaginationUnitTest {
    @ParameterizedTest
    @CsvSource({"17,false", "17,true", "18,false", "18,true", "25,false", "25,true"})
    void printsEveryMedicineAndItsCompleteDirections(int count, boolean longText, @TempDir Path output)
            throws Exception {
        var bean = new BpmhForm2Bean();
        var patient = new Demographic();
        patient.setFirstName("Synthetic");
        patient.setLastName("BPMH-Patient");
        bean.setDemographic(patient);
        var drugs = new ArrayList<BpmhDrug>();
        for (int i = 1; i <= count; i++) {
            var drug = new BpmhDrug();
            drug.setId(Integer.toString(i));
            drug.setWhat("Fixture-Med-%02d".formatted(i));
            drug.setHow("DIR-%02d".formatted(i));
            drug.setWhy("WHY-%02d".formatted(i));
            if (longText && i == count) {
                drug.setHow("DIR-%02d ".formatted(i) + "Répéter selon les directives. ".repeat(160) + "LAST-DIRECTION");
                drug.setWhy("WHY-%02d\nSECOND-INDICATION".formatted(i));
                drug.setPatientReportedUse("REPORTED-USE");
                drug.setInstruction("FINAL-NOTE");
            }
            drugs.add(drug);
        }
        bean.setDrugs(drugs);
        var template = getClass().getResource("/oscar/form/prop/bpmh_template_marked.pdf");
        var pdf = new PDFController(Path.of(template.toURI()).toString());
        pdf.setOutputPath(output.toString());
        pdf.setContinueInstructions(true);
        pdf.writeDataToPDF(bean, new String[]{"1"}, "synthetic");
        try (var rendered = Loader.loadPDF(Path.of(pdf.getOutputPath()).toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(rendered);
            for (int i = 1; i <= count; i++) {
                assertThat(text).contains("Fixture-Med-%02d".formatted(i), "DIR-%02d".formatted(i), "WHY-%02d".formatted(i));
            }
            if (longText) {
                String continuousText = text.replace("BPMH medication instruction notes (continued)", "")
                        .replace("Patient: BPMH-Patient, Synthetic", "")
                        .replaceAll("\\s+", " ")
                        .replace("LAST- DIRECTION", "LAST-DIRECTION");
                assertThat(continuousText).contains(drugs.getLast().getHow(),
                        "WHY-%02d SECOND-INDICATION".formatted(count), "REPORTED-USE", "FINAL-NOTE");
                assertThat(rendered.getNumberOfPages()).isGreaterThan(2);
            }
            for (int page = 2; page <= rendered.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                assertThat(stripper.getText(rendered)).contains("BPMH-Patient", "Synthetic");
            }
        }
    }

    @Test
    void refusesAMissingMedicationField(@TempDir Path output) throws Exception {
        Path wrongTemplate = output.resolve("missing.pdf");
        try (var stream = Files.newOutputStream(wrongTemplate); var document = new Document()) {
            PdfWriter.getInstance(document, stream);
            document.open();
            document.add(new Paragraph("Template without medication fields"));
        }
        var bean = new BpmhForm2Bean();
        bean.setDrugs(java.util.List.of(new BpmhDrug()));
        var pdf = new PDFController(wrongTemplate.toString());
        pdf.setOutputPath(output.toString());
        pdf.setContinueInstructions(true);
        assertThatThrownBy(() -> pdf.writeDataToPDF(bean, new String[]{"1"}, "synthetic"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("missing a medication field");
    }

    @Test
    void shouldKeepNineMedicationsOnSinglePage_whenRowNineReasonIsBlank(@TempDir Path output) throws Exception {
        var bean = new BpmhForm2Bean();
        var patient = new Demographic();
        patient.setFirstName("Synthetic");
        patient.setLastName("RowNine-BlankWhy");
        bean.setDemographic(patient);
        var drugs = new ArrayList<BpmhDrug>();
        for (int i = 1; i <= 9; i++) {
            var drug = new BpmhDrug();
            drug.setId(Integer.toString(i));
            drug.setWhat("Fixture-Med-%02d".formatted(i));
            drug.setHow("DIR-%02d".formatted(i));
            drug.setWhy(i == 9 ? "" : "WHY-%02d".formatted(i));
            drug.setInstruction("NOTE-%02d".formatted(i));
            drugs.add(drug);
        }
        bean.setDrugs(drugs);
        var template = getClass().getResource("/oscar/form/prop/bpmh_template_marked.pdf");
        var pdf = new PDFController(Path.of(template.toURI()).toString());
        pdf.setOutputPath(output.toString());
        pdf.setContinueInstructions(true);
        pdf.writeDataToPDF(bean, new String[]{"1"}, "synthetic");
        try (var rendered = Loader.loadPDF(Path.of(pdf.getOutputPath()).toFile())) {
            assertThat(rendered.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(rendered);
            assertThat(text).contains("Fixture-Med-09", "DIR-09", "NOTE-09")
                    .doesNotContain("See instructions #9");
        }
    }
}
