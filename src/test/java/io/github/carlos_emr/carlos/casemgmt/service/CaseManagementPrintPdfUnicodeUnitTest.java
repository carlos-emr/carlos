/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.service;

import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.List;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.prevention.PreventionDateService;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * Prints an encounter through the real {@link CaseManagementPrintPdf} and checks that clinic,
 * patient, allergy and note text in any script reaches the PDF (issue #5046).
 *
 * <p>The header, allergy and note sections were Phrases in DejaVu alone, which has no Chinese,
 * Japanese or Korean characters, so the eChart Print left them out. All values are synthetic.
 */
@Tag("unit")
@Tag("report")
class CaseManagementPrintPdfUnicodeUnitTest extends CarlosUnitTestBase {
    @Test
    @DisplayName("should print clinic, patient, allergy and note text in Chinese, Japanese and Korean")
    void shouldPrintEncounterText_inChineseJapaneseAndKorean() throws Exception {
        Clinic clinic = new Clinic();
        clinic.setId(1);
        clinic.setClinicName("FAKE-诊所 Łukasz");
        clinic.setClinicAddress("FAKE 1 北京路");
        when(createAndRegisterMock(ClinicDAO.class).getClinic()).thenReturn(clinic);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("demoName", "FAKE-陈, 小明");
        request.setAttribute("demoSex", "F");
        request.setAttribute("demoDOB", "2000-01-01");
        request.setAttribute("demoAge", "26");
        request.setAttribute("mrp", "Dr FAKE-김민준");
        request.setAttribute("demoPhn", "9876543210");
        Allergy allergy = new Allergy();
        allergy.setDescription("FAKE-페니실린");
        allergy.setReaction("FAKE-じんましん");
        CaseManagementNote note = new CaseManagementNote();
        note.setNote("FAKE-复诊 ≥5 mmol/L");
        note.setObservation_date(new Date(1_790_000_000_000L));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PartialDateDao partialDates = mock(PartialDateDao.class);
        when(partialDates.getFormat(4, 41, 7)).thenReturn("YYYY");
        Prevention partial = new Prevention();
        org.springframework.test.util.ReflectionTestUtils.setField(partial, "id", 41);
        partial.setPreventionType("Td");
        partial.setPreventionDate(java.sql.Date.valueOf("2019-01-01"));
        Prevention unknown = new Prevention();
        unknown.setPreventionType("MMR");
        CaseManagementPrintPdf pdf = new CaseManagementPrintPdf(request, output,
                () -> new PreventionDateService(partialDates));
        pdf.printDocHeaderFooter();
        pdf.printPreventions(List.of(partial, unknown));
        pdf.printAllergies(List.of(allergy));
        pdf.printNotes(List.of(note));
        pdf.finish();

        try (var document = Loader.loadPDF(output.toByteArray())) {
            for (var page : document.getPages()) {
                for (var name : page.getResources().getFontNames()) {
                    assertThat(page.getResources().getFont(name).isEmbedded()).as("font %s", name).isTrue();
                }
            }
            assertThat(new PDFTextStripper().getText(document)).contains("FAKE-诊所 Łukasz", "FAKE 1 北京路",
                    "FAKE-陈, 小明", "Dr FAKE-김민준", "FAKE-페니실린", "FAKE-じんましん", "FAKE-复诊 ≥5 mmol/L");
            assertThat(new PDFTextStripper().getText(document)).contains("2019 - Td", "Unknown - MMR")
                    .doesNotContain("01-Jan-2019");
        }
    }
}
