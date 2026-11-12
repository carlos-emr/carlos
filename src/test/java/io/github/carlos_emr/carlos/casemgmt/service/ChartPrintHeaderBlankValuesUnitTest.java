/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The chart print header (encounter Print, and the Messenger preview built on it) printed "PHN/HIN: null" for a
 * patient with no health number, and "null" for any other empty header value. An empty value now prints as a
 * blank after its label. All values are synthetic.
 */
@Tag("unit")
@Tag("casemgmt")
@DisplayName("Chart print header with empty patient values")
class ChartPrintHeaderBlankValuesUnitTest extends CarlosUnitTestBase {
    private static final String PATIENT = "900401";

    private static CaseManagementManagerImpl managerFor(Demographic patient) {
        DemographicDao demographics = mock(DemographicDao.class);
        when(demographics.getClientByDemographicNo(Integer.valueOf(PATIENT))).thenReturn(patient);
        CaseManagementManagerImpl manager = new CaseManagementManagerImpl();
        manager.setDemographicDao(demographics);
        return manager;
    }

    @Test
    @DisplayName("should give blanks, never null, for a patient with no health number, sex or name")
    void shouldGiveBlanks_forEmptyHeaderValues() {
        CaseManagementManagerImpl manager = managerFor(new Demographic());

        assertThat(manager.getDemoPhn(PATIENT)).isEmpty();
        assertThat(manager.getDemoGender(PATIENT)).isEmpty();
        assertThat(manager.getDemoName(PATIENT)).isEmpty();
    }

    @Test
    @DisplayName("should keep values on file, and leave out a missing part of the name")
    void shouldKeepValuesOnFile_andLeaveOutAMissingNamePart() {
        Demographic patient = new Demographic();
        patient.setHin("9876543217");
        patient.setSex("F");
        patient.setLastName("FAKE-Lovelace");
        CaseManagementManagerImpl manager = managerFor(patient);

        assertThat(manager.getDemoPhn(PATIENT)).isEqualTo("9876543217");
        assertThat(manager.getDemoGender(PATIENT)).isEqualTo("F");
        assertThat(manager.getDemoName(PATIENT)).isEqualTo("FAKE-Lovelace");

        patient.setFirstName("FAKE-Ada");
        assertThat(manager.getDemoName(PATIENT)).isEqualTo("FAKE-Ada FAKE-Lovelace");
    }

    @Test
    @DisplayName("should print the header labels with blanks when the caller and the clinic set no values")
    void shouldPrintBlankHeaderValues_whenNoneAreSet() throws Exception {
        // No clinic on file either: its phone, fax, city and province are empty too.
        createAndRegisterMock(ClinicDAO.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        CaseManagementPrintPdf pdf = new CaseManagementPrintPdf(request, output);
        pdf.printDocHeaderFooter();
        pdf.finish();

        try (var document = Loader.loadPDF(output.toByteArray())) {
            String printed = new PDFTextStripper().getText(document);
            assertThat(printed).contains("PHN/HIN:", "Sex:", "MRP:", "Phone:", "Fax:").doesNotContain("null");
            // No lone comma where the clinic's city and province would be (the footer's disclaimer has commas).
            assertThat(printed.lines().map(String::strip)).doesNotContain(",");
        }
        assertThat(CaseManagementPrintPdf.headerValue(request, "demoPhn")).isEmpty();
        request.setAttribute("demoPhn", "9876543217");
        assertThat(CaseManagementPrintPdf.headerValue(request, "demoPhn")).isEqualTo("9876543217");
    }

    @Test
    @DisplayName("should print the clinic's city and province, and its phone, when they are on file")
    void shouldPrintClinicCityProvinceAndPhone_whenOnFile() throws Exception {
        io.github.carlos_emr.carlos.commn.model.Clinic clinic = new io.github.carlos_emr.carlos.commn.model.Clinic();
        clinic.setId(1);
        clinic.setClinicName("FAKE Clinic");
        clinic.setClinicCity("FAKE-Town");
        clinic.setClinicProvince("ON");
        clinic.setClinicPhone("555-0100");
        when(createAndRegisterMock(ClinicDAO.class).getClinic()).thenReturn(clinic);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        CaseManagementPrintPdf pdf = new CaseManagementPrintPdf(new MockHttpServletRequest(), output);
        pdf.printDocHeaderFooter();
        pdf.finish();

        try (var document = Loader.loadPDF(output.toByteArray())) {
            assertThat(new PDFTextStripper().getText(document)).contains("FAKE-Town, ON", "Phone: 555-0100")
                    .doesNotContain("null");
        }
    }
}
