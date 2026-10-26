/**
 * Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences,
 * University of British Columbia. All Rights Reserved.
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
 * This software was written for the
 * The Pharmacists Clinic
 * Faculty of Pharmaceutical Sciences
 * University of British Columbia
 * Vancouver, British Columbia, Canada
 *
 * <p>
 * Migrated from legacy JUnit 4 PDFControllerTest to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.net.URL;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.openpdf.text.pdf.PdfReader;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhDrug;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhForm2Bean;

/**
 * Unit tests for {@link PDFController}.
 *
 * <p>Tests PDF template loading, output path management, and getter method
 * invocation for BPMH (Best Possible Medication History) form PDF generation.
 * Migrated from legacy JUnit 4 PDFControllerTest.
 *
 * @since 2026-03-07
 */
@Tag("unit")
@Tag("form")
@DisplayName("PDFController unit tests")
class PDFControllerUnitTest {

    private PDFController pdfController;
    private BpmhForm2Bean data;
    private Demographic demographic;

    @BeforeEach
    void setUp() {
        ClassLoader loader = PDFController.class.getClassLoader();
        URL url = loader.getResource("oscar/form/prop/bpmh_template_marked.pdf");

        pdfController = new PDFController(url.getPath());
        pdfController.setOutputPath("/var/lib/CarlosDocument");

        demographic = new Demographic();
        demographic.setDemographicNo(12345);
        demographic.setFirstName("Dennis");
        demographic.setLastName("Warren");
        demographic.setHin("9374636728674");
        demographic.setEffDate(new Date());

        BpmhDrug bpmhDrug1 = new BpmhDrug();
        bpmhDrug1.setGenericName("GENERIC DRUG");
        bpmhDrug1.setWhy("This is a description.");
        bpmhDrug1.setWhat("chicken butt");

        BpmhDrug bpmhDrug2 = new BpmhDrug();
        bpmhDrug2.setGenericName("DRUG NAME");
        bpmhDrug2.setWhy("take this drug daily");

        List<BpmhDrug> bpmhDrugList = new ArrayList<>();
        bpmhDrugList.add(bpmhDrug1);
        bpmhDrugList.add(bpmhDrug2);

        data = new BpmhForm2Bean();
        data.setDemographicNo("2345");
        data.setFamilyDrName("Dr. Who");
        data.setDemographic(demographic);
        data.setDrugs(bpmhDrugList);
    }

    @Test
    @DisplayName("should return configured output path")
    void shouldReturnOutputPath_whenSet() {
        assertThat(pdfController.getOutputPath()).isEqualTo("/var/lib/CarlosDocument");
    }

    @Test
    @DisplayName("should return PDF template filename from input path")
    void shouldReturnTemplateFilename_fromInputPath() {
        assertThat(pdfController.getFilePath().getName()).isEqualTo("bpmh_template_marked.pdf");
    }

    @Test
    @DisplayName("should invoke getter methods on demographic object via reflection")
    void shouldInvokeGetterMethods_onDemographicObject() {
        pdfController.setDataObject(demographic);
        assertThat(pdfController.invokeValue("firstName")).isEqualTo("Dennis");
        assertThat(pdfController.invokeValue("hin")).isEqualTo("9374636728674");
    }

    @Test
    @DisplayName("should return null for missing getter methods")
    void shouldReturnNull_forMissingGetterMethods() {
        Map<String, Method> getterMethods = PDFController.getGetterMethods(data);
        assertThat(PDFController.invokeValue("democratic.fakemethod", getterMethods, data)).isNull();
        assertThat(PDFController.invokeValue("fakemethod", getterMethods, data)).isNull();
    }

    @Test
    void shouldContinueTheActualClippedInstructionWithItsMedicationAndDirections(@TempDir Path output) throws Exception {
        String instruction = "FAKE-PW1234567890abcdef continue at discharge";
        byte[] bytes = renderInstructions(output, List.of(instruction), true);
        try (var pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(2);
            PDFTextStripper first = new PDFTextStripper(); first.setEndPage(1);
            assertThat(first.getText(pdf)).contains("See instructions #1");
            String continuation = continuationTextAndCheckGeometry(bytes);
            assertThat(compact(continuation)).contains(compact(instruction));
            assertThat(continuation).contains("FAKE medication 1 20 mg", "Take 1 tablet by mouth daily", "Instruction #1:");
        }
    }

    @Test
    void shouldPaginateLongClinicalTextAndUnbrokenUnicodeWithoutShrinking(@TempDir Path output) throws Exception {
        String clinical = "Take 1 tablet by mouth with breakfast; hold if dizzy and contact the pharmacist. ".repeat(180) + " END-CLINICAL";
        String unicode = "Nguyễn Łukasz ≥ 5 中文 用藥指示 " + "LONGIDENTIFIER1234567890".repeat(30) + " 結束 END-UNICODE";
        byte[] bytes = renderInstructions(output, List.of(clinical, unicode), true);
        try (var pdf = Loader.loadPDF(bytes)) { assertThat(pdf.getNumberOfPages()).isGreaterThan(3); }
        String text = compact(continuationTextAndCheckGeometry(bytes));
        assertThat(text).contains(compact(clinical), compact(unicode));
    }

    @Test
    void shouldKeepShortInstructionsInTheExistingRowsAtReadableSize(@TempDir Path output) throws Exception {
        byte[] bytes = renderInstructions(output, List.of("With food", ""), true);
        try (var pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(pdf)).contains("With food").doesNotContain("See instructions");
            Positions positions = new Positions(); positions.getText(pdf);
            assertThat(positions.glyphs.stream().filter(g -> g.getXDirAdj() > 490 && g.getUnicode().contains("W")))
                    .isNotEmpty().allSatisfy(g -> assertThat(g.getFontSizeInPt()).isGreaterThanOrEqualTo(7));
        }
    }

    @Test
    void shouldNamePatientWithoutRecordNumber_onEveryContinuationPage(@TempDir Path output) throws Exception {
        String clinical = "Take 1 tablet by mouth with breakfast; hold if dizzy and contact the pharmacist. ".repeat(120);
        byte[] bytes = renderInstructions(output, List.of(clinical), true);
        try (var pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(2);
            for (int page = 2; page <= pdf.getNumberOfPages(); page++) {
                PDFTextStripper stripper = new PDFTextStripper(); stripper.setStartPage(page); stripper.setEndPage(page);
                String text = stripper.getText(pdf);
                assertThat(text).contains("BPMH medication instruction notes (continued)", "Patient: Warren, Dennis");
                assertThat(text).doesNotContain("2345").doesNotContain("12345");
            }
        }
    }

    @Test
    void shouldLeaveContinuationDisabledForCallersThatDoNotOptIn(@TempDir Path output) throws Exception {
        byte[] bytes = renderInstructions(output, List.of("FAKE-PW1234567890abcdef continue at discharge"), false);
        try (var pdf = Loader.loadPDF(bytes)) { assertThat(pdf.getNumberOfPages()).isEqualTo(1); }
    }

    private byte[] renderInstructions(Path output, List<String> instructions, boolean continueInstructions) throws IOException {
        List<BpmhDrug> drugs = new ArrayList<>();
        for (int i = 0; i < instructions.size(); i++) {
            BpmhDrug drug = new BpmhDrug();
            drug.setWhat("FAKE medication " + (i + 1) + " 20 mg");
            drug.setHow("Take 1 tablet by mouth daily");
            drug.setInstruction(instructions.get(i));
            drugs.add(drug);
        }
        data.setDrugs(drugs);
        data.setNote("FAKE preserved note");
        pdfController.setContinueInstructions(continueInstructions);
        pdfController.setOutputPath(output.toString());
        pdfController.writeDataToPDF(data, new String[]{"1"}, "FAKE-UNIT");
        return Files.readAllBytes(Path.of(pdfController.getOutputPath()));
    }

    private String continuationTextAndCheckGeometry(byte[] bytes) throws Exception {
        StringBuilder text = new StringBuilder();
        try (var pdf = Loader.loadPDF(bytes); PdfReader reader = new PdfReader(bytes)) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            for (int page = 2; page <= pdf.getNumberOfPages(); page++) {
                Positions positions = new Positions(); positions.setStartPage(page); positions.setEndPage(page);
                String pageText = positions.getText(pdf);
                assertThat(pageText).contains("BPMH medication instruction notes");
                text.append(pageText.replaceAll("BPMH medication instruction notes[^\\n]*\\n", "")
                        .replaceAll("Patient: [^\\n]*\\n", ""));
                assertThat(positions.glyphs).isNotEmpty().allSatisfy(glyph -> {
                    assertThat(glyph.getFontSizeInPt()).isGreaterThanOrEqualTo(10);
                    assertThat(glyph.getXDirAdj()).isGreaterThanOrEqualTo(35.9f);
                    assertThat(glyph.getXDirAdj() + glyph.getWidthDirAdj()).isLessThanOrEqualTo(576.1f);
                    assertThat(glyph.getYDirAdj()).isBetween(25.9f, 756.1f);
                });
                // Text extraction can include invisible clipped text. Continuations must have no clip operators.
                assertThat(new String(reader.getPageContent(page), StandardCharsets.ISO_8859_1))
                        .doesNotMatch("(?s).*\\sW\\*?\\s.*");
            }
        }
        return text.toString();
    }

    private static String compact(String value) { return value.replaceAll("\\s+", ""); }

    private static final class Positions extends PDFTextStripper {
        final List<TextPosition> glyphs = new ArrayList<>();
        Positions() throws IOException { super(); }
        @Override protected void processTextPosition(TextPosition position) {
            glyphs.add(position); super.processTextPosition(position);
        }
    }
}
