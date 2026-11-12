/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.pdf;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhDrug;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhForm2Bean;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.LogSafe;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openpdf.text.DocumentException;
import org.openpdf.text.pdf.AcroFields;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfStamper;
import org.openpdf.text.pdf.PdfWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Captures actual controller logs while real BPMH beans fill the bundled AcroForm template.
 * Failure injection stays at the OpenPDF field boundary; successful controls render real PDF bytes.
 *
 * @since 2026-10-25
 */
@Tag("unit")
class PDFControllerLogPrivacyUnitTest {
    private static final String NOTE = "FAKE_BPMH_NOTE";
    private static final String MEDICATION = "FAKE_BPMH_RX";
    private static final String NAME = "FAKE_BPMH_NAME";
    private static final String HIN = "FAKE_BPMH_HIN";
    private static final String ORIGINAL = "FAKE_BPMH_ORIGINAL";
    private static final String FORGED = "FAKE_BPMH_AUDIT";
    private static final String PRIVATE = NOTE + " 患者秘密\r\n" + FORGED;
    @TempDir Path directory;

    private BpmhForm2Bean clinicalData() {
        Demographic patient = new Demographic();
        patient.setDemographicNo(999998);
        patient.setFirstName(NAME);
        patient.setHin(HIN);
        BpmhDrug drug = new BpmhDrug();
        drug.setWhat(MEDICATION);
        drug.setHow("Fixture instructions");
        drug.setWhy("Fixture indication");
        BpmhForm2Bean data = new BpmhForm2Bean();
        data.setDemographic(patient);
        data.setDemographicNo("999998");
        data.setDrugs(List.of(drug));
        data.setAllergies(List.of());
        data.setNote(PRIVATE);
        return data;
    }

    private Path template(boolean populated) throws Exception {
        Path source = Path.of(PDFController.class.getClassLoader()
                .getResource("oscar/form/prop/bpmh_template_marked.pdf").toURI());
        Path target = directory.resolve(populated ? "populated.pdf" : "template.pdf");
        if (populated) {
            try (PdfReader reader = new PdfReader(source.toString());
                 var output = Files.newOutputStream(target);
                 PdfStamper stamper = new PdfStamper(reader, output)) {
                assertThat(stamper.getAcroFields().setField("note", ORIGINAL)).isTrue();
                stamper.setInfoDictionary(Map.of("Subject", ORIGINAL));
            }
            try (PdfReader reader = new PdfReader(target.toString())) {
                assertThat(reader.getAcroFields().getField("note")).isEqualTo(ORIGINAL);
            }
        } else Files.copy(source, target);
        return target;
    }

    private PDFController controller(Path template) {
        PDFController controller = new PDFController(template.toFile());
        controller.setOutputPath(directory.toString());
        return controller;
    }

    private void render(PDFController controller) {
        controller.writeDataToPDF(clinicalData(), new String[]{"1", "2", "3"}, "owned");
    }

    private void assertRealPdf(PDFController controller) throws Exception {
        byte[] content = Files.readAllBytes(Path.of(controller.getOutputPath()));
        assertThat(new String(content, StandardCharsets.ISO_8859_1)).startsWith("%PDF-").contains("%%EOF");
        try (var pdf = Loader.loadPDF(content)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(3);
            assertThat(new PDFTextStripper().getText(pdf)).contains(NOTE, MEDICATION, HIN).doesNotContain(ORIGINAL);
        }
        try (PdfReader reader = new PdfReader(content)) {
            assertThat(reader.getAcroFields().getAllFields()).isEmpty();
        }
    }

    private void assertNoClinicalLogs(LogCapture capture) {
        assertThat(capture.events()).isNotEmpty();
        PatternLayout layout = PatternLayout.newBuilder().withPattern("%p %m%n%throwable").build();
        String rendered = capture.events().stream().map(layout::toSerializable).reduce("", String::concat);
        for (String value : List.of(NOTE, MEDICATION, NAME, HIN, ORIGINAL, FORGED, "患者秘密")) {
            assertThat(rendered).doesNotContain(value, LogSafe.sanitize(value));
        }
        assertThat(capture.events()).allSatisfy(event -> assertThat(event.getThrown()).isNull());
    }

    @Test
    void shouldFillAndFlattenTheBundledBpmhPdf_whenClinicalValuesAreProvided() throws Exception {
        PDFController controller = controller(template(false));
        render(controller);
        assertRealPdf(controller);
    }

    @Test
    void shouldKeepOriginalAndReplacementClinicalValuesOutOfLogs_whenRenderingARealBpmhPdf() throws Exception {
        Path populated = template(true);
        try (var capture = LogCapture.forLogger(PDFController.class)) {
            PDFController controller = controller(populated);
            render(controller);
            assertRealPdf(controller);
            assertNoClinicalLogs(capture);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"flat", "nested", "list"})
    void shouldReturnClinicalGetterValues_withoutLoggingTheirContents(String access) {
        PDFController controller = new PDFController();
        BpmhForm2Bean bean = clinicalData();
        try (var capture = LogCapture.forLogger(PDFController.class)) {
            if (access.equals("flat")) {
                controller.setDataObject(bean.getDemographic());
                assertThat(controller.invokeValue("firstName")).isEqualTo(NAME);
            } else {
                controller.setDataObject(bean);
                String property = access.equals("nested") ? "demographic.hin" : "drugs#0.what";
                assertThat(controller.invokeValue(property)).isEqualTo(access.equals("nested") ? HIN : MEDICATION);
            }
            assertNoClinicalLogs(capture);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"io", "document"})
    void shouldRetainBestEffortFieldFilling_withoutLoggingRejectedValuesOrExceptionMessages(String failure) throws Exception {
        PDFController controller = controller(template(false));
        AcroFields fields = spy(controller.getReader().getAcroFields());
        var note = fields.getAllFields().get("note");
        assertThat(note).isNotNull();
        doReturn(Map.of("note", note)).when(fields).getAllFields();
        Exception problem = failure.equals("io") ? new IOException(PRIVATE) : new DocumentException(PRIVATE);
        doThrow(problem).when(fields).setField(eq("note"), anyString());
        try (var capture = LogCapture.forLogger(PDFController.class);
             var stampers = mockConstruction(PdfStamper.class, (stamper, context) -> {
                 when(stamper.getAcroFields()).thenReturn(fields);
                 when(stamper.getWriter()).thenReturn(mock(PdfWriter.class));
             })) {
            assertThatCode(() -> render(controller)).doesNotThrowAnyException();
            verify(fields).setField("note", PRIVATE);
            assertThat(stampers.constructed()).hasSize(1);
            verify(stampers.constructed().getFirst()).close();
            assertThat(capture.events()).anyMatch(event -> event.getLevel() == Level.FATAL);
            assertNoClinicalLogs(capture);
        } finally { controller.getReader().close(); }
    }

    @Test
    void shouldRetainEmptyValueOnGetterFailure_withoutLoggingTheClinicalExceptionCause() {
        PDFController controller = new PDFController();
        controller.setDataObject(new FailingNote());
        try (var capture = LogCapture.forLogger(PDFController.class)) {
            assertThat(controller.invokeValue("note")).isEmpty();
            assertThat(capture.events()).anyMatch(event -> event.getLevel() == Level.FATAL);
            assertNoClinicalLogs(capture);
        }
    }

    @Test
    void shouldRetainPartialMethodDiscoveryOnFailure_withoutLoggingTheClinicalExceptionCause() {
        PDFController controller = new PDFController();
        try (var capture = LogCapture.forLogger(PDFController.class)) {
            assertThatCode(() -> controller.setDataObject(new FailingDrugs())).doesNotThrowAnyException();
            assertThat(controller.getMethodMap()).isNotNull();
            assertThat(capture.events()).anyMatch(event -> event.getLevel() == Level.FATAL);
            assertNoClinicalLogs(capture);
        }
    }

    public static class FailingNote extends BpmhForm2Bean {
        private static final long serialVersionUID = 1L;
        @Override public String getNote() { throw new IllegalStateException(PRIVATE); }
    }

    public static class FailingDrugs extends BpmhForm2Bean {
        private static final long serialVersionUID = 1L;
        @Override public List<BpmhDrug> getDrugs() { throw new IllegalStateException(PRIVATE); }
    }
}
